package com.waray.spendhound

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Transaction(
    var id: String = UUID.randomUUID().toString(),
    var transactionType: String? = null,
    var paymentAmount: Double = 0.0,
    var multilineStr: String? = null,
    var payorsList: MutableList<String?>? = null,
    var amountsPaidList: MutableList<Double?>? = null,
    var usernamePost: String? = null,
    var totalIndividualPayment: Double = 0.0,
    var groupId: String? = null,
    var groupName: String? = null,
    var payorsDisplayNames: MutableList<String?>? = null,
    var posterDisplayName: String? = null,
    var monthYear: String? = null,
    var day: String? = null,
    var timeKey: String? = null,
    var timestamp: Long = System.currentTimeMillis()
) {
    fun isUserInvolvedByUid(uid: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        if (uid == usernamePost) return true
        if (payorsList?.contains(uid) == true) return true
        return false
    }

    fun isUserInvolvedByUsername(username: String?): Boolean {
        if (username.isNullOrEmpty()) return false
        if (username == posterDisplayName || username == usernamePost) return true
        if (payorsDisplayNames?.contains(username) == true) return true
        if (payorsList?.contains(username) == true) return true
        return false
    }

    // Getters for compatibility
    fun getTransactionType(): String? = transactionType
    fun getPaymentAmount(): Double = paymentAmount
    fun getMultilineStr(): String? = multilineStr
    fun getPayorsList(): MutableList<String?>? = payorsList
    fun getAmountsPaidList(): MutableList<Double?>? = amountsPaidList
    fun getUsernamePost(): String? = usernamePost
    fun getTotalIndividualPayment(): Double = totalIndividualPayment
    fun getGroupId(): String? = groupId
    fun getGroupName(): String? = groupName
    fun getPayorsDisplayNames(): MutableList<String?>? = payorsDisplayNames
    fun getPosterDisplayName(): String? = posterDisplayName
    fun getMonthYear(): String? = monthYear
    fun getDay(): String? = day
    fun getTimeKey(): String? = timeKey
    fun getTimestamp(): Long = timestamp
    fun getId(): String = id
}
