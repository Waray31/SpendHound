package com.waray.spendhound

class BorrowTransaction {
    var date: String? = null
    var borrowee: String? = null // Now stores UID (was username/lender name)
    var borrowedAmountStr: String? = null
    var status: String? = null
    var borroweeDisplayName: String? = null // Display name for UI
    var paymentSentDate: String? = null
    var borrowId: String? = null
    var monthYear: String? = null
    var day: String? = null

    constructor()

    constructor(date: String?, borrowee: String?, borrowedAmountStr: String?, status: String?) {
        this.date = date.toString()
        this.borrowee = borrowee
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
    }

    // New constructor with display name
    constructor(
        date: String?,
        borrowee: String?,
        borrowedAmountStr: String?,
        status: String?,
        borroweeDisplayName: String?
    ) {
        this.date = date.toString()
        this.borrowee = borrowee
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
        this.borroweeDisplayName = borroweeDisplayName
    }

    // Getters aligned with database schema
    fun getDate(): String? = date
    fun getBorrowee(): String? = borrowee
    fun getBorrowedAmountStr(): String? = borrowedAmountStr
    fun getStatus(): String? = status
    fun getBorroweeDisplayName(): String? = borroweeDisplayName
    fun getPaymentSentDate(): String? = paymentSentDate
    fun getBorrowId(): String? = borrowId
    fun getMonthYear(): String? = monthYear
    fun getDay(): String? = day

    // Setters aligned with database schema
    fun setDate(date: String?) {
        this.date = date?.toString()
    }
    fun setBorrowee(borrowee: String?) {
        this.borrowee = borrowee
    }
    fun setBorrowedAmountStr(amount: String?) {
        this.borrowedAmountStr = amount
    }
    fun setStatus(status: String?) {
        this.status = status
    }
    fun setBorroweeDisplayName(name: String?) {
        this.borroweeDisplayName = name
    }
    fun setPaymentSentDate(date: String?) {
        this.paymentSentDate = date
    }
    fun setBorrowId(id: String?) {
        this.borrowId = id
    }
    fun setMonthYear(monthYear: String?) {
        this.monthYear = monthYear
    }
    fun setDay(day: String?) {
        this.day = day
    }
}
