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
) {
    // Getters
    fun getTotalBillSpent(): Double = totalBillSpent
    fun getTotalBillPayment(): Double = totalBillPayment
    fun getTotalIndividualSpent(): Double = totalIndividualSpent
    fun getTotaldebt(): Double = totaldebt
    fun getTotalreceivable(): Double = totalreceivable

    // Setters
    fun setTotalBillSpent(amount: Double) {
        this.totalBillSpent = amount
    }
    fun setTotalBillPayment(amount: Double) {
        this.totalBillPayment = amount
    }
    fun setTotalIndividualSpent(amount: Double) {
        this.totalIndividualSpent = amount
    }
    fun setTotaldebt(amount: Double) {
        this.totaldebt = amount
    }
    fun setTotalreceivable(amount: Double) {
        this.totalreceivable = amount
    }
}
