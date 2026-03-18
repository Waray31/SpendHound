package com.waray.spendhound

class BorrowNowTransaction {
    var borrowId: String? = null
    var borrowerID: String? = null
    var lenderID: String? = null
    var borrowerName: String? = null
    var date: Long? = null // changed from String? to Long?
    var lender: String? = null
    var borrowedAmount: Double? = null // changed from borrowedAmountStr: String?
    var status: String? = null
    var timestamp: Long = 0
    var paymentSentDate: Long = 0
    var month_year: String? = null

    constructor()

    constructor(
        borrowId: String?,
        borrowerID: String?,
        lenderID: String?,
        borrowerName: String?,
        date: Long?,
        lender: String?,
        borrowedAmount: Double?,
        status: String?,
        timestamp: Long
    ) {
        this.borrowId = borrowId
        this.borrowerID = borrowerID
        this.lenderID = lenderID
        this.borrowerName = borrowerName
        this.date = date
        this.lender = lender
        this.borrowedAmount = borrowedAmount
        this.status = status
        this.timestamp = timestamp
    }

    // Legacy constructor for backwards compatibility
    constructor(
        borrowerID: String?,
        lenderID: String?,
        date: Long?,
        borrowee: String?,
        borrowedAmount: Double?,
        status: String?
    ) {
        this.borrowerID = borrowerID
        this.lenderID = lenderID
        this.date = date
        this.lender = borrowee
        this.borrowedAmount = borrowedAmount
        this.status = status
        this.timestamp = System.currentTimeMillis()
    }

    // Getters aligned with database schema
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
    fun getMonthYear(): String? = month_year

    // Setters aligned with database schema
    fun setBorrowId(id: String?) {
        this.borrowId = id
    }
    fun setBorrowerID(id: String?) {
        this.borrowerID = id
    }
    fun setLenderID(id: String?) {
        this.lenderID = id
    }
    fun setBorrowerName(name: String?) {
        this.borrowerName = name
    }
    fun setDate(date: Long?) {
        this.date = date
    }
    fun setLender(lender: String?) {
        this.lender = lender
    }
    fun setBorrowedAmount(amount: Double?) {
        this.borrowedAmount = amount
    }
    fun setStatus(status: String?) {
        this.status = status
    }
    fun setTimestamp(timestamp: Long) {
        this.timestamp = timestamp
    }
    fun setPaymentSentDate(date: Long) {
        this.paymentSentDate = date
    }
    fun setMonthYear(monthYear: String?) {
        this.month_year = monthYear
    }
}
