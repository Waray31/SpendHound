package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PayerGroup(
    @SerialName("group_id")
    var groupId: Long? = null,
    @SerialName("group_name")
    var groupName: String? = null,
    @SerialName("createdby_id")
    var createdBy: Long? = null,
    @SerialName("created_at")
    var createdAt: String? = null,
    @Transient
    var memberDisplayNames: MutableList<String?>? = null
)

@Serializable
data class GroupMember(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
