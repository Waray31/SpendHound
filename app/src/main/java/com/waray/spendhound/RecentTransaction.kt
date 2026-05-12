package com.waray.spendhound

import com.waray.spendhound.ui.multi_transaction.TransactionItemFull
import com.waray.spendhound.ui.multi_transaction.TransactionPayorTable
import com.waray.spendhound.ui.multi_transaction.TransactionSplitTable

class RecentTransaction {
    var transactionId: Long? = null
    var mostRecentDate: String? = null
    var mostRecentTransactionType: String? = null
    var mostRecentDetails: String? = null
    var mostRecentPaymentAmountStr: String? = null
    var iconResource: Int = 0
    var sortDateTime: String? = null
    var timestamp: Long = 0L
    var payorsList: MutableList<String?>? = null
    var payorUserIds: MutableList<String?>? = null
    var amountsPaidList: MutableList<Double?>? = null
    var totalIndividualPayment: Double = 0.0
    var fullDateWithYear: String? = null
    var createdBy: String? = null
    var createdByUserId: String? = null
    var monthYear: String? = null
    var day: String? = null
    var timeKey: String? = null
    var isExpanded: Boolean = false
    var isUnread: Boolean = false

    var transactionItems: List<TransactionItemFull> = emptyList()
    var transactionStatus: String = "Pending"
    var payorAmountMap: Map<Long, Double> = emptyMap()
    var itemPayorMap: Map<Long, String> = emptyMap()
    var creatorNumericId: Long? = null
    var rawPayorRows: List<TransactionPayorTable> = emptyList()
    var rawSplitRows: List<TransactionSplitTable> = emptyList()
    var groupId: Long? = null
    var isArchived: Boolean = false

    constructor()

    constructor(
        transactionId: Long?,
        mostRecentDate: String?,
        mostRecentTransactionType: String?,
        mostRecentDetails: String?,
        mostRecentPaymentAmountStr: String?,
        iconResource: Int,
        sortDateTime: String?,
        timestamp: Long,
        payorsList: MutableList<String?>?,
        payorUserIds: MutableList<String?>?,
        amountsPaidList: MutableList<Double?>?,
        totalIndividualPayment: Double,
        fullDateWithYear: String?,
        createdBy: String?,
        createdByUserId: String?,
        monthYear: String?,
        day: String?,
        timeKey: String?
    ) {
        this.transactionId = transactionId
        this.mostRecentDate = mostRecentDate
        this.mostRecentTransactionType = mostRecentTransactionType
        this.mostRecentDetails = mostRecentDetails
        this.mostRecentPaymentAmountStr = mostRecentPaymentAmountStr
        this.iconResource = iconResource
        this.sortDateTime = sortDateTime
        this.timestamp = timestamp
        this.payorsList = payorsList
        this.payorUserIds = payorUserIds
        this.amountsPaidList = amountsPaidList
        this.totalIndividualPayment = totalIndividualPayment
        this.fullDateWithYear = fullDateWithYear
        this.createdBy = createdBy
        this.createdByUserId = createdByUserId
        this.monthYear = monthYear
        this.day = day
        this.timeKey = timeKey
    }
}
