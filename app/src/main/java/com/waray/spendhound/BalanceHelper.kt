package com.waray.spendhound

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for managing user balance operations in Supabase.
 * Aligned with the 'user_balance' table schema.
 */
object BalanceHelper {
    private const val TAG = "BalanceHelper"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize balances for a user in the 'user_balance' table.
     */
    fun initializeBalancesForNewUser(uid: String?, callback: BalanceCallback?) {
        if (uid == null) return
        
        scope.launch {
            try {
                val initialBalance = UserBalance(userId = uid.toLong())
                DeclareDatabase.userBalanceTable.insert(initialBalance)
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Balances initialized in user_balance for user: $uid")
                    callback?.onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Failed to initialize balances: ${e.message}")
                    callback?.onFailure(e.message)
                }
            }
        }
    }

    /**
     * Ensures a balance record exists for the user.
     */
    fun ensureBalancesExist(uid: String?, callback: BalanceCallback?) {
        if (uid == null) return

        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter {
                        eq("user_id", uid.toLong())
                    }
                }.decodeSingleOrNull<UserBalance>()

                withContext(Dispatchers.Main) {
                    if (balance == null) {
                        initializeBalancesForNewUser(uid, callback)
                    } else {
                        callback?.onSuccess()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Failed to check balances: ${e.message}")
                    callback?.onFailure(e.message)
                }
            }
        }
    }

    /**
     * Fetches the user's balance.
     */
    fun getUserBalance(uid: String?, callback: (UserBalance?) -> Unit) {
        if (uid == null) {
            callback(null)
            return
        }
        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", uid.toLong()) }
                }.decodeSingleOrNull<UserBalance>()
                withContext(Dispatchers.Main) {
                    callback(balance)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching balance: ${e.message}")
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }

    // New methods aligned with user_balance table columns
    fun updateUnpaidTotalGroup(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("unpaid_total_group", uid, amountChange, callback)
    fun updateUnpaidTotalIndividual(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("unpaid_total_individual", uid, amountChange, callback)
    fun updateReceivableTotalGroup(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("receivable_total_group", uid, amountChange, callback)
    fun updateReceivableTotalIndividual(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("receivable_total_individual", uid, amountChange, callback)
    fun updateBalanceTotalGroup(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("balance_total_group", uid, amountChange, callback)
    fun updateBalanceTotalIndividual(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("balance_total_individual", uid, amountChange, callback)

    // Legacy compatibility methods
    fun updateTotalBillSpent(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateUnpaidTotalGroup(uid, amountChange, callback)
    fun updateTotalBillPayment(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceTotalGroup(uid, amountChange, callback)
    fun updateTotalIndividualSpent(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateUnpaidTotalIndividual(uid, amountChange, callback)
    fun updateTotaldebt(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        // Update both group and individual if needed, or map to group as default
        updateUnpaidTotalGroup(uid, amountChange, callback)
    }
    fun updateTotalreceivable(uid: String?, amountChange: Double, callback: BalanceCallback?) = updateReceivableTotalGroup(uid, amountChange, callback)

    private fun updateBalanceField(columnName: String, uid: String?, amountChange: Double, callback: BalanceCallback?) {
        if (uid == null) return
        
        scope.launch {
            try {
                // Fetch current balance
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", uid.toLong()) }
                }.decodeSingleOrNull<UserBalance>() ?: UserBalance(userId = uid.toLong())
                
                val newValue = when(columnName) {
                    "unpaid_total_group" -> balance.unpaidTotalGroup + amountChange
                    "unpaid_total_individual" -> balance.unpaidTotalIndividual + amountChange
                    "receivable_total_group" -> balance.receivableTotalGroup + amountChange
                    "receivable_total_individual" -> balance.receivableTotalIndividual + amountChange
                    "balance_total_group" -> balance.balanceTotalGroup + amountChange
                    "balance_total_individual" -> balance.balanceTotalIndividual + amountChange
                    else -> 0.0
                }

                DeclareDatabase.userBalanceTable.update({
                    set(columnName, newValue)
                }) {
                    filter { eq("user_id", uid.toLong()) }
                }

                withContext(Dispatchers.Main) {
                    callback?.onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Failed to update $columnName: ${e.message}")
                    callback?.onFailure(e.message)
                }
            }
        }
    }

    /**
     * Add a record to userBorrows table for the borrower
     */
    fun addBorrowerEntry(uid: String?, borrowId: String, callback: BalanceCallback?) {
        if (uid == null) return
        scope.launch {
            try {
                DeclareDatabase.userBorrowsTable.insert(mapOf(
                    "user_id" to uid.toLong(),
                    "borrow_id" to borrowId.toLong(),
                    "type" to "borrower"
                ))
                withContext(Dispatchers.Main) { callback?.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    /**
     * Add a record to userBorrows table for the lender
     */
    fun addLenderEntry(uid: String?, borrowId: String, callback: BalanceCallback?) {
        if (uid == null) return
        scope.launch {
            try {
                DeclareDatabase.userBorrowsTable.insert(mapOf(
                    "user_id" to uid.toLong(),
                    "borrow_id" to borrowId.toLong(),
                    "type" to "lender"
                ))
                withContext(Dispatchers.Main) { callback?.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback?.onFailure(e.message) }
            }
        }
    }

    interface BalanceCallback {
        fun onSuccess()
        fun onFailure(error: String?)
    }
}
