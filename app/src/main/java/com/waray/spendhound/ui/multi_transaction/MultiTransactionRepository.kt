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

                // 3. Insert ALL group members into transaction_payors at amount=0,
                //    then update actual payors with their real paid amount.
                //    This ensures every member has a row so the creator can later
                //    update any member's payment with a simple UPDATE (no INSERT needed).
                if (groupMembers.isNotEmpty()) {
                    val allMemberRecords = groupMembers.map { member ->
                        TransactionPayorInsert(
                            transactionId = txId,
                            userId = member.id!!,
                            amount = 0.0,
                            transactionItemsId = itemId
                        )
                    }
                    client.postgrest.from("transaction_payors").insert(allMemberRecords)

                    // Update actual payors with their real paid amount
                    entry.payors.forEach { payor ->
                        val paidAmount = if (payor.amount > 0) payor.amount else entry.amount
                        client.postgrest.from("transaction_payors").update({
                            set("amount", paidAmount)
                        }) {
                            filter {
                                eq("transaction_id", txId)
                                eq("user_id", payor.userId)
                                eq("transaction_items_id", itemId)
                            }
                        }
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

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
