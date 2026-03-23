package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Transaction(
    var id: String = UUID.randomUUID().toString(),
    @SerialName("transaction_type")
    var transactionType: String? = null,
    @SerialName("payment_amount")
    var paymentAmount: Double = 0.0,
    @SerialName("multiline_str")
    var multilineStr: String? = null,
    @SerialName("payors_list")
    var payorsList: MutableList<String?>? = null,
    @SerialName("amounts_paid_list")
    var amountsPaidList: MutableList<Double?>? = null,
    @SerialName("username_post")
    var usernamePost: String? = null,
    @SerialName("total_individual_payment")
    var totalIndividualPayment: Double = 0.0,
    @SerialName("group_id")
    var groupId: Long? = null,
    @SerialName("group_name")
    var groupName: String? = null,
    @SerialName("payors_display_names")
    var payorsDisplayNames: MutableList<String?>? = null,
    @SerialName("poster_display_name")
    var posterDisplayName: String? = null,
    @SerialName("month_year")
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
}
