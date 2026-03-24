package com.waray.spendhound.ui.multi_transaction

import com.waray.spendhound.DeclareDatabase
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
        val group = client.postgrest.from("groups").select {
            filter {
                eq("group_id", groupId)
            }
        }.decodeSingle<PayerGroup>()
        
        val memberIds = group.members?.filterNotNull() ?: emptyList()
        if (memberIds.isEmpty()) return@withContext emptyList()
        
        client.postgrest.from("users").select {
            filter {
                isIn("user_id", memberIds)
            }
        }.decodeList<User>()
    }

    suspend fun submitTransactions(
        groupId: Long,
        createdBy: Long,
        entries: List<TransactionEntry>,
        groupMembers: List<User>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val createdAt = isoFormat.format(Date())
            
            entries.forEach { entry ->
                // 1. Insert into transactions
                val transaction = TransactionTable(
                    groupId = groupId,
                    title = entry.title,
                    totalAmount = entry.amount,
                    createdBy = createdBy,
                    createdAt = createdAt
                )
                
                val insertedTx = client.postgrest.from("transactions").insert(transaction) {
                    select()
                }.decodeSingle<TransactionTable>()
                
                val txId = insertedTx.id ?: throw Exception("Failed to get transaction ID")

                // 2. Insert into transaction_payors
                val payorRecords = entry.payors.map { 
                    TransactionPayorTable(transactionId = txId, userId = it.userId, amount = it.amount)
                }
                client.postgrest.from("transaction_payors").insert(payorRecords)

                // 3. Compute equal split and insert into transaction_splits
                if (groupMembers.isNotEmpty()) {
                    val splitAmount = entry.amount / groupMembers.size
                    val splitRecords = groupMembers.map { 
                        TransactionSplitTable(transactionId = txId, userId = it.id!!, amount = splitAmount)
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
