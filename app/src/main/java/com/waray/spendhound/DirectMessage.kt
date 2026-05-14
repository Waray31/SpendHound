package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DirectMessage(
    val id: Long? = null,
    @SerialName("sender_id") val senderId: Long? = null,
    @SerialName("recipient_id") val recipientId: Long? = null,
    val content: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @kotlinx.serialization.Transient var reactions: MutableList<MessageReaction> = mutableListOf()
)
