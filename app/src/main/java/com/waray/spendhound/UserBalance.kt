package com.waray.spendhound

import kotlinx.serialization.Serializable

/**
 * Model class for user balance data
 */
@Serializable
data class UserBalance(
    var totalBillSpent: Double = 0.0,
    var totalBillPayment: Double = 0.0,
    var totalIndividualSpent: Double = 0.0,
    var totaldebt: Double = 0.0,
    var totalreceivable: Double = 0.0
)
