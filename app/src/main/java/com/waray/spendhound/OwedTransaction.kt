package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
data class OwedTransaction(
    var date: String? = null,
    var borrower: String? = null, // Renamed from borrowerUserId to match usage
    var borrowedAmountStr: String? = null,
    var status: String? = null,
    var paymentSentDate: String? = null,
    var borrowId: String? = null,
    var monthYear: String? = null,
    var day: String? = null
)
