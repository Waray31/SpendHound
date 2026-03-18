package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
data class PayerGroup(
    var groupId: String? = null,
    var groupName: String? = null,
    var members: MutableList<String?>? = null,
    var createdBy: String? = null,
    var memberDisplayNames: MutableList<String?>? = null
) {
    // Getters for compatibility
    fun getGroupId(): String? = groupId
    fun getGroupName(): String? = groupName
    fun getMembers(): MutableList<String?>? = members
    fun getCreatedBy(): String? = createdBy
    fun getMemberDisplayNames(): MutableList<String?>? = memberDisplayNames
}
