package com.waray.spendhound

class Transaction {
    var transactionType: String? = null
    var paymentAmount: Double = 0.0
    var multilineStr: String? = null
    var payorsList: MutableList<String?>? = null // Now stores UIDs (was usernames)
    var amountsPaidList: MutableList<Double?>? = null
    var usernamePost: String? = null // Now stores UID (was username)
    var totalIndividualPayment: Double = 0.0
    var groupId: String? = null
    var groupName: String? = null

    // Display name fields (for backward compatibility with old data)
    var payorsDisplayNames: MutableList<String?>? = null
    var posterDisplayName: String? = null

    constructor(
        transactionType: String?,
        paymentAmount: Double,
        multilineStr: String?,
        payorsList: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        usernamePost: String?,
        totalIndividualPayment: Double
    ) {
        this.transactionType = transactionType
        this.paymentAmount = paymentAmount
        this.multilineStr = multilineStr
        this.payorsList = payorsList
        this.amountsPaidList = amountsPaidList
        this.usernamePost = usernamePost
        this.totalIndividualPayment = totalIndividualPayment
    }

    constructor(
        transactionType: String?,
        paymentAmount: Double,
        multilineStr: String?,
        payorsList: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        usernamePost: String?,
        totalIndividualPayment: Double,
        groupId: String?,
        groupName: String?
    ) {
        this.transactionType = transactionType
        this.paymentAmount = paymentAmount
        this.multilineStr = multilineStr
        this.payorsList = payorsList
        this.amountsPaidList = amountsPaidList
        this.usernamePost = usernamePost
        this.totalIndividualPayment = totalIndividualPayment
        this.groupId = groupId
        this.groupName = groupName
    }

    // New constructor with display names for UID-based storage
    constructor(
        transactionType: String?,
        paymentAmount: Double,
        multilineStr: String?,
        payorsList: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        usernamePost: String?,
        totalIndividualPayment: Double,
        groupId: String?,
        groupName: String?,
        payorsDisplayNames: MutableList<String?>?,
        posterDisplayName: String?
    ) {
        this.transactionType = transactionType
        this.paymentAmount = paymentAmount
        this.multilineStr = multilineStr
        this.payorsList = payorsList
        this.amountsPaidList = amountsPaidList
        this.usernamePost = usernamePost
        this.totalIndividualPayment = totalIndividualPayment
        this.groupId = groupId
        this.groupName = groupName
        this.payorsDisplayNames = payorsDisplayNames
        this.posterDisplayName = posterDisplayName
    }

    // Add an empty constructor
    constructor()

    constructor(
        paymentAmount: Double,
        payorsList: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        usernamePost: String?
    )

    fun isUserInvolvedByUid(uid: String?): Boolean {
        if (uid == null || uid.isEmpty()) {
            return false
        }
        if (uid == usernamePost) {
            return true
        }
        if (payorsList != null && payorsList!!.contains(uid)) {
            return true
        }
        return false
    }

    fun isUserInvolvedByUsername(username: String?): Boolean {
        if (username == null || username.isEmpty()) {
            return false
        }
        if (username == posterDisplayName || username == usernamePost) {
            return true
        }
        if (payorsDisplayNames != null && payorsDisplayNames!!.contains(username)) {
            return true
        }
        if (payorsList != null && payorsList!!.contains(username)) {
            return true
        }
        return false
    }
}
