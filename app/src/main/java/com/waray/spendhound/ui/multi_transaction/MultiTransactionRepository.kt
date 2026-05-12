package com.waray.spendhound.ui.multi_transaction

import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MultiTransactionRepository {

    private val client = DeclareDatabase.client

    suspend fun getGroups(): List<PayerGroup> = withContext(Dispatchers.IO) {
        client.postgrest.from("groups").select().decodeList<PayerGroup>()
    }

    suspend fun getGroupMembers(groupId: Long): List<User> = withContext(Dispatchers.IO) {
        val memberIds = client.postgrest.from("group_members").select {
            filter { eq("group_id", groupId) }
        }.decodeList<GroupMember>().mapNotNull { it.userId }

        if (memberIds.isEmpty()) return@withContext emptyList()

        client.postgrest.from("users").select {
            filter { isIn("user_id", memberIds) }
        }.decodeList<User>()
    }

    suspend fun submitTransactions(
        groupId: Long,
        createdBy: Long,
        title: String,
        entries: List<TransactionEntry>,
        groupMembers: List<User>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val createdAt = isoFormat.format(Date())
            val totalAmount = entries.sumOf { it.amount }

            // 1. Insert into transactions
            val txResponse = client.postgrest.from("transactions").insert(
                TransactionInsert(
                    totalAmount = totalAmount,
                    description = title.ifBlank { null },
                    groupId = groupId,
                    createdBy = createdBy,
                    createdAt = createdAt,
                    status = 2 // starts as Pending
                )
            ) { select() }.decodeSingle<TransactionResponse>()

            val txId = txResponse.id ?: throw Exception("Failed to get transaction ID")

            // Calculate total split amount per member across all items
            // Build a map of user total split owed based on their participation in each item
            val userTotalSplitOwed = mutableMapOf<Long, Double>()
            
            // Track total initial payment per user across all items
            val userTotalInitialPayments = mutableMapOf<Long, Double>()
            
            entries.forEach { entry ->
                // 2. Insert transaction_item
                val itemResponse = client.postgrest.from("transaction_items").insert(
                    TransactionItemInsert(
                        transactionId = txId,
                        amount = entry.amount,
                        category = entry.category,
                        itemDescription = entry.title.ifBlank { null },
                        createdAt = createdAt
                    )
                ) { select() }.decodeSingle<TransactionItemFull>()

                val itemId = itemResponse.id ?: throw Exception("Failed to get transaction_item ID")

                // Accumulate initial payments per user
                entry.payors.forEach { payor ->
                    userTotalInitialPayments[payor.userId] = 
                        (userTotalInitialPayments[payor.userId] ?: 0.0) + payor.amount
                }

                // 3. Insert ONLY payors who paid for this item into transaction_payors
                if (entry.payors.isNotEmpty()) {
                    val payorRecords = entry.payors
                        .filter { it.amount > 0.0 }  // Only insert payors who actually paid
                        .map { payor ->
                            TransactionPayorInsert(
                                transactionId = txId,
                                userId = payor.userId,
                                initialAmountPaid = payor.amount,
                                currentAmountPaid = payor.amount,  // Set to actual amount paid for this item
                                excessAmount = 0.0,  // Will be calculated after all items
                                transactionItemsId = itemId,
                                status = 0  // Will be calculated after all items
                            )
                        }
                    
                    if (payorRecords.isNotEmpty()) {
                        client.postgrest.from("transaction_payors").insert(payorRecords)
                    }
                }

                // 4. Split only among included members for this entry
                val membersToSplit = if (entry.includedMemberIds.isNotEmpty()) {
                    groupMembers.filter { member -> entry.includedMemberIds.contains(member.id) }
                } else {
                    groupMembers // Default to all members if no specific inclusion list
                }
                
                if (membersToSplit.isNotEmpty()) {
                    val splitAmount = entry.amount / membersToSplit.size
                    
                    // Track each user's total split owed across all items
                    membersToSplit.forEach { member ->
                        userTotalSplitOwed[member.id!!] = 
                            (userTotalSplitOwed[member.id!!] ?: 0.0) + splitAmount
                    }
                    
                    client.postgrest.from("transaction_splits").insert(
                        membersToSplit.map { member ->
                            TransactionSplitInsert(
                                transactionId = txId,
                                userId = member.id!!,
                                amount = splitAmount,
                                transactionItemsId = itemId
                            )
                        }
                    )
                }
            }
            
            // 5. Update all payors with calculated excess_amount and status based on total payments
            userTotalInitialPayments.forEach { (userId, totalInitialPaid) ->
                val userSplitOwed = userTotalSplitOwed[userId] ?: 0.0
                val currentPaid = if (totalInitialPaid > userSplitOwed) userSplitOwed else totalInitialPaid
                val excess = if (totalInitialPaid > userSplitOwed) totalInitialPaid - userSplitOwed else 0.0
                val status = when {
                    currentPaid == 0.0 -> 0
                    currentPaid >= userSplitOwed -> 1
                    else -> 2
                }
                client.postgrest.from("transaction_payors").update(buildJsonObject {
                    put("excess_amount", excess)
                    put("status", status)
                }) {
                    filter { eq("transaction_id", txId); eq("user_id", userId) }
                }
            }

            // 6. Refresh user_balance for all involved members
            groupMembers.forEach { member ->
                member.id?.let { BalanceHelper.refreshUserBalance(it) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a transaction payor's payment amount.
     * Recalculates current_amount_paid, excess_amount, and status.
     */
    suspend fun updatePayorPayment(
        transactionId: Long,
        userId: Long,
        transactionItemsId: Long,
        newAmountPaid: Double,
        splitAmountPerMember: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Calculate excess: if paid more than their split share
            val excess = if (newAmountPaid > splitAmountPerMember) {
                newAmountPaid - splitAmountPerMember
            } else {
                0.0
            }
            
            // Determine status: 0=unpaid, 1=settled, 2=pending
            val status = when {
                newAmountPaid == 0.0 -> 0  // unpaid
                newAmountPaid >= splitAmountPerMember -> 1  // settled
                else -> 2  // pending (partial payment)
            }
            
            client.postgrest.from("transaction_payors").update(buildJsonObject {
                put("current_amount_paid", newAmountPaid)
                put("excess_amount", excess)
                put("status", status)
            }) {
                filter {
                    eq("transaction_id", transactionId)
                    eq("user_id", userId)
                    eq("transaction_items_id", transactionItemsId)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateTransaction(
        transactionId: Long,
        groupId: Long,
        createdBy: Long,
        title: String,
        entries: List<TransactionEntry>,
        groupMembers: List<User>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val totalAmount = entries.sumOf { it.amount }

            // 1. Update main transaction
            client.postgrest.from("transactions").update(buildJsonObject {
                put("total_amount", totalAmount)
                put("description", title.ifBlank { null })
            }) {
                filter { eq("id", transactionId) }
            }

            // 2. Delete existing related records
            client.postgrest.from("transaction_payors").delete {
                filter { eq("transaction_id", transactionId) }
            }
            client.postgrest.from("transaction_splits").delete {
                filter { eq("transaction_id", transactionId) }
            }
            client.postgrest.from("transaction_items").delete {
                filter { eq("transaction_id", transactionId) }
            }

            // 3. Re-insert with new data (same logic as submitTransactions)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val createdAt = isoFormat.format(Date())
            
            val userTotalSplitOwed = mutableMapOf<Long, Double>()
            val userTotalInitialPayments = mutableMapOf<Long, Double>()
            
            entries.forEach { entry ->
                val itemResponse = client.postgrest.from("transaction_items").insert(
                    TransactionItemInsert(
                        transactionId = transactionId,
                        amount = entry.amount,
                        category = entry.category,
                        itemDescription = entry.title.ifBlank { null },
                        createdAt = createdAt
                    )
                ) { select() }.decodeSingle<TransactionItemFull>()

                val itemId = itemResponse.id ?: throw Exception("Failed to get transaction_item ID")

                entry.payors.forEach { payor ->
                    userTotalInitialPayments[payor.userId] = 
                        (userTotalInitialPayments[payor.userId] ?: 0.0) + payor.amount
                }

                if (entry.payors.isNotEmpty()) {
                    val payorRecords = entry.payors
                        .filter { it.amount > 0.0 }
                        .map { payor ->
                            TransactionPayorInsert(
                                transactionId = transactionId,
                                userId = payor.userId,
                                initialAmountPaid = payor.amount,
                                currentAmountPaid = payor.amount,
                                excessAmount = 0.0,
                                transactionItemsId = itemId,
                                status = 0
                            )
                        }
                    
                    if (payorRecords.isNotEmpty()) {
                        client.postgrest.from("transaction_payors").insert(payorRecords)
                    }
                }

                val membersToSplit = if (entry.includedMemberIds.isNotEmpty()) {
                    groupMembers.filter { member -> entry.includedMemberIds.contains(member.id) }
                } else {
                    groupMembers
                }
                
                if (membersToSplit.isNotEmpty()) {
                    val splitAmount = entry.amount / membersToSplit.size
                    
                    membersToSplit.forEach { member ->
                        userTotalSplitOwed[member.id!!] = 
                            (userTotalSplitOwed[member.id!!] ?: 0.0) + splitAmount
                    }
                    
                    client.postgrest.from("transaction_splits").insert(
                        membersToSplit.map { member ->
                            TransactionSplitInsert(
                                transactionId = transactionId,
                                userId = member.id!!,
                                amount = splitAmount,
                                transactionItemsId = itemId
                            )
                        }
                    )
                }
            }
            
            // Update payors with calculated values
            userTotalInitialPayments.forEach { (userId, totalInitialPaid) ->
                val userSplitOwed = userTotalSplitOwed[userId] ?: 0.0
                val currentPaid = if (totalInitialPaid > userSplitOwed) userSplitOwed else totalInitialPaid
                val excess = if (totalInitialPaid > userSplitOwed) totalInitialPaid - userSplitOwed else 0.0
                val status = when {
                    currentPaid == 0.0 -> 0
                    currentPaid >= userSplitOwed -> 1
                    else -> 2
                }
                client.postgrest.from("transaction_payors").update(buildJsonObject {
                    put("excess_amount", excess)
                    put("status", status)
                }) {
                    filter { eq("transaction_id", transactionId); eq("user_id", userId) }
                }
            }

            // Refresh balances
            groupMembers.forEach { member ->
                member.id?.let { BalanceHelper.refreshUserBalance(it) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
