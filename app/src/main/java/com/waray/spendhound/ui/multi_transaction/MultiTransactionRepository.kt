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
        }.decodeSingleOrNull<PayerGroup>() ?: return@withContext emptyList()
        
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
                // Corrected mapping: transactionType gets the entry.category
                val transaction = TransactionInsert(
                    groupId = groupId,
                    transactionType = entry.category, // Use category for transaction_type column
                    transactionDetail = entry.title,   // Use user-entered title for transaction_detail column
                    paymentAmount = entry.amount,
                    creatorId = createdBy,
                    createdAt = createdAt,
                    status = 0
                )
                
                // Supabase will return the inserted row with the generated ID
                val response = client.postgrest.from("transactions").insert(transaction) {
                    select()
                }.decodeSingle<TransactionResponse>()
                
                val txId = response.id ?: throw Exception("Failed to get transaction ID")

                // 2. Insert into transaction_payors
                // If single payor mode, ensure the entry has the total amount assigned to that payor
                val payorRecords = entry.payors.map { 
                    TransactionPayorTable(
                        transactionId = txId, 
                        userId = it.userId, 
                        amount = if (it.amount > 0) it.amount else entry.amount // Fix for 0 amount issue
                    )
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
