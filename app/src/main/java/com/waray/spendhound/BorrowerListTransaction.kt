package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
class BorrowerListTransaction {
    var date: String? = null
    var borrowee: String? = null
    var borrowedAmountStr: String? = null
    var borrowerImgUrl: String? = null // URL to the image
    var status: String? = null
    var profileImageUrl: String? = null

    // No-argument constructor
    constructor()

    constructor(date: String?, borrowee: String?, borrowedAmountStr: String?, status: String?) {
        this.date = date
        this.borrowee = borrowee
        this.borrowedAmountStr = borrowedAmountStr
        this.status = status
    }
}
