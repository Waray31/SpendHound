package com.waray.spendhound

data class MultiTransactionItem(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val payers: List<PayerContribution> = emptyList(),
    val includedMembers: List<String> = emptyList(), // List of member IDs
    val isValid: Boolean = false
) {
    fun getTotalPaid(): Double = payers.sumOf { it.amount }
    
    fun isPaymentComplete(): Boolean = getTotalPaid() == amount
    
    fun getPaymentSummary(): String {
        return when {
            payers.isEmpty() -> "Tap to select payer"
            payers.size == 1 -> payers.first().payerName
            else -> "${payers.first().payerName} + ${payers.size - 1} more"
        }
    }
    
    fun getParticipantSummary(totalMembers: Int): String {
        return when {
            includedMembers.size == totalMembers -> "All members"
            else -> "${includedMembers.size}/$totalMembers members"
        }
    }
}