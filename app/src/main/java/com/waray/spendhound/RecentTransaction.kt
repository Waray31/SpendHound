package com.waray.spendhound

class RecentTransaction {
    var mostRecentDate: String?
    var mostRecentTransactionType: String?
    var mostRecentDetails: String?
    var mostRecentPaymentAmountStr: String?
    var iconResource: Int
    var sortDateTime: String? = null // For sorting by date and time (format: "yyyy-MM-dd HH:mm:ss")
    var payorsList: MutableList<String?>? = null // Display names
    var payorUids: MutableList<String?>? = null // UIDs for profile images
    var amountsPaidList: MutableList<Double?>? = null
    var totalIndividualPayment: Double = 0.0
    var fullDateWithYear: String? = null // Full date including year for details dialog
    var createdBy: String? = null // Name of the person who created the transaction
    var createdByUid: String? =
        null // UID of the person who created the transaction (for profile image)

    // Database reference keys
    var monthYear: String? = null
    var day: String? = null
    var timeKey: String? = null

    var isExpanded: Boolean = false

    constructor(
        mostRecentDate: String?,
        mostRecentTransactionType: String?,
        mostRecentDetails: String?,
        mostRecentPaymentAmountStr: String?,
        iconResource: Int
    ) {
        this.mostRecentDate = mostRecentDate
        this.mostRecentTransactionType = mostRecentTransactionType
        this.mostRecentDetails = mostRecentDetails
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr
        this.iconResource = iconResource
    }

    constructor(
        mostRecentDate: String?,
        mostRecentTransactionType: String?,
        mostRecentDetails: String?,
        mostRecentPaymentAmountStr: String?,
        iconResource: Int,
        sortDateTime: String?
    ) {
        this.mostRecentDate = mostRecentDate
        this.mostRecentTransactionType = mostRecentTransactionType
        this.mostRecentDetails = mostRecentDetails
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr
        this.iconResource = iconResource
        this.sortDateTime = sortDateTime
    }

    constructor(
        mostRecentDate: String?,
        mostRecentTransactionType: String?,
        mostRecentDetails: String?,
        mostRecentPaymentAmountStr: String?,
        iconResource: Int,
        sortDateTime: String?,
        payorsList: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        fullDateWithYear: String?,
        createdBy: String?,
        createdByUid: String?
    ) {
        this.mostRecentDate = mostRecentDate
        this.mostRecentTransactionType = mostRecentTransactionType
        this.mostRecentDetails = mostRecentDetails
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr
        this.iconResource = iconResource
        this.sortDateTime = sortDateTime
        this.payorsList = payorsList
        this.amountsPaidList = amountsPaidList
        this.fullDateWithYear = fullDateWithYear
        this.createdBy = createdBy
        this.createdByUid = createdByUid
    }

    constructor(
        mostRecentDate: String?,
        mostRecentTransactionType: String?,
        mostRecentDetails: String?,
        mostRecentPaymentAmountStr: String?,
        iconResource: Int,
        sortDateTime: String?,
        payorsList: MutableList<String?>?,
        payorUids: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        totalIndividualPayment: Double,
        fullDateWithYear: String?,
        createdBy: String?,
        createdByUid: String?,
        monthYear: String?,
        day: String?,
        timeKey: String?
    ) {
        this.mostRecentDate = mostRecentDate
        this.mostRecentTransactionType = mostRecentTransactionType
        this.mostRecentDetails = mostRecentDetails
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr
        this.iconResource = iconResource
        this.sortDateTime = sortDateTime
        this.payorsList = payorsList
        this.payorUids = payorUids
        this.amountsPaidList = amountsPaidList
        this.totalIndividualPayment = totalIndividualPayment
        this.fullDateWithYear = fullDateWithYear
        this.createdBy = createdBy
        this.createdByUid = createdByUid
        this.monthYear = monthYear
        this.day = day
        this.timeKey = timeKey
    }
}
