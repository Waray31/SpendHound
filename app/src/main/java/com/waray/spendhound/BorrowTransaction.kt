package com.waray.spendhound

import kotlinx.serialization.Serializable

@Serializable
data class BorrowTransaction(
    var date: String? = null,
    var borrowee: String? = null, // Renamed from borroweeUserId to match usage
    var borrowedAmountStr: String? = null,
    var status: String? = null,
    var borroweeDisplayName: String? = null, // Display name for UI
    var paymentSentDate: String? = null,
    var borrowId: String? = null,
    var monthYear: String? = null,
    var day: String? = null
)
