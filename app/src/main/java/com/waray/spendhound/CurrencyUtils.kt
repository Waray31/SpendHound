package com.waray.spendhound

import java.util.Locale

object CurrencyUtils {
    fun formatAmount(amount: Double): String {
        if (amount == amount.toLong().toDouble()) {
            return String.format(Locale.getDefault(), "%,d", amount.toLong())
        } else {
            return String.format(Locale.getDefault(), "%,.2f", amount)
        }
    }

    fun formatAmountWithCurrency(amount: Double): String {
        return "₱ " + formatAmount(amount)
    }

    fun formatAmountWithCurrency(amountStr: String): String {
        try {
            val amount = amountStr.toDouble()
            return formatAmountWithCurrency(amount)
        } catch (e: NumberFormatException) {
            return "₱ 0"
        } catch (e: NullPointerException) {
            return "₱ 0"
        }
    }
}
