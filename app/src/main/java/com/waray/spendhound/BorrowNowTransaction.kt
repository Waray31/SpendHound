package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
class BorrowNowTransaction(
    var borrowId: String? = null,
    var borrowerID: String? = null,
    var lenderID: String? = null,
    var borrowerName: String? = null,
    var date: Long? = null,
    var lender: String? = null,
    var borrowedAmount: Double? = null,
    var status: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    var paymentSentDate: Long = 0,
    var monthYear: String? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null, System.currentTimeMillis(), 0, null)

    // Getters for compatibility
    fun getBorrowId(): String? = borrowId
    fun getBorrowerID(): String? = borrowerID
    fun getLenderID(): String? = lenderID
    fun getBorrowerName(): String? = borrowerName
    fun getDate(): Long? = date
    fun getLender(): String? = lender
    fun getBorrowedAmount(): Double? = borrowedAmount
    fun getStatus(): String? = status
    fun getTimestamp(): Long = timestamp
    fun getPaymentSentDate(): Long = paymentSentDate
    fun getMonthYear(): String? = monthYear

    // Setters for compatibility
    fun setBorrowId(id: String?) { this.borrowId = id }
    fun setBorrowerID(id: String?) { this.borrowerID = id }
    fun setLenderID(id: String?) { this.lenderID = id }
    fun setBorrowerName(name: String?) { this.borrowerName = name }
    fun setDate(date: Long?) { this.date = date }
    fun setLender(lender: String?) { this.lender = lender }
    fun setBorrowedAmount(amount: Double?) { this.borrowedAmount = amount }
    fun setStatus(status: String?) { this.status = status }
    fun setTimestamp(timestamp: Long) { this.timestamp = timestamp }
    fun setPaymentSentDate(date: Long) { this.paymentSentDate = date }
    fun setMonthYear(monthYear: String?) { this.monthYear = monthYear }
}
