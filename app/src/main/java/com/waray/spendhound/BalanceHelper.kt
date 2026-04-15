package com.waray.spendhound

import android.util.Log
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object BalanceHelper {
    private const val TAG = "BalanceHelper"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Recalculates and upserts the user_balance row for the given user
     * by aggregating transaction_splits and transaction_payors.
     * Call this after any transaction or settlement change.
     */
    suspend fun refreshUserBalance(userId: Long) {
        try {
            val allSplits = DeclareDatabase.transactionSplitsTable
                .select().decodeList<TransactionSplitTable>()
            val allPayors = DeclareDatabase.transactionPayorsTable
                .select().decodeList<TransactionPayorTable>()

            val userSplitsByTx = allSplits.filter { it.userId == userId }.groupBy { it.transactionId }
            val userPayorsByTx = allPayors.filter { it.userId == userId }.groupBy { it.transactionId }
            val involvedTxIds = (userSplitsByTx.keys + userPayorsByTx.keys).toSet()

            var youOwe = 0.0
            var youreOwed = 0.0

            // What the current user still owes
            for (txId in involvedTxIds) {
                val owed = userSplitsByTx[txId]?.sumOf { it.amount } ?: 0.0
                val paid = userPayorsByTx[txId]?.sumOf { it.currentAmountPaid } ?: 0.0
                if (paid < owed) youOwe += (owed - paid)
            }

            val payorsByTx = allPayors.groupBy { it.transactionId }
            val splitsByTx = allSplits.groupBy { it.transactionId }
            for (txId in involvedTxIds) {
                val splits = splitsByTx[txId] ?: continue
                val payors = payorsByTx[txId] ?: emptyList()
                youreOwed += calculateReceivableForUser(userId, splits, payors)
            }

            val data = buildJsonObject {
                put("user_id", userId)
                put("unpaid_total_group", youOwe)
                put("receivable_total_group", youreOwed)
                put("balance_total_group", youreOwed - youOwe)
                put("unpaid_total_individual", 0.0)
                put("receivable_total_individual", 0.0)
                put("balance_total_individual", 0.0)
            }
            DeclareDatabase.userBalanceTable.upsert(data)
            Log.d(TAG, "user_balance refreshed for user $userId: owe=$youOwe, owed=$youreOwed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh user_balance: ${e.message}")
        }
    }

    private fun calculateReceivableForUser(
        userId: Long,
        splits: List<TransactionSplitTable>,
        payors: List<TransactionPayorTable>
    ): Double {
        val splitsByUser = splits.groupBy { it.userId }
        val payorsByUser = payors.groupBy { it.userId }
        val participantIds = (splitsByUser.keys + payorsByUser.keys).toSet()

        if (participantIds.isEmpty()) return 0.0

        data class ParticipantBalance(val userId: Long, var amount: Double)

        val creditors = ArrayDeque<ParticipantBalance>()
        val debtors = ArrayDeque<ParticipantBalance>()

        participantIds.forEach { participantId ->
            val owed = splitsByUser[participantId]?.sumOf { it.amount } ?: 0.0
            val payorRows = payorsByUser[participantId] ?: emptyList()
            val totalInitialPaid = payorRows.sumOf { it.initialAmountPaid }
            val totalCurrentPaid = payorRows.sumOf { it.currentAmountPaid }

            val effectivePaid = if (owed > 0.0 && totalInitialPaid >= owed) {
                totalInitialPaid
            } else {
                totalCurrentPaid
            }

            val diff = effectivePaid - owed
            when {
                diff > 0.01 -> creditors.add(ParticipantBalance(participantId, diff))
                diff < -0.01 -> debtors.add(ParticipantBalance(participantId, -diff))
            }
        }

        var receivable = 0.0
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val creditor = creditors.first()
            val debtor = debtors.first()
            val transfer = minOf(creditor.amount, debtor.amount)

            if (creditor.userId == userId) {
                receivable += transfer
            }

            creditor.amount -= transfer
            debtor.amount -= transfer

            if (creditor.amount < 0.01) creditors.removeFirst()
            if (debtor.amount < 0.01) debtors.removeFirst()
        }

        return receivable
    }

    fun initializeBalancesForNewUser(userId: String?, callback: BalanceCallback?) {
        if (userId == null) return
        scope.launch {
            try {
                val data = buildJsonObject {
                    put("user_id", userId.toLong())
                    put("unpaid_total_group", 0.0)
                    put("unpaid_total_individual", 0.0)
                    put("receivable_total_group", 0.0)
                    put("receivable_total_individual", 0.0)
                    put("balance_total_group", 0.0)
                    put("balance_total_individual", 0.0)
                }
                DeclareDatabase.userBalanceTable.upsert(data)
                withContext(Dispatchers.Main) { callback?.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    fun ensureBalancesExist(userId: String?, callback: BalanceCallback?) {
        if (userId == null) return
        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", userId.toLong()) }
                }.decodeSingleOrNull<UserBalance>()
                withContext(Dispatchers.Main) {
                    if (balance == null) initializeBalancesForNewUser(userId, callback)
                    else callback?.onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    fun getUserBalance(userId: String?, callback: (UserBalance?) -> Unit) {
        if (userId == null) { callback(null); return }
        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", userId.toLong()) }
                }.decodeSingleOrNull<UserBalance>()
                withContext(Dispatchers.Main) { callback(balance) }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching balance: ${e.message}")
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }

    /**
     * Add a record to userBorrows table for the borrower
     */
    fun addBorrowerEntry(userId: String?, borrowId: String, callback: BalanceCallback?) {
        if (userId == null) return
        scope.launch {
            try {
                val entry = buildJsonObject {
                    put("user_id", userId.toLong())
                    put("borrow_id", borrowId.toLong())
                    put("type", "borrower")
                }
                DeclareDatabase.userBorrowsTable.insert(entry)
                withContext(Dispatchers.Main) { callback?.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    /**
     * Add a record to userBorrows table for the lender
     */
    fun addLenderEntry(userId: String?, borrowId: String, callback: BalanceCallback?) {
        if (userId == null) return
        scope.launch {
            try {
                val entry = buildJsonObject {
                    put("user_id", userId.toLong())
                    put("borrow_id", borrowId.toLong())
                    put("type", "lender")
                }
                DeclareDatabase.userBorrowsTable.insert(entry)
                withContext(Dispatchers.Main) { callback?.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    fun updateTotaldebt(userId: String?, amountChange: Double, callback: BalanceCallback?) {}
    fun updateTotalreceivable(userId: String?, amountChange: Double, callback: BalanceCallback?) {}

    interface BalanceCallback {
        fun onSuccess()
        fun onFailure(error: String?)
    }
}
