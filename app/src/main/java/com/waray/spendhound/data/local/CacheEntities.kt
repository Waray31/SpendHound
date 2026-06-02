package com.waray.spendhound.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey @SerialName("id") val id: Long,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("message") val message: String?,
    @SerialName("transaction_id") val transactionId: Long?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
    @SerialName("is_deleted") val isDeleted: Boolean,
    val senderName: String?,
    val senderProfileImage: String?
)

@Serializable
@Entity(tableName = "cached_transactions")
data class CachedTransaction(
    @PrimaryKey @SerialName("id") val id: Long,
    @SerialName("group_id") val groupId: Long?,
    @SerialName("description") val description: String?,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("status") val status: Int?,
    @SerialName("created_by") val createdBy: Long?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("is_archived") val isArchived: Boolean = false
)

@Serializable
@Entity(tableName = "cached_json_blobs")
data class CachedJsonBlob(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long
)
