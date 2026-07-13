package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.UserBalance
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeData(
    val totalMonthSpends: Double,
    val youOweAmount: Double,
    val youreOwedAmount: Double,
    val netBalance: Double = 0.0,
    val lastMonthTotal: Double = 0.0
)

class HomeRepository(private val db: AppDatabase) {

    fun getHomeData(userId: Long, groupId: Long? = null): Flow<HomeData> = db.cachedFlow(
        key = CacheKeys.home(userId) + (groupId?.let { "_g$it" } ?: ""),
        staleTtlMs = CacheKeys.STALE_HOME,
        type = typeOf<HomeData>()
    ) {
        try {
            val balance = if (groupId == null) {
                DeclareDatabase.userBalanceTable.select(
                    Columns.list("unpaid_total_group", "receivable_total_group", "balance_total_group")
                ) { filter { eq("user_id", userId) } }.decodeSingleOrNull<UserBalance>()
            } else {
                // For a specific group, we need to calculate balance from scratch or if there's a per-group balance table
                // Based on BalanceHelper.refreshUserBalance, it aggregates ALL groups.
                // We'll calculate it manually for the group here to ensure accuracy.
                calculateGroupBalance(userId, groupId)
            }

            val splits = DeclareDatabase.transactionSplitsTable.select {
                filter { 
                    eq("user_id", userId)
                }
            }.decodeList<TransactionSplitTable>()

            val involvedIds = splits.mapNotNull { it.transactionId }.toSet()
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val cal = Calendar.getInstance()

            val monthStart = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val monthEnd = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val lastMonthCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val lastMonthStart = (lastMonthCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val lastMonthEnd = (lastMonthCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            var thisMonthTotal = 0.0
            var lastMonthTotal = 0.0

            if (involvedIds.isNotEmpty()) {
                val txs = DeclareDatabase.transactionsTable.select {
                    filter { 
                        isIn("id", involvedIds.toList()) 
                        if (groupId != null) eq("group_id", groupId)
                    }
                }.decodeList<TransactionFull>()
                val splitsByTx = splits.groupBy { it.transactionId }
                for (tx in txs) {
                    val ts = try { sdf.parse(tx.createdAt ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
                    val amount = splitsByTx[tx.id]?.sumOf { it.amount } ?: 0.0
                    if (ts in monthStart.timeInMillis..monthEnd.timeInMillis) thisMonthTotal += amount
                    if (ts in lastMonthStart.timeInMillis..lastMonthEnd.timeInMillis) lastMonthTotal += amount
                }
            }

            HomeData(
                totalMonthSpends = thisMonthTotal,
                youOweAmount = balance?.unpaidTotalGroup ?: 0.0,
                youreOwedAmount = balance?.receivableTotalGroup ?: 0.0,
                netBalance = balance?.balanceTotalGroup ?: 0.0,
                lastMonthTotal = lastMonthTotal
            )
        } catch (e: Exception) {
            android.util.Log.e("HomeRepo", "Error fetching home data: ${e.message}")
            HomeData(0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private suspend fun calculateGroupBalance(userId: Long, groupId: Long): UserBalance = try {
        // Fetch all splits and payors for THIS group only
        val groupTxs = DeclareDatabase.transactionsTable.select {
            filter { eq("group_id", groupId) }
        }.decodeList<TransactionFull>()
        val txIds = groupTxs.mapNotNull { it.id }
        if (txIds.isEmpty()) UserBalance()
        else {
            val splits = DeclareDatabase.transactionSplitsTable.select {
                filter { isIn("transaction_id", txIds) }
            }.decodeList<TransactionSplitTable>()
            val payors = DeclareDatabase.transactionPayorsTable.select {
                filter { isIn("transaction_id", txIds) }
            }.decodeList<TransactionPayorTable>()

            val splitsByTx = splits.groupBy { it.transactionId }
            val payorsByTx = payors.groupBy { it.transactionId }
            
            val netBalancesWithOthers = mutableMapOf<Long, Double>()

            for (txId in txIds) {
                val s = splitsByTx[txId] ?: continue
                val p = payorsByTx[txId] ?: emptyList()

                val transfers = com.waray.spendhound.BalanceHelper.calculateTransfersForTransaction(s, p)
                for (transfer in transfers) {
                    if (transfer.to == userId) {
                        netBalancesWithOthers[transfer.from] = (netBalancesWithOthers[transfer.from] ?: 0.0) + transfer.amount
                    } else if (transfer.from == userId) {
                        netBalancesWithOthers[transfer.to] = (netBalancesWithOthers[transfer.to] ?: 0.0) - transfer.amount
                    }
                }
            }

            var totalNetReceivable = 0.0
            var totalNetDebt = 0.0
            for (balance in netBalancesWithOthers.values) {
                if (balance > 0.01) totalNetReceivable += balance
                else if (balance < -0.01) totalNetDebt += -balance
            }

            UserBalance(
                userId = userId,
                unpaidTotalGroup = totalNetDebt,
                receivableTotalGroup = totalNetReceivable,
                balanceTotalGroup = totalNetReceivable - totalNetDebt
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("HomeRepo", "Error calculating group balance: ${e.message}")
        UserBalance()
    }

    suspend fun invalidate(userId: Long) {
        db.jsonBlobDao().delete(CacheKeys.home(userId))
        db.jsonBlobDao().delete(CacheKeys.homeRecent(userId))
        
        // Also invalidate group-specific home data caches
        val memberships = DeclareDatabase.groupMembersTable.select {
            filter { eq("user_id", userId) }
        }.decodeList<com.waray.spendhound.GroupMember>()
        memberships.forEach { 
            it.groupId?.let { gid -> db.jsonBlobDao().delete(CacheKeys.home(userId) + "_g$gid") }
        }

        com.waray.spendhound.BalanceHelper.refreshUserBalance(userId)
    }
}
