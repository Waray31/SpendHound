package com.waray.spendhound.ui.multi_transaction

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionEntry(
    var title: String = "",
    var amount: Double = 0.0,
    var category: String = "General",
    var payors: MutableList<PayorEntry> = mutableListOf()
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class PayorEntry(
    val userId: Long,
    val username: String,
    var amount: Double = 0.0
)

/**
 * Insertion model for 'transactions' table.
 * IMPORTANT: Omit 'id' field entirely for insertion to let Postgres generate it via the sequence.
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionInsert(
    @SerialName("total_amount")
    val paymentAmount: Double = 0.0,
    
    @SerialName("title")
    val transactionType: String? = null,
    
    @SerialName("description")
    val transactionDetail: String? = null,
    
    @SerialName("group_id")
    val groupId: Long? = null,
    
    @SerialName("created_by")
    val creatorId: Long? = null,
    
    @SerialName("created_at")
    val createdAt: String? = null,
    
    @SerialName("status")
    val status: Int? = null
)

/**
 * Retrieval model for 'transactions' table (includes ID).
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionResponse(
    @SerialName("id")
    val id: Long? = null,
    
    @SerialName("payment_amount")
    val paymentAmount: Double = 0.0
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionPayorTable(
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("user_id") val userId: Long,
    val amount: Double
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionSplitTable(
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("user_id") val userId: Long,
    val amount: Double
)
