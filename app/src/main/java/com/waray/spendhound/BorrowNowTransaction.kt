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
}
