package com.waray.spendhound.data.repository

import com.waray.spendhound.DeclareDatabase
import com.waray.spendhound.GroupMessage
import com.waray.spendhound.User
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CacheKeys
import com.waray.spendhound.data.local.CachedJsonBlob
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

    fun getMessages(groupId: Long): Flow<List<CachedMessage>> = flow {
        emit(db.messageDao().getMessages(groupId))

        val cacheKey = CacheKeys.messageReads(groupId)
        val ts = db.jsonBlobDao().get(cacheKey)
        val isStale = ts == null || (System.currentTimeMillis() - ts.fetchedAt) > CacheKeys.STALE_READS
        if (!isStale) return@flow

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

            raw.filter { !it.isDeleted }.mapNotNull { msg ->
                val sender = usersById[msg.userId]
                CachedMessage(
                    id = msg.id ?: return@mapNotNull null,
                    groupId = msg.groupId ?: groupId,
                    userId = msg.userId ?: 0L,
                    message = msg.message,
                    transactionId = msg.transactionId,
                    createdAt = msg.createdAt,
                    updatedAt = msg.updatedAt,
                    isDeleted = msg.isDeleted,
                    senderName = sender?.username,
                    senderProfileImage = sender?.profileImageUrl
                )
            }
        }

        db.messageDao().deleteByGroup(groupId)
        db.messageDao().insertAll(fresh)
        db.jsonBlobDao().upsert(CachedJsonBlob(cacheKey, "1", System.currentTimeMillis()))
        emit(fresh)
    }.flowOn(Dispatchers.IO)

    fun getTransactions(groupId: Long): Flow<List<CachedTransaction>> = flow {
        emit(db.transactionDao().getTransactions(groupId))

        val cacheKey = CacheKeys.groupExpenses(groupId)
        val ts = db.jsonBlobDao().get(cacheKey)
        val isStale = ts == null || (System.currentTimeMillis() - ts.fetchedAt) > CacheKeys.STALE_TRANSACTIONS
        if (!isStale) return@flow

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
                    createdAt = tx.createdAt,
                    isArchived = tx.isArchived ?: false
                )
            }
        }

        db.transactionDao().deleteByGroup(groupId)
        db.transactionDao().insertAll(fresh)
        db.jsonBlobDao().upsert(CachedJsonBlob(cacheKey, "1", System.currentTimeMillis()))
        emit(fresh)
    }.flowOn(Dispatchers.IO)

    suspend fun invalidateMessages(groupId: Long) {
        db.jsonBlobDao().delete(CacheKeys.messageReads(groupId))
    }

    suspend fun invalidateTransactions(groupId: Long) {
        db.jsonBlobDao().delete(CacheKeys.groupExpenses(groupId))
    }
}
