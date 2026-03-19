package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model for the 'borrows' table, exactly aligned with the Supabase schema.
 * Database Types:
 * id: int8 (Primary Key, Auto-increment)
 * created_at: timestamptz
 * borrowed_amount: float8
 * borrower_id: int8 (FK to users.id)
 * lender_id: int8 (FK to users.id)
 * payment_sent_date: timestamptz
 * status: int2
 */
@Serializable
class BorrowNowTransaction(
    @SerialName("id")
    var id: Long? = null,
    
    @SerialName("created_at")
    var createdAt: String? = null,
    
    @SerialName("borrowed_amount")
    var borrowedAmount: Double? = null,
    
    @SerialName("borrower_id")
    var borrowerId: Long? = null,
    
    @SerialName("lender_id")
    var lenderId: Long? = null,
    
    @SerialName("payment_sent_date")
    var paymentSentDate: String? = null,
    
    @SerialName("status")
    var statusInt: Int? = null,

    // App-specific transient fields for UI and legacy logic
    @kotlinx.serialization.Transient
    var borrowerName: String? = null,
    @kotlinx.serialization.Transient
    var lender: String? = null,
    @kotlinx.serialization.Transient
    var monthYear: String? = null,
    @kotlinx.serialization.Transient
    var timestamp: Long = System.currentTimeMillis(),
    @kotlinx.serialization.Transient
    private var statusStr: String? = null
) {
    constructor() : this(null)

    // Legacy constructor compatibility
    constructor(
        borrowId: String?,
        borrowerID: String?,
        borrowerName: String?,
        lenderID: String?,
        lender: String?,
        borrowedAmount: Double?,
        status: String?,
        date: Long?,
        monthYear: String?,
        timestamp: Long
    ) : this() {
        this.id = borrowId?.toLongOrNull()
        this.borrowerId = borrowerID?.toLongOrNull()
        this.lenderId = lenderID?.toLongOrNull()
        this.borrowerName = borrowerName
        this.lender = lender
        this.borrowedAmount = borrowedAmount
        this.setStatus(status)
        this.monthYear = monthYear
        this.timestamp = timestamp
        if (date != null && date > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            this.createdAt = sdf.format(Date(date))
        }
    }

    // Compatibility methods for existing app logic
    fun getBorrowId(): String? = id?.toString()
    fun getBorrowerID(): String? = borrowerId?.toString()
    fun getLenderID(): String? = lenderId?.toString()
    
    // borrowedAmount, borrowerName, lender, monthYear, timestamp 
    // are already accessible via generated accessors in Kotlin.
    // If Java calls them, it uses getBorrowedAmount(), etc.

    fun getDate(): Long {
        if (createdAt == null) return timestamp
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            sdf.parse(createdAt!!)?.time ?: timestamp
        } catch (e: Exception) { timestamp }
    }
    
    fun getStatus(): String? {
        if (statusStr != null) return statusStr
        return when (statusInt) {
            1 -> "For Lender Approval"
            2 -> "Pending Payment"
            3 -> "Paid"
            4 -> "Declined"
            5 -> "Payment Denied"
            6 -> "Removed"
            7 -> "Paid Partially"
            else -> "Unpaid"
        }
    }
    
    fun getPaymentSentDate(): Long {
        if (paymentSentDate == null) return 0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            sdf.parse(paymentSentDate!!)?.time ?: 0
        } catch (e: Exception) { 0 }
    }

    fun setBorrowId(id: String?) { this.id = id?.toLongOrNull() }
    fun setBorrowerID(id: String?) { this.borrowerId = id?.toLongOrNull() }
    fun setLenderID(id: String?) { this.lenderId = id?.toLongOrNull() }
    
    fun setStatus(statusStr: String?) {
        this.statusStr = statusStr
        this.statusInt = when (statusStr) {
            "For Lender Approval" -> 1
            "Pending Payment" -> 2
            "Paid" -> 3
            "Declined" -> 4
            "Payment Denied" -> 5
            "Removed" -> 6
            "Paid Partially" -> 7
            else -> 0
        }
    }
    
    fun setPaymentSentDate(millis: Long) {
        if (millis <= 0L) {
            this.paymentSentDate = null
            return
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        this.paymentSentDate = sdf.format(Date(millis))
    }
}
