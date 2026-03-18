package com.waray.spendhound

class PayerGroup {
    var groupId: String? = null
    var groupName: String? = null
    var members: MutableList<String?>? = null // Now stores UIDs (was usernames)
    var createdBy: String? = null
    var memberDisplayNames: MutableList<String?>? = null // Display names for UI

    constructor()

    constructor(
        groupId: String?,
        groupName: String?,
        members: MutableList<String?>?,
        createdBy: String?
    ) {
        this.groupId = groupId
        this.groupName = groupName
        this.members = members
        this.createdBy = createdBy
    }

    // New constructor with display names
    constructor(
        groupId: String?,
        groupName: String?,
        members: MutableList<String?>?,
        createdBy: String?,
        memberDisplayNames: MutableList<String?>?
    ) {
        this.groupId = groupId
        this.groupName = groupName
        this.members = members
        this.createdBy = createdBy
        this.memberDisplayNames = memberDisplayNames
    }

    // Getters aligned with database schema
    fun getGroupId(): String? = groupId
    fun getGroupName(): String? = groupName
    fun getMembers(): MutableList<String?>? = members
    fun getCreatedBy(): String? = createdBy
    fun getMemberDisplayNames(): MutableList<String?>? = memberDisplayNames

    // Setters aligned with database schema
    fun setGroupId(id: String?) {
        this.groupId = id
    }
    fun setGroupName(name: String?) {
        this.groupName = name
    }
    fun setMembers(members: MutableList<String?>?) {
        this.members = members
    }
    fun setCreatedBy(createdBy: String?) {
        this.createdBy = createdBy
    }
    fun setMemberDisplayNames(names: MutableList<String?>?) {
        this.memberDisplayNames = names
    }
}
