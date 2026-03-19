package com.waray.spendhound

import kotlinx.serialization.Serializable

/**
 * Model class representing a breakdown item for financial summary display.
 * Used in the breakdown dialog to show detailed transaction information.
 */
@Serializable
data class BreakdownItem(
    var category: Category? = null,
    var date: String? = null,
    var personName: String? = null,
    var amount: Double = 0.0,
    var status: String? = null,
    var description: String? = null
) {
    enum class Category {
        BALANCE,
        UNPAID,
        OWE,
        DEBT
    }
}
