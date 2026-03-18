package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
data class OwedTransaction(
    var date: String? = null,
    var borrower: String? = null,
    var borrowedAmountStr: String? = null,
    var status: String? = null,
    var paymentSentDate: String? = null,
    var borrowId: String? = null,
    var monthYear: String? = null,
    var day: String? = null
) {
    // Getters for compatibility
    fun getDate(): String? = date
    fun getBorrower(): String? = borrower
    fun getBorrowedAmountStr(): String? = borrowedAmountStr
    fun getStatus(): String? = status
    fun getPaymentSentDate(): String? = paymentSentDate
    fun getBorrowId(): String? = borrowId
    fun getMonthYear(): String? = monthYear
    fun getDay(): String? = day

    // Setters for compatibility
    fun setDate(date: String?) { this.date = date }
    fun setBorrower(borrower: String?) { this.borrower = borrower }
    fun setBorrowedAmountStr(amount: String?) { this.borrowedAmountStr = amount }
    fun setStatus(status: String?) { this.status = status }
    fun setPaymentSentDate(date: String?) { this.paymentSentDate = date }
    fun setBorrowId(id: String?) { this.borrowId = id }
    fun setMonthYear(monthYear: String?) { this.monthYear = monthYear }
    fun setDay(day: String?) { this.day = day }
}
