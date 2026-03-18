package com.waray.spendhound

/**
 * Model class representing a breakdown item for financial summary display.
 * Used in the breakdown dialog to show detailed transaction information.
 */
class BreakdownItem {
    enum class Category {
        BALANCE,
        UNPAID,
        OWE,
        DEBT
    }

    var category: Category? = null
    var date: String? = null
    var personName: String? = null
    var amount: Double = 0.0
    var status: String? = null
    var description: String? = null

    constructor()

    constructor(
        category: Category?,
        date: String?,
        personName: String?,
        amount: Double,
        status: String?
    ) {
        this.category = category
        this.date = date
        this.personName = personName
        this.amount = amount
        this.status = status
    }

    constructor(
        category: Category?,
        date: String?,
        personName: String?,
        amount: Double,
        status: String?,
        description: String?
    ) {
        this.category = category
        this.date = date
        this.personName = personName
        this.amount = amount
        this.status = status
        this.description = description
    }
}
