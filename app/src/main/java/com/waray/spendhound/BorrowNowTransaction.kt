package com.waray.spendhound

class BorrowNowTransaction {
    var borrowId: String? = null
    var borrowerID: String? = null
    var lenderID: String? = null
    var borrowerName: String? = null
    var date: String? = null
    var lender: String? = null
    var borrowedAmountStr: String? = null
    var status: String? = null
    var timestamp: Long = 0
    var paymentSentDate: Long = 0

    constructor()

    constructor(
        borrowId: String?,
        borrowerID: String?,
        lenderID: String?,
        borrowerName: String?,
        date: String?,
        lender: String?,
        borrowedAmountStr: String?,
        status: String?,
        timestamp: Long
    ) {
        this.borrowId = borrowId
        this.borrowerID = borrowerID
        this.lenderID = lenderID
        this.borrowerName = borrowerName
        this.date = date.toString()
        this.lender = lender
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
        this.timestamp = timestamp
    }

    // Legacy constructor for backwards compatibility
    constructor(
        borrowerID: String?,
        lenderID: String?,
        date: String?,
        borrowee: String?,
        borrowedAmountStr: String?,
        status: String?
    ) {
        this.borrowerID = borrowerID
        this.lenderID = lenderID
        this.date = date.toString()
        this.lender = borrowee
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
        this.timestamp = System.currentTimeMillis()
    }

    // Getters aligned with database schema
    fun getBorrowId(): String? = borrowId
    fun getBorrowerID(): String? = borrowerID
    fun getLenderID(): String? = lenderID
    fun getBorrowerName(): String? = borrowerName
    fun getDate(): String? = date
    fun getLender(): String? = lender
    fun getBorrowedAmountStr(): String? = borrowedAmountStr
    fun getStatus(): String? = status
    fun getTimestamp(): Long = timestamp
    fun getPaymentSentDate(): Long = paymentSentDate

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
    fun setDate(date: String?) {
        this.date = date?.toString()
    }
    fun setLender(lender: String?) {
        this.lender = lender
    }
    fun setBorrowedAmountStr(amount: String?) {
        this.borrowedAmountStr = amount
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
}
