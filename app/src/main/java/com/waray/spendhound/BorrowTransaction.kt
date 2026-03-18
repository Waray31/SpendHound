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
}
