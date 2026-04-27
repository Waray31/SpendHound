package com.waray.spendhound.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages WHERE groupId = :groupId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeMessages(groupId: Long): Flow<List<CachedMessage>>

    @Query("SELECT * FROM cached_messages WHERE groupId = :groupId AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getMessages(groupId: Long): List<CachedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CachedMessage>)

    @Query("DELETE FROM cached_messages WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeTransactions(groupId: Long): Flow<List<CachedTransaction>>

    @Query("SELECT * FROM cached_transactions WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun getTransactions(groupId: Long): List<CachedTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CachedTransaction>)

    @Query("DELETE FROM cached_transactions WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)
}

@Dao
interface JsonBlobDao {
    @Query("SELECT * FROM cached_json_blobs WHERE `key` = :key")
    suspend fun get(key: String): CachedJsonBlob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(blob: CachedJsonBlob)

    @Query("DELETE FROM cached_json_blobs WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_json_blobs WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)
}
