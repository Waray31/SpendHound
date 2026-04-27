package com.waray.spendhound.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey val id: Long,
    val groupId: Long,
    val userId: Long,
    val message: String?,
    val transactionId: Long?,
    val createdAt: String?,
    val updatedAt: String?,
    val isDeleted: Boolean,
    val senderName: String?,
    val senderProfileImage: String?
)

@Entity(tableName = "cached_transactions")
data class CachedTransaction(
    @PrimaryKey val id: Long,
    val groupId: Long?,
    val description: String?,
    val totalAmount: Double,
    val status: Int?,
    val createdBy: Long?,
    val createdAt: String?
)

@Entity(tableName = "cached_json_blobs")
data class CachedJsonBlob(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long
)
