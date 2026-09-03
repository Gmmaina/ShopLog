package com.example.shoplog.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Utility object for safe monetary calculations stored as integer cents (minor currency units)
 * to prevent floating point rounding inaccuracies.
 */
object Money {

    /**
     * Converts a String input (e.g. "75.50" or "75") to long cents.
     * Returns 0L if parsing fails.
     */
    fun parseToCents(amountStr: String): Long {
        if (amountStr.isBlank()) return 0L
        val clean = amountStr.replace(",", "").trim()
        val parts = clean.split(".")
        return when (parts.size) {
            1 -> {
                val integerPart = parts[0].toLongOrNull() ?: 0L
                integerPart * 100L
            }
            2 -> {
                val integerPart = parts[0].toLongOrNull() ?: 0L
                val decimalsRaw = parts[1]
                val decimalsFormatted = when {
                    decimalsRaw.length == 1 -> decimalsRaw + "0"
                    decimalsRaw.length >= 2 -> decimalsRaw.substring(0, 2)
                    else -> "00"
                }
                val decimalPart = decimalsFormatted.toLongOrNull() ?: 0L
                (integerPart * 100L) + decimalPart
            }
            else -> 0L
        }
    }

    /**
     * Converts cents to major units string for UI input fields (e.g., 7550L -> "75.50", 7500L -> "75.00").
     */
    fun centsToInputValue(cents: Long): String {
        val integerPart = cents / 100
        val decimalPart = cents % 100
        return if (decimalPart == 0L) {
            integerPart.toString()
        } else {
            String.format(Locale.US, "%d.%02d", integerPart, decimalPart)
        }
    }

    /**
     * Calculates the subtotal for a given item quantity and unit price in cents.
     */
    fun calculateSubtotal(quantity: Int, unitPriceCents: Long): Long {
        if (quantity <= 0 || unitPriceCents <= 0L) return 0L
        return quantity.toLong() * unitPriceCents
    }

    /**
     * Formats cents into a human-readable currency string (e.g., 115000L with "KSh" -> "KSh 1,150.00").
     */
    fun format(cents: Long, currencySymbol: String = "KSh"): String {
        val majorValue = cents / 100.0
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val formattedNumber = formatter.format(majorValue)
        val symbolPrefix = if (currencySymbol.isBlank()) "" else "$currencySymbol "
        return "$symbolPrefix$formattedNumber"
    }
}
