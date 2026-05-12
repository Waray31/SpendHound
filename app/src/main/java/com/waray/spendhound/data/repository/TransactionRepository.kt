package com.waray.spendhound.data.repository

import com.waray.spendhound.CurrencyUtils
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.R
import com.waray.spendhound.RecentTransaction
import com.waray.spendhound.TransactionRead
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionRepository(private val db: AppDatabase) {

    fun getTransactions(userId: Long): Flow<List<RecentTransaction>> = db.cachedFlow(
        key = CacheKeys.transactions(userId),
        staleTtlMs = CacheKeys.STALE_TRANSACTIONS,
        type = typeOf<List<RecentTransaction>>()
    ) { fetchTransactions(userId, 200L) }

    fun getRecentTransactions(userId: Long): Flow<List<RecentTransaction>> = db.cachedFlow(
        key = CacheKeys.homeRecent(userId),
        staleTtlMs = CacheKeys.STALE_HOME,
        type = typeOf<List<RecentTransaction>>()
    ) { fetchTransactions(userId, 5L) }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.transactions(userId))
        db.jsonBlobDao().delete(CacheKeys.homeRecent(userId))
    }

    private suspend fun fetchTransactions(userId: Long, limit: Long): List<RecentTransaction> {
        val userSplits = DeclareDatabase.transactionSplitsTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<TransactionSplitTable>()
        val userPayors = DeclareDatabase.transactionPayorsTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<TransactionPayorTable>()
        val involvedIds = (userPayors.map { it.transactionId } + userSplits.map { it.transactionId }).toSet()
        if (involvedIds.isEmpty()) return emptyList()

        val txs = DeclareDatabase.transactionsTable.select {
            filter { isIn("id", involvedIds.toList()) }
            order("created_at", Order.DESCENDING)
            this.limit(limit)
        }.decodeList<TransactionFull>()

        val txIds = txs.mapNotNull { it.id }
        val readTxIds = DeclareDatabase.transactionReadsTable.select(Columns.list("transaction_id")) {
            filter { eq("user_id", userId) }
        }.decodeList<TransactionRead>().mapNotNull { it.transactionId }.toSet()

        val allPayors = DeclareDatabase.transactionPayorsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionPayorTable>()
        val allSplits = DeclareDatabase.transactionSplitsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionSplitTable>()
        val allItems = DeclareDatabase.transactionItemsTable.select {
            filter { isIn("transaction_id", txIds) }
        }.decodeList<TransactionItemFull>()

        val allUserIds = (allPayors.map { it.userId } + allSplits.map { it.userId }).distinct()
        val usersById = if (allUserIds.isNotEmpty()) {
            DeclareDatabase.usersTable.select {
                filter { isIn("user_id", allUserIds) }
            }.decodeList<User>().associate { it.id!! to (it.username ?: "Unknown") }
        } else emptyMap()

        val involvedGroupIds = txs.mapNotNull { it.groupId }.distinct()
        val groupsById = if (involvedGroupIds.isNotEmpty()) {
            DeclareDatabase.groupsTable.select {
                filter { isIn("group_id", involvedGroupIds) }
            }.decodeList<PayerGroup>().associate { it.groupId!! to (it.groupName ?: "Unknown") }
        } else emptyMap()

        val payorsByTx = allPayors.groupBy { it.transactionId }
        val splitsByTx = allSplits.groupBy { it.transactionId }
        val itemsByTx = allItems.groupBy { it.transactionId }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

        return txs.mapNotNull { tx ->
            val txId = tx.id ?: return@mapNotNull null
            val payors = payorsByTx[txId] ?: emptyList()
            val splits = splitsByTx[txId] ?: emptyList()
            val items = itemsByTx[txId] ?: emptyList()
            val timestamp = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            val year = cal.get(Calendar.YEAR).toString()
            val day = cal.get(Calendar.DAY_OF_MONTH).toString()
            val timeKey = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(cal.time)
            val contributorIds = (payors.map { it.userId } + splits.map { it.userId }).distinct()
            val payorNames = contributorIds.map { usersById[it] ?: "Unknown" }.toMutableList<String?>()
            val payorUserIds = contributorIds.map { it.toString() }.toMutableList<String?>()
            val amountsPaid = contributorIds.map { uid ->
                payors.filter { it.userId == uid }.sumOf { it.currentAmountPaid } as Double?
            }.toMutableList()
            val userOwedMap = splits.groupBy { it.userId }.mapValues { it.value.sumOf { s -> s.amount } }
            val individualPayment = userOwedMap.values.firstOrNull() ?: 0.0
            val paidByUser = payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.currentAmountPaid } }
            val allSettled = userOwedMap.isNotEmpty() && userOwedMap.all { (userId, owed) ->
                (paidByUser[userId] ?: 0.0) >= owed - 0.01
            }
            val itemPayorMap = items.associate { item ->
                val itemId = item.id ?: 0L
                itemId to payors.filter { it.transactionItemsId == itemId }
                    .mapNotNull { usersById[it.userId] }.joinToString(", ").ifEmpty { "-" }
            }
            RecentTransaction(
                txId, "$monthName - $day", tx.description, tx.description,
                CurrencyUtils.formatAmountWithCurrency(tx.totalAmount),
                getIcon(tx.description), "$year-$monthName-$day $timeKey", timestamp,
                payorNames, payorUserIds, amountsPaid, individualPayment,
                "$monthName $day, $year", usersById[tx.createdBy] ?: "Unknown",
                tx.createdBy?.toString(), "$monthName-$year", day, timeKey
            ).also {
                it.transactionItems = items
                it.transactionStatus = if (allSettled) "Settled" else "Pending"
                it.itemPayorMap = itemPayorMap
                it.creatorNumericId = tx.createdBy
                it.rawPayorRows = payors
                it.rawSplitRows = splits
                it.isUnread = tx.groupId != null && txId !in readTxIds && tx.createdBy != userId
                it.groupId = tx.groupId
                it.groupName = groupsById[tx.groupId]
                it.isArchived = tx.isArchived ?: false
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun getIcon(type: String?) = when (type) {
        "Electricity" -> R.drawable.lightning_bolt
        "Water" -> R.drawable.faucet
        "Rent" -> R.drawable.house
        "Internet" -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel" -> R.drawable.travel
        "Groceries" -> R.drawable.groceries
        "Foods" -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation" -> R.drawable.vehicles
        else -> R.drawable.others
    }
}
