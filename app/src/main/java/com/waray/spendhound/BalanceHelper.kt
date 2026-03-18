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
 */
object BalanceHelper {
    private const val TAG = "BalanceHelper"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize balances for new users during registration
     */
    fun initializeBalancesForNewUser(uid: String?, callback: BalanceCallback?) {
        if (uid == null) return
        
        scope.launch {
            try {
                val initialBalance = UserBalance(0.0, 0.0, 0.0, 0.0, 0.0)
                DeclareDatabase.usersTable.update({
                    set("balances", initialBalance)
                }) {
                    filter {
                        eq("id", uid)
                    }
                }
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Balances initialized for user: $uid")
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
     * Check if balances exist for existing users, create if not
     */
    fun ensureBalancesExist(uid: String?, callback: BalanceCallback?) {
        if (uid == null) return

        scope.launch {
            try {
                val user = DeclareDatabase.usersTable.select(Columns.list("balances")) {
                    filter {
                        eq("id", uid)
                    }
                }.decodeSingleOrNull<User>()

                withContext(Dispatchers.Main) {
                    if (user?.balances == null) {
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
     * Update user's totalBillSpent
     */
    fun updateTotalBillSpent(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        updateBalanceField(uid, "totalBillSpent", amountChange, callback)
    }

    /**
     * Update user's totalBillPayment
     */
    fun updateTotalBillPayment(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        updateBalanceField(uid, "totalBillPayment", amountChange, callback)
    }

    /**
     * Update user's totalIndividualSpent
     */
    fun updateTotalIndividualSpent(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        updateBalanceField(uid, "totalIndividualSpent", amountChange, callback)
    }

    /**
     * Update user's totaldebt
     */
    fun updateTotaldebt(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        updateBalanceField(uid, "totaldebt", amountChange, callback)
    }

    /**
     * Update user's totalreceivable
     */
    fun updateTotalreceivable(uid: String?, amountChange: Double, callback: BalanceCallback?) {
        updateBalanceField(uid, "totalreceivable", amountChange, callback)
    }

    private fun updateBalanceField(uid: String?, fieldName: String, amountChange: Double, callback: BalanceCallback?) {
        if (uid == null) return
        
        scope.launch {
            try {
                // Fetch current balances
                val user = DeclareDatabase.usersTable.select(Columns.list("balances")) {
                    filter { eq("id", uid) }
                }.decodeSingleOrNull<User>()
                
                val currentBalances = user?.balances ?: UserBalance()
                
                when(fieldName) {
                    "totalBillSpent" -> currentBalances.totalBillSpent += amountChange
                    "totalBillPayment" -> currentBalances.totalBillPayment += amountChange
                    "totalIndividualSpent" -> currentBalances.totalIndividualSpent += amountChange
                    "totaldebt" -> currentBalances.totaldebt += amountChange
                    "totalreceivable" -> currentBalances.totalreceivable += amountChange
                }

                DeclareDatabase.usersTable.update({
                    set("balances", currentBalances)
                }) {
                    filter { eq("id", uid) }
                }

                withContext(Dispatchers.Main) {
                    callback?.onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Failed to update $fieldName: ${e.message}")
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
                    "user_id" to uid,
                    "borrow_id" to borrowId,
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
                    "user_id" to uid,
                    "borrow_id" to borrowId,
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
