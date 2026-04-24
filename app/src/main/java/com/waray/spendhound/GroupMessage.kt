package com.waray.spendhound

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupMessage(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("transaction_id")
    val transactionId: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    // UI-only fields — excluded from serialization
    @kotlinx.serialization.Transient var senderName: String? = null,
    @kotlinx.serialization.Transient var senderProfileImage: String? = null,
    @kotlinx.serialization.Transient var transactionTitle: String? = null,
    @kotlinx.serialization.Transient var transactionAmount: Double = 0.0,
    @kotlinx.serialization.Transient var transactionStatus: String? = null
)

@Serializable
data class MessageRead(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("message_id")
    val messageId: Long? = null,
    @SerialName("group_id")
    val groupId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("read_at")
    val readAt: String? = null
)

@Serializable
data class TransactionRead(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("transaction_id")
    val transactionId: Long? = null,
    @SerialName("group_id")
    val groupId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("read_at")
    val readAt: String? = null
)

@Serializable
data class GroupMessageReaction(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("message_id")
    val messageId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("emoji")
    val emoji: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class GroupMessageInsert(
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("message") val message: String,
    // Exclude transaction_id from JSON entirely when null so it is not sent as a column
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("transaction_id") val transactionId: Long? = null
)

@Serializable
data class MessageReadInsert(
    @SerialName("message_id") val messageId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("group_id") val groupId: Long,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class TransactionReadInsert(
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("group_id") val groupId: Long,
    @SerialName("read_at") val readAt: String? = null
)

