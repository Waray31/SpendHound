package com.waray.spendhound

class RecentTransaction {
    var mostRecentDate: String? = null
    var mostRecentTransactionType: String? = null
    var mostRecentDetails: String? = null
    var mostRecentPaymentAmountStr: String? = null
    var iconResource: Int = 0
    var sortDateTime: String? = null
    var payorsList: MutableList<String?>? = null
    var payorUids: MutableList<String?>? = null
    var amountsPaidList: MutableList<Double?>? = null
    var totalIndividualPayment: Double = 0.0
    var fullDateWithYear: String? = null
    var createdBy: String? = null
    var createdByUid: String? = null

    var monthYear: String? = null
    var day: String? = null
    var timeKey: String? = null

    var isExpanded: Boolean = false

    constructor()

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

    // Explicitly named getters for Java/Kotlin compatibility
    fun getMostRecentDate(): String? = mostRecentDate
    fun getMostRecentTransactionType(): String? = mostRecentTransactionType
    fun getMostRecentDetails(): String? = mostRecentDetails
    fun getMostRecentPaymentAmountStr(): String? = mostRecentPaymentAmountStr
    fun getIconResource(): Int = iconResource
    fun getSortDateTime(): String? = sortDateTime
    fun getPayorsList(): MutableList<String?>? = payorsList
    fun getPayorUids(): MutableList<String?>? = payorUids
    fun getAmountsPaidList(): MutableList<Double?>? = amountsPaidList
    fun getTotalIndividualPayment(): Double = totalIndividualPayment
    fun getFullDateWithYear(): String? = fullDateWithYear
    fun getCreatedBy(): String? = createdBy
    fun getCreatedByUid(): String? = createdByUid
    fun getMonthYear(): String? = monthYear
    fun getDay(): String? = day
    fun getTimeKey(): String? = timeKey
    fun isExpanded(): Boolean = isExpanded

    fun setExpanded(expanded: Boolean) {
        this.isExpanded = expanded
    }

    fun setAmountsPaidList(list: MutableList<Double?>?) {
        this.amountsPaidList = list
    }
}
