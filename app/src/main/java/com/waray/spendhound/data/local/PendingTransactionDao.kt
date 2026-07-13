package com.waray.spendhound.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pending: PendingTransaction)

    @Query("SELECT * FROM pending_transactions")
    suspend fun getAll(): List<PendingTransaction>

    @Query("SELECT * FROM pending_transactions WHERE status = 0")
    suspend fun getPending(): List<PendingTransaction>

    @Query("UPDATE pending_transactions SET status = :status, failureReason = :failureReason WHERE localId = :localId")
    suspend fun updateStatus(localId: String, status: Int, failureReason: String? = null)

    @Query("UPDATE pending_transactions SET retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun incrementRetry(localId: String)

    @Query("DELETE FROM pending_transactions WHERE localId = :localId")
    suspend fun delete(localId: String)

    @Query("SELECT COUNT(*) FROM pending_transactions")
    fun getCount(): Flow<Int>
}
