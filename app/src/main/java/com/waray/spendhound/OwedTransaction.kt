package com.waray.spendhound

class OwedTransaction {
    var date: String? = null
    var borrower: String? = null
    var borrowedAmountStr: String? = null
    var status: String? = null
    var paymentSentDate: String? = null
    var borrowId: String? = null
    var monthYear: String? = null
    var day: String? = null

    constructor()

    constructor(date: String?, borrower: String?, borrowedAmountStr: String?, status: String?) {
        this.date = date.toString()
        this.borrower = borrower
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
    }

    constructor(
        date: String?,
        borrower: String?,
        borrowedAmountStr: String?,
        status: String?,
        paymentSentDate: String?,
        borrowId: String?,
        monthYear: String?,
        day: String?
    ) {
        this.date = date.toString()
        this.borrower = borrower
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
        this.paymentSentDate = paymentSentDate
        this.borrowId = borrowId
        this.monthYear = monthYear
        this.day = day
    }
}
