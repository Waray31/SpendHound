package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CrewMember(
    val id: Long? = null,
    @SerialName("owner_user_id") val ownerUserId: Long? = null,
    @SerialName("member_user_id") val memberUserId: Long? = null,
    val status: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("responded_at") val respondedAt: String? = null,
    @kotlinx.serialization.Transient val lastMessage: String? = null,
    @kotlinx.serialization.Transient val lastMessageSenderId: Long? = null,
    @kotlinx.serialization.Transient val unreadCount: Int = 0
)

@Serializable
data class CrewMemberWithUser(
    val crewMember: CrewMember,
    val user: User,
    val isOwner: Boolean
)
