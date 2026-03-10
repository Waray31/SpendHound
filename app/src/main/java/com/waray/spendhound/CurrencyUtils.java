package com.waray.spendhound;

import java.util.Locale;

public class CurrencyUtils {
    public static String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.format(Locale.getDefault(), "%,d", (long) amount);
        } else {
            return String.format(Locale.getDefault(), "%,.2f", amount);
        }
    }

    public static String formatAmountWithCurrency(double amount) {
        return "₱ " + formatAmount(amount);
    }

    public static String formatAmountWithCurrency(String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            return formatAmountWithCurrency(amount);
        } catch (NumberFormatException | NullPointerException e) {
            return "₱ 0";
        }
    }
}
