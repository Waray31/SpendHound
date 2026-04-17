package com.waray.spendhound.ui.multi_transaction

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── UI / local models ────────────────────────────────────────────────────────

@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionEntry(
    var title: String = "",           // maps to transaction_items.item_description
    var amount: Double = 0.0,
    var category: String = "",        // maps to transaction_items.category
    var payors: MutableList<PayorEntry> = mutableListOf()
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class PayorEntry(
    val userId: Long,
    val username: String,
    var amount: Double = 0.0
)

// ─── transactions table ───────────────────────────────────────────────────────

/** INSERT — no id, no title column (removed from schema) */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionInsert(
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("description")  val description: String? = null,
    @SerialName("group_id")     val groupId: Long? = null,
    @SerialName("created_by")   val createdBy: Long? = null,
    @SerialName("created_at")   val createdAt: String? = null,
    @SerialName("status")       val status: Int? = null
)

/** SELECT — includes generated id */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionFull(
    @SerialName("id")           val id: Long? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("description")  val description: String? = null,
    @SerialName("group_id")     val groupId: Long? = null,
    @SerialName("created_by")   val createdBy: Long? = null,
    @SerialName("created_at")   val createdAt: String? = null,
    @SerialName("status")       val status: Int? = null
)

/** Minimal response after insert — only need the id back */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionResponse(
    @SerialName("id") val id: Long? = null
)

// ─── transaction_items table ──────────────────────────────────────────────────

/** INSERT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionItemInsert(
    @SerialName("transaction_id")   val transactionId: Long,
    @SerialName("amount")           val amount: Double,
    @SerialName("category")         val category: String,
    @SerialName("item_description") val itemDescription: String? = null,
    @SerialName("created_at")       val createdAt: String? = null
)

/** SELECT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionItemFull(
    @SerialName("id")               val id: Long? = null,
    @SerialName("transaction_id")   val transactionId: Long = 0,
    @SerialName("amount")           val amount: Double = 0.0,
    @SerialName("category")         val category: String? = null,
    @SerialName("item_description") val itemDescription: String? = null,
    @SerialName("created_at")       val createdAt: String? = null
)

// ─── transaction_payors table ─────────────────────────────────────────────────

/** INSERT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionPayorInsert(
    @SerialName("transaction_id")         val transactionId: Long,
    @SerialName("user_id")                val userId: Long,
    @SerialName("initial_amount_paid")    val initialAmountPaid: Double,
    @SerialName("current_amount_paid")    val currentAmountPaid: Double,
    @SerialName("excess_amount")          val excessAmount: Double,
    @SerialName("transaction_items_id")   val transactionItemsId: Long?,
    @SerialName("status")                 val status: Int,
    @SerialName("paid_to")                val paidTo: Long? = null
)

/** SELECT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionPayorTable(
    @SerialName("id")                     val id: Long? = null,
    @SerialName("transaction_id")         val transactionId: Long = 0,
    @SerialName("user_id")                val userId: Long = 0,
    @SerialName("initial_amount_paid")    val initialAmountPaid: Double = 0.0,
    @SerialName("current_amount_paid")    val currentAmountPaid: Double = 0.0,
    @SerialName("excess_amount")          val excessAmount: Double = 0.0,
    @SerialName("created_at")             val createdAt: String? = null,
    @SerialName("transaction_items_id")   val transactionItemsId: Long? = null,
    @SerialName("status")                 val status: Int = 0,
    @SerialName("paid_to")                val paidTo: Long? = null
)

/** UPDATE - for editing payment amounts */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionPayorUpdate(
    @SerialName("current_amount_paid")    val currentAmountPaid: Double,
    @SerialName("excess_amount")          val excessAmount: Double,
    @SerialName("status")                 val status: Int,
    @SerialName("paid_to")                val paidTo: Long? = null
)

/** UPDATE - for excess and status only */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionPayorPartialUpdate(
    @SerialName("excess_amount")          val excessAmount: Double,
    @SerialName("status")                 val status: Int
)

// ─── transaction_splits table ─────────────────────────────────────────────────

/** INSERT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionSplitInsert(
    @SerialName("transaction_id")       val transactionId: Long,
    @SerialName("user_id")              val userId: Long,
    @SerialName("amount")               val amount: Double,
    @SerialName("transaction_items_id") val transactionItemsId: Long
)

/** SELECT */
@OptIn(InternalSerializationApi::class)
@Serializable
data class TransactionSplitTable(
    @SerialName("id")                   val id: Long? = null,
    @SerialName("transaction_id")       val transactionId: Long = 0,
    @SerialName("user_id")              val userId: Long = 0,
    @SerialName("amount")               val amount: Double = 0.0,
    @SerialName("created_at")           val createdAt: String? = null,
    @SerialName("transaction_items_id") val transactionItemsId: Long? = null
)
