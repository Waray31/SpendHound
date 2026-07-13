package com.waray.spendhound.ui.multi_transaction

import com.waray.spendhound.BalanceHelper
import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMember
import com.waray.spendhound.PayerGroup
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CachedJsonBlob
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MultiTransactionRepository(
    private val database: AppDatabase? = null
) {

    private val jsonBlobDao = database?.jsonBlobDao()

    suspend fun getGroups(): List<PayerGroup> = withContext(Dispatchers.IO) {
        try {
            val fetchedGroups = DeclareDatabase.groupsTable.select().decodeList<PayerGroup>()
            // Cache groups
            jsonBlobDao?.upsert(CachedJsonBlob("all_groups", Json.encodeToString(fetchedGroups), System.currentTimeMillis()))
            fetchedGroups
        } catch (e: Exception) {
            // Load from cache
            val cached = jsonBlobDao?.get("all_groups")
            if (cached != null) {
                Json.decodeFromString<List<PayerGroup>>(cached.json)
            } else {
                emptyList()
            }
        }
    }

    suspend fun getGroupMembers(groupId: Long): List<User> = withContext(Dispatchers.IO) {
        try {
            val memberIds = DeclareDatabase.groupMembersTable.select {
                filter { eq("group_id", groupId) }
            }.decodeList<GroupMember>().mapNotNull { it.userId }

            if (memberIds.isEmpty()) return@withContext emptyList()

            val fetchedMembers = DeclareDatabase.usersTable.select {
                filter { isIn("user_id", memberIds) }
            }.decodeList<User>()
            
            // Cache members
            jsonBlobDao?.upsert(CachedJsonBlob("group_members_$groupId", Json.encodeToString(fetchedMembers), System.currentTimeMillis()))
            fetchedMembers
        } catch (e: Exception) {
            // Load from cache
            val cached = jsonBlobDao?.get("group_members_$groupId")
            if (cached != null) {
                Json.decodeFromString<List<User>>(cached.json)
            } else {
                emptyList()
            }
        }
    }

    suspend fun submitTransactions(
        groupId: Long?,
        createdBy: Long,
        title: String,
        entries: List<TransactionEntry>,
        groupMembers: List<User>,
        status: Int = 2
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val createdAt = isoFormat.format(Date())
            val totalAmount = entries.sumOf { it.amount }

            // 1. Insert into transactions
            val txResponse = DeclareDatabase.transactionsTable.insert(
                TransactionInsert(
                    totalAmount = totalAmount,
                    description = title.ifBlank { null },
                    groupId = groupId,
                    createdBy = createdBy,
                    createdAt = createdAt,
                    status = status
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
                val itemResponse = DeclareDatabase.transactionItemsTable.insert(
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
                        DeclareDatabase.transactionPayorsTable.insert(payorRecords)
                    }
                }

                // 4. Split only among included members for this entry
                val membersToSplit = if (entry.includedMemberIds.isNotEmpty()) {
                    groupMembers.filter { member -> entry.includedMemberIds.contains(member.id) }
                } else {
                    groupMembers // Default to all members if no specific inclusion list
                }
                
                if (membersToSplit.isNotEmpty()) {
                    // Calculate split amount per member, accounting for covers
                    // If there's only one item, entry.amount should be used.
                    // If multiple items, each item's amount is split independently.
                    val baseSplitAmount = entry.amount / membersToSplit.size
                    
                    val splitRecords = membersToSplit.map { member ->
                        val memberId = member.id ?: 0L
                        val covererId = entry.coveredByMap[memberId]
                        val amount = if (covererId != null) {
                            0.0 // Covered member pays 0
                        } else {
                            val coversCount = entry.coveredByMap.values.count { it == memberId }
                            baseSplitAmount * (1 + coversCount)
                        }

                        // Track each user's total split owed across all items
                        userTotalSplitOwed[memberId] = 
                            (userTotalSplitOwed[memberId] ?: 0.0) + amount

                        TransactionSplitInsert(
                            transactionId = txId,
                            userId = memberId,
                            amount = amount,
                            transactionItemsId = itemId,
                            coveredByUserId = covererId
                        )
                    }
                    
                    DeclareDatabase.transactionSplitsTable.insert(splitRecords)
                }
            }
            
            // 5. Update all payors with calculated excess_amount and status based on total payments
            // Also ensure covered users are marked as "Paid" (status 1) even if they paid 0
            val allInvolvedUserIds = (userTotalSplitOwed.keys + userTotalInitialPayments.keys).distinct()
            
            allInvolvedUserIds.forEach { userId ->
                val userSplitOwed = userTotalSplitOwed[userId] ?: 0.0
                val totalInitialPaid = userTotalInitialPayments[userId] ?: 0.0
                
                // A user is "Paid" if their initial payment >= split owed, 
                // OR if they were covered (split owed is 0 and they are in the coveredByMap)
                val wasCovered = userSplitOwed < 0.01 && entries.any { it.coveredByMap.containsKey(userId) }
                
                val currentPaid = if (totalInitialPaid > userSplitOwed) userSplitOwed else totalInitialPaid
                val excess = if (totalInitialPaid > userSplitOwed) totalInitialPaid - userSplitOwed else 0.0
                
                val status = when {
                    wasCovered -> 1 // Marked as Paid if covered
                    totalInitialPaid >= userSplitOwed - 0.01 -> 1 // Settled or Creditor
                    totalInitialPaid < 0.01 -> 0 // Unpaid
                    else -> 2 // Partial
                }

                // Check if payor records exist for this user
                val existingPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { eq("transaction_id", txId); eq("user_id", userId) }
                }.decodeList<TransactionPayorTable>()

                if (existingPayors.isNotEmpty()) {
                    // Update all existing records to new status, but usually we just want to update the "lead" one with excess
                    // For simplicity, let's update them all to the same status, but only the first one gets the full currentPaid and excess
                    existingPayors.forEachIndexed { i, payor ->
                        val isLead = i == 0
                        val payorId = payor.id ?: return@forEachIndexed
                        DeclareDatabase.transactionPayorsTable.update(buildJsonObject {
                            put("status", status)
                            if (isLead) {
                                put("excess_amount", excess)
                                put("current_amount_paid", currentPaid)
                            } else {
                                put("excess_amount", 0.0)
                                // Keep its existing current_amount_paid (which was its initial contribution to that item)
                            }
                        }) {
                            filter { eq("id", payorId) }
                        }
                    }
                } else if (wasCovered || totalInitialPaid > 0.01) {
                    // Create payor record for covered user or someone who paid
                    DeclareDatabase.transactionPayorsTable.insert(
                        TransactionPayorInsert(
                            transactionId = txId,
                            userId = userId,
                            initialAmountPaid = 0.0,
                            currentAmountPaid = currentPaid,
                            excessAmount = excess,
                            transactionItemsId = null,
                            status = status
                        )
                    )
                }
            }

            // 6. Refresh user_balance for all involved members
            groupMembers.forEach { member ->
                member.id?.let { BalanceHelper.refreshUserBalance(it) }
            }

            // 7. Record History
            DeclareDatabase.transactionHistoryTable.insert(
                TransactionHistoryInsert(
                    transactionId = txId,
                    userId = createdBy,
                    action = "CREATE",
                    details = "Transaction created with ${entries.size} items"
                )
            )

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
            
            DeclareDatabase.transactionPayorsTable.update(buildJsonObject {
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
            DeclareDatabase.transactionsTable.update(buildJsonObject {
                put("total_amount", totalAmount)
                put("description", title.ifBlank { null })
                put("group_id", groupId)
                put("status", 2) // Reset to standard status once info is provided
            }) {
                filter { eq("id", transactionId) }
            }

            // 2. Delete existing related records
            DeclareDatabase.transactionPayorsTable.delete {
                filter { eq("transaction_id", transactionId) }
            }
            DeclareDatabase.transactionSplitsTable.delete {
                filter { eq("transaction_id", transactionId) }
            }
            DeclareDatabase.transactionItemsTable.delete {
                filter { eq("transaction_id", transactionId) }
            }

            // 3. Re-insert with new data (same logic as submitTransactions)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val createdAt = isoFormat.format(Date())
            
            val userTotalSplitOwed = mutableMapOf<Long, Double>()
            val userTotalInitialPayments = mutableMapOf<Long, Double>()
            
            entries.forEach { entry ->
                val itemResponse = DeclareDatabase.transactionItemsTable.insert(
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
                        DeclareDatabase.transactionPayorsTable.insert(payorRecords)
                    }
                }

                val membersToSplit = if (entry.includedMemberIds.isNotEmpty()) {
                    groupMembers.filter { member -> entry.includedMemberIds.contains(member.id) }
                } else {
                    groupMembers
                }
                
                if (membersToSplit.isNotEmpty()) {
                    // Calculate split amount per member, accounting for covers
                    val baseSplitAmount = entry.amount / membersToSplit.size
                    
                    val splitRecords = membersToSplit.map { member ->
                        val covererId = entry.coveredByMap[member.id]
                        val amount = if (covererId != null) {
                            0.0 // Covered member pays 0
                        } else {
                            val coversCount = entry.coveredByMap.values.count { it == member.id }
                            baseSplitAmount * (1 + coversCount)
                        }

                        // Track each user's total split owed across all items
                        userTotalSplitOwed[member.id!!] = 
                            (userTotalSplitOwed[member.id!!] ?: 0.0) + amount

                        TransactionSplitInsert(
                            transactionId = transactionId,
                            userId = member.id!!,
                            amount = amount,
                            transactionItemsId = itemId,
                            coveredByUserId = covererId
                        )
                    }
                    
                    DeclareDatabase.transactionSplitsTable.insert(splitRecords)
                }
            }
            
            // Update payors with calculated values
            // Also ensure covered users are marked as "Paid" (status 1) even if they paid 0
            val allInvolvedUserIds = (userTotalSplitOwed.keys + userTotalInitialPayments.keys).distinct()
            
            allInvolvedUserIds.forEach { userId ->
                val userSplitOwed = userTotalSplitOwed[userId] ?: 0.0
                val totalInitialPaid = userTotalInitialPayments[userId] ?: 0.0
                
                // A user is "Paid" if their initial payment >= split owed, 
                // OR if they were covered (split owed is 0 and they are in the coveredByMap)
                val wasCovered = userSplitOwed < 0.01 && entries.any { it.coveredByMap.containsKey(userId) }
                
                val currentPaid = if (totalInitialPaid > userSplitOwed) userSplitOwed else totalInitialPaid
                val excess = if (totalInitialPaid > userSplitOwed) totalInitialPaid - userSplitOwed else 0.0
                
                val status = when {
                    wasCovered -> 1 // Marked as Paid if covered
                    totalInitialPaid >= userSplitOwed - 0.01 -> 1 // Settled or Creditor
                    totalInitialPaid < 0.01 -> 0 // Unpaid
                    else -> 2 // Partial
                }

                // Check if payor records exist for this user (they might not have been an initial payer)
                val existingPayors = DeclareDatabase.transactionPayorsTable.select {
                    filter { eq("transaction_id", transactionId); eq("user_id", userId) }
                }.decodeList<TransactionPayorTable>()

                if (existingPayors.isNotEmpty()) {
                    existingPayors.forEachIndexed { i, payor ->
                        val isLead = i == 0
                        val payorId = payor.id ?: return@forEachIndexed
                        DeclareDatabase.transactionPayorsTable.update(buildJsonObject {
                            put("excess_amount", if (isLead) excess else 0.0)
                            put("status", status)
                            put("current_amount_paid", if (isLead) currentPaid else payor.currentAmountPaid)
                        }) {
                            filter { eq("id", payorId) }
                        }
                    }
                } else if (wasCovered || totalInitialPaid > 0.01) {
                    // Create payor record for covered user
                    DeclareDatabase.transactionPayorsTable.insert(
                        TransactionPayorInsert(
                            transactionId = transactionId,
                            userId = userId,
                            initialAmountPaid = 0.0,
                            currentAmountPaid = currentPaid,
                            excessAmount = excess,
                            transactionItemsId = null,
                            status = status
                        )
                    )
                }
            }

            // Refresh balances
            groupMembers.forEach { member ->
                member.id?.let { BalanceHelper.refreshUserBalance(it) }
            }

            // Record History
            DeclareDatabase.transactionHistoryTable.insert(
                TransactionHistoryInsert(
                    transactionId = transactionId,
                    userId = createdBy,
                    action = "EDIT",
                    details = "Transaction updated with ${entries.size} items"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
