package com.waray.spendhound.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_transactions")
data class PendingTransaction(
    @PrimaryKey val localId: String,
    val groupId: Long?,        // Nullable for unassigned offline transactions
    val createdByUserId: Long,
    val description: String?,
    val totalAmount: Double,
    val itemsJson: String,      // full serialized List<TransactionEntry> as JSON string
    val createdAt: String,      // ISO timestamp
    val status: Int = 0,        // 0=pending, 1=syncing, 2=failed
    val retryCount: Int = 0,
    val failureReason: String? = null
)
