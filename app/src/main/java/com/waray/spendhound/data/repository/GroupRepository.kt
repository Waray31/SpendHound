package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CachedMessage
import com.waray.spendhound.data.local.CachedTransaction
import com.waray.spendhound.ui.multi_transaction.TransactionFull
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class GroupRepository(private val db: AppDatabase) {

    // Emit cached data immediately, then fetch fresh from Supabase and update cache
    fun getMessages(groupId: Long): Flow<List<CachedMessage>> = flow {
        // 1. Emit cached data immediately
        emit(db.messageDao().getMessages(groupId))

        // 2. Fetch fresh from Supabase
        val fresh = withContext(Dispatchers.IO) {
            val raw = DeclareDatabase.groupMessagesTable.select {
                filter { eq("group_id", groupId) }
                order("created_at", Order.ASCENDING)
                limit(200)
            }.decodeList<GroupMessage>()

            val userIds = raw.mapNotNull { it.userId }.distinct()
            val users = if (userIds.isNotEmpty()) {
                DeclareDatabase.usersTable.select {
                    filter { isIn("user_id", userIds) }
                }.decodeList<User>()
            } else emptyList()
            val usersById = users.associateBy { it.id }

            raw.filter { !it.isDeleted }.map { msg ->
                val sender = usersById[msg.userId]
                CachedMessage(
                    id = msg.id ?: return@map null,
                    groupId = msg.groupId ?: groupId,
                    userId = msg.userId ?: 0L,
                    message = msg.message,
                    transactionId = msg.transactionId,
                    createdAt = msg.createdAt,
                    updatedAt = msg.updatedAt,
                    isDeleted = msg.isDeleted,
                    senderName = sender?.username,
                    senderProfileImage = sender?.id?.let { "$it/$it.jpg" }
                )
            }.filterNotNull()
        }

        // 3. Save to Room
        db.messageDao().deleteByGroup(groupId)
        db.messageDao().insertAll(fresh)

        // 4. Emit updated data
        emit(fresh)
    }.flowOn(Dispatchers.IO)

    fun getTransactions(groupId: Long): Flow<List<CachedTransaction>> = flow {
        // 1. Emit cached data immediately
        emit(db.transactionDao().getTransactions(groupId))

        // 2. Fetch fresh from Supabase
        val fresh = withContext(Dispatchers.IO) {
            DeclareDatabase.transactionsTable.select {
                filter { eq("group_id", groupId) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<TransactionFull>().mapNotNull { tx ->
                CachedTransaction(
                    id = tx.id ?: return@mapNotNull null,
                    groupId = tx.groupId,
                    description = tx.description,
                    totalAmount = tx.totalAmount,
                    status = tx.status,
                    createdBy = tx.createdBy,
                    createdAt = tx.createdAt
                )
            }
        }

        // 3. Save to Room
        db.transactionDao().deleteByGroup(groupId)
        db.transactionDao().insertAll(fresh)

        // 4. Emit updated data
        emit(fresh)
    }.flowOn(Dispatchers.IO)
}
