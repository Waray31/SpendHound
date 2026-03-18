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
}
