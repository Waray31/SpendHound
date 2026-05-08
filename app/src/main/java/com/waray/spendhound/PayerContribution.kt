package com.waray.spendhound

data class PayerContribution(
    val payerId: String,
    val payerName: String,
    val amount: Double
)