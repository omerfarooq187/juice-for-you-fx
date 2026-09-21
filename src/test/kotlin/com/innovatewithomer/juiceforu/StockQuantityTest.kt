package com.innovatewithomer.juiceforu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StockQuantityTest {
    @Test
    fun `quantities accept at most two decimal places`() {
        assertEquals(6.28, StockQuantity.parse("6.28"))
        assertEquals(6.0, StockQuantity.parse("6"))
        assertEquals(0.0, StockQuantity.parse("0.00", allowZero = true))
        assertNull(StockQuantity.parse("6.32344545"))
        assertNull(StockQuantity.parse("0.00"))
        assertNull(StockQuantity.parse("-1.00"))
    }

    @Test
    fun `stock display and arithmetic stay at hundredths`() {
        assertEquals("6.28", StockQuantity.format(6.28344545))
        assertEquals(0.84, StockQuantity.multiply(0.28, 3))
        assertEquals(6.28, StockQuantity.round(6.28344545))
    }
}
