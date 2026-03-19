package com.waray.spendhound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model class for the 'user_balance' table in Supabase.
 * Database Types:
 * user_id: int8 (Primary Key)
 * created_at: timestamptz
 * unpaid_total_group: numeric
 * unpaid_total_individual: numeric
 * receivable_total_group: numeric
 * receivable_total_individual: numeric
 * balance_total_group: numeric
 * balance_total_individual: numeric
 */
@Serializable
data class UserBalance(
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("unpaid_total_group")
    var unpaidTotalGroup: Double = 0.0,
    @SerialName("unpaid_total_individual")
    var unpaidTotalIndividual: Double = 0.0,
    @SerialName("receivable_total_group")
    var receivableTotalGroup: Double = 0.0,
    @SerialName("receivable_total_individual")
    var receivableTotalIndividual: Double = 0.0,
    @SerialName("balance_total_group")
    var balanceTotalGroup: Double = 0.0,
    @SerialName("balance_total_individual")
    var balanceTotalIndividual: Double = 0.0
) {
    // Legacy compatibility getters/setters or mapping can be added if needed,
    // but aligning with the new schema is better for direct Supabase interaction.

    fun getUserId(): String? = userId?.toString()
    
    // Compatibility helpers for BalanceHelper if it still uses old names
    fun getTotalBillSpent(): Double = unpaidTotalGroup
    fun getTotalBillPayment(): Double = balanceTotalGroup // Assuming this mapping
    fun getTotalIndividualSpent(): Double = unpaidTotalIndividual
    fun getTotaldebt(): Double = unpaidTotalGroup + unpaidTotalIndividual
    fun getTotalreceivable(): Double = receivableTotalGroup + receivableTotalIndividual
}
