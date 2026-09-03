package com.example.shoplog

import com.example.shoplog.core.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun parseToCents_correctlyParsesFormattedInputs() {
        assertEquals(7550L, Money.parseToCents("75.50"))
        assertEquals(7500L, Money.parseToCents("75"))
        assertEquals(6500L, Money.parseToCents("65.0"))
        assertEquals(18000L, Money.parseToCents("180.00"))
        assertEquals(42525L, Money.parseToCents("425.25"))
        assertEquals(0L, Money.parseToCents(""))
        assertEquals(0L, Money.parseToCents("invalid"))
    }

    @Test
    fun centsToInputValue_correctlyFormatsCentsForInput() {
        assertEquals("75.50", Money.centsToInputValue(7550L))
        assertEquals("75", Money.centsToInputValue(7500L))
        assertEquals("180", Money.centsToInputValue(18000L))
        assertEquals("0", Money.centsToInputValue(0L))
    }

    @Test
    fun calculateSubtotal_multipliesQuantityAndUnitPriceExact() {
        assertEquals(15000L, Money.calculateSubtotal(2, 7500L)) // 2 * 75.00 = 150.00
        assertEquals(6500L, Money.calculateSubtotal(1, 6500L))  // 1 * 65.00 = 65.00
        assertEquals(36000L, Money.calculateSubtotal(2, 18000L)) // 2 * 180.00 = 360.00
        assertEquals(0L, Money.calculateSubtotal(0, 1000L))
        assertEquals(0L, Money.calculateSubtotal(5, -100L))
    }

    @Test
    fun format_formatsCentsWithCurrencySymbol() {
        assertEquals("KSh 1,150.00", Money.format(115000L, "KSh"))
        assertEquals("$ 75.50", Money.format(7550L, "$"))
        assertEquals("€ 0.00", Money.format(0L, "€"))
    }
}
