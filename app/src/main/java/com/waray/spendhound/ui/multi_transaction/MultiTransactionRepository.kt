package com.waray.spendhound.ui.multi_transaction

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import io.github.jan.supabase.postgrest.postgrest
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
            val totalSplitPerMember = totalAmount / groupMembers.size
            
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

                // 4. Equal split across all group members
                if (groupMembers.isNotEmpty()) {
                    val splitAmount = entry.amount / groupMembers.size
                    client.postgrest.from("transaction_splits").insert(
                        groupMembers.map { member ->
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
                // Calculate current_amount_paid (capped at total split)
                val currentPaid = if (totalInitialPaid > totalSplitPerMember) {
                    totalSplitPerMember
                } else {
                    totalInitialPaid
                }
                
                // Calculate excess
                val excess = if (totalInitialPaid > totalSplitPerMember) {
                    totalInitialPaid - totalSplitPerMember
                } else {
                    0.0
                }
                
                // Determine status based on current_amount_paid
                val status = when {
                    currentPaid == 0.0 -> 0  // unpaid
                    currentPaid >= totalSplitPerMember -> 1  // settled
                    else -> 2  // pending (partial payment)
                }
                
                // Update all payor records for this user across all items
                client.postgrest.from("transaction_payors").update(
                    TransactionPayorPartialUpdate(
                        excessAmount = excess,
                        status = status
                    )
                ) {
                    filter {
                        eq("transaction_id", txId)
                        eq("user_id", userId)
                    }
                }
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
            
            client.postgrest.from("transaction_payors").update(
                TransactionPayorUpdate(
                    currentAmountPaid = newAmountPaid,
                    excessAmount = excess,
                    status = status
                )
            ) {
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
}
