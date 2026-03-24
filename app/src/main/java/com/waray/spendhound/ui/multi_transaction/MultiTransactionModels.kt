package com.waray.spendhound.ui.multi_transaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionEntry(
    var title: String = "",
    var amount: Double = 0.0,
    var category: String = "General",
    var payors: MutableList<PayorEntry> = mutableListOf()
)

@Serializable
data class PayorEntry(
    val userId: Long,
    val username: String,
    var amount: Double = 0.0
)

@Serializable
data class TransactionTable(
    val id: Long? = null,
    @SerialName("group_id") val groupId: Long,
    val title: String,
    val description: String? = null,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("created_by") val createdBy: Long,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TransactionPayorTable(
    val id: Long? = null,
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("user_id") val userId: Long,
    val amount: Double
)

@Serializable
data class TransactionSplitTable(
    val id: Long? = null,
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("user_id") val userId: Long,
    val amount: Double
)
