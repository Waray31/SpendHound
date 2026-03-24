package com.waray.spendhound

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    fun initializeBalancesForNewUser(userId: String?, callback: BalanceCallback?) {
        if (userId == null) return
        
        scope.launch {
            try {
                // Use buildJsonObject to avoid 'Serializer for class Any not found' error
                // and avoid sending null 'created_at', allowing Supabase to use its default value (NOW()).
                val initialBalanceData = buildJsonObject {
                    put("user_id", userId.toLong())
                    put("unpaid_total_group", 0.0)
                    put("unpaid_total_individual", 0.0)
                    put("receivable_total_group", 0.0)
                    put("receivable_total_individual", 0.0)
                    put("balance_total_group", 0.0)
                    put("balance_total_individual", 0.0)
                }
                DeclareDatabase.userBalanceTable.insert(initialBalanceData)
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Balances initialized in user_balance for user: $userId")
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
    fun ensureBalancesExist(userId: String?, callback: BalanceCallback?) {
        if (userId == null) return

        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter {
                        eq("user_id", userId.toLong())
                    }
                }.decodeSingleOrNull<UserBalance>()

                withContext(Dispatchers.Main) {
                    if (balance == null) {
                        initializeBalancesForNewUser(userId, callback)
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
    fun getUserBalance(userId: String?, callback: (UserBalance?) -> Unit) {
        if (userId == null) {
            callback(null)
            return
        }
        scope.launch {
            try {
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", userId.toLong()) }
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
    fun updateUnpaidTotalGroup(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("unpaid_total_group", userId, amountChange, callback)
    fun updateUnpaidTotalIndividual(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("unpaid_total_individual", userId, amountChange, callback)
    fun updateReceivableTotalGroup(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("receivable_total_group", userId, amountChange, callback)
    fun updateReceivableTotalIndividual(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("receivable_total_individual", userId, amountChange, callback)
    fun updateBalanceTotalGroup(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("balance_total_group", userId, amountChange, callback)
    fun updateBalanceTotalIndividual(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceField("balance_total_individual", userId, amountChange, callback)

    // Legacy compatibility methods
    fun updateTotalBillSpent(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateUnpaidTotalGroup(userId, amountChange, callback)
    fun updateTotalBillPayment(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateBalanceTotalGroup(userId, amountChange, callback)
    fun updateTotalIndividualSpent(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateUnpaidTotalIndividual(userId, amountChange, callback)
    fun updateTotaldebt(userId: String?, amountChange: Double, callback: BalanceCallback?) {
        // Update both group and individual if needed, or map to group as default
        updateUnpaidTotalGroup(userId, amountChange, callback)
    }
    fun updateTotalreceivable(userId: String?, amountChange: Double, callback: BalanceCallback?) = updateReceivableTotalGroup(userId, amountChange, callback)

    private fun updateBalanceField(columnName: String, userId: String?, amountChange: Double, callback: BalanceCallback?) {
        if (userId == null) return
        
        scope.launch {
            try {
                // Fetch current balance
                val balance = DeclareDatabase.userBalanceTable.select {
                    filter { eq("user_id", userId.toLong()) }
                }.decodeSingleOrNull<UserBalance>() ?: UserBalance(userId = userId.toLong())
                
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
                    filter { eq("user_id", userId.toLong()) }
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

    interface BalanceCallback {
        fun onSuccess()
        fun onFailure(error: String?)
    }
}
