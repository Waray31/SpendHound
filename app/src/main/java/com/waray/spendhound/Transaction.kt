package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Transaction(
    @SerialName("id")
    var id: Long? = null,
    
    @SerialName("payment_amount")
    var paymentAmount: Double = 0.0,
    
    @SerialName("transaction_type")
    var transactionType: String? = null,
    
    @SerialName("transaction_detail")
    var transactionDetail: String? = null,
    
    @SerialName("group_id")
    var groupId: Long? = null,
    
    @SerialName("amount_paid_list")
    var amountPaidList: List<Double>? = null,
    
    @SerialName("contributors")
    var contributors: List<String>? = null, // Changed to List<String> to store user_id (UUID string)
    
    @SerialName("individual_payment")
    var individualPayment: Double = 0.0,
    
    @SerialName("creator_id")
    var creatorId: Long? = null,
    
    @SerialName("created_at")
    var createdAt: String? = null,
    
    @SerialName("status")
    var status: Int? = null,
    
    // Additional transient fields used in UI
    @Transient var day: String? = null,
    @Transient var monthYear: String? = null,
    @Transient var timeKey: String? = null,
    @Transient var timestamp: Long = 0L,
    @Transient var posterDisplayName: String? = null,
    @Transient var usernamePost: String? = null,
    @Transient var payorsDisplayNames: List<String>? = null,
    @Transient var groupName: String? = null
) {
    // Aliases for legacy property names if needed
    val payment_amount get() = paymentAmount
    val transaction_type get() = transactionType
    val transaction_detail get() = transactionDetail
    val group_id get() = groupId
    val amount_paid_list get() = amountPaidList
    val individual_payment get() = individualPayment
    val creator_id get() = creatorId
    val created_at get() = createdAt

    fun isUserInvolvedByUserId(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return contributors?.contains(userId) == true || usernamePost == userId
    }

    fun isUserInvolvedByUsername(username: String?): Boolean {
        if (username.isNullOrEmpty()) return false
        return payorsDisplayNames?.contains(username) == true || posterDisplayName == username
    }
}
