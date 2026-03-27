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
            val txInsert = TransactionInsert(
                totalAmount = totalAmount,
                description = title.ifBlank { null },
                groupId = groupId,
                createdBy = createdBy,
                createdAt = createdAt,
                status = 0
            )
            val txResponse = client.postgrest.from("transactions").insert(txInsert) {
                select()
            }.decodeSingle<TransactionResponse>()

            val txId = txResponse.id ?: throw Exception("Failed to get transaction ID")

            // 2-4. Per entry: insert item, then payors and splits referencing that item
            entries.forEach { entry ->
                val itemInsert = TransactionItemInsert(
                    transactionId = txId,
                    amount = entry.amount,
                    category = entry.category,
                    itemDescription = entry.title.ifBlank { null },
                    createdAt = createdAt
                )
                val itemResponse = client.postgrest.from("transaction_items").insert(itemInsert) {
                    select()
                }.decodeSingle<TransactionItemFull>()

                val itemId = itemResponse.id ?: throw Exception("Failed to get transaction_item ID")

                // 3. Insert payors for this item
                val payorRecords = entry.payors.map { payor ->
                    TransactionPayorInsert(
                        transactionId = txId,
                        userId = payor.userId,
                        amount = if (payor.amount > 0) payor.amount else entry.amount,
                        transactionItemsId = itemId
                    )
                }
                if (payorRecords.isNotEmpty()) {
                    client.postgrest.from("transaction_payors").insert(payorRecords)
                }

                // 4. Equal split across all group members for this item
                if (groupMembers.isNotEmpty()) {
                    val splitAmount = entry.amount / groupMembers.size
                    val splitRecords = groupMembers.map { member ->
                        TransactionSplitInsert(
                            transactionId = txId,
                            userId = member.id!!,
                            amount = splitAmount,
                            transactionItemsId = itemId
                        )
                    }
                    client.postgrest.from("transaction_splits").insert(splitRecords)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
