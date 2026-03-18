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

    // Getters
    fun getDate(): String? = date
    fun getBorrowee(): String? = borrowee
    fun getBorrowedAmountStr(): String? = borrowedAmountStr
    fun getBorrowerImgUrl(): String? = borrowerImgUrl
    fun getStatus(): String? = status
    fun getProfileImageUrl(): String? = profileImageUrl

    // Setters
    fun setDate(date: String?) {
        this.date = date
    }
    fun setBorrowee(borrowee: String?) {
        this.borrowee = borrowee
    }
    fun setBorrowedAmountStr(amount: String?) {
        this.borrowedAmountStr = amount
    }
    fun setBorrowerImgUrl(url: String?) {
        this.borrowerImgUrl = url
    }
    fun setStatus(status: String?) {
        this.status = status
    }
    fun setProfileImageUrl(url: String?) {
        this.profileImageUrl = url
    }
}
