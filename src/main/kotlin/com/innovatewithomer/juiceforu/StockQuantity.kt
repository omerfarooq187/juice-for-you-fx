package com.innovatewithomer.juiceforu

import java.math.BigDecimal
import java.math.RoundingMode

/** Inventory and recipe quantities use hundredths throughout the app. */
object StockQuantity {
    fun parse(text: String, allowZero: Boolean = false): Double? {
        val raw = text.trim()
        if (!raw.matches(Regex("\\d+(?:\\.\\d{1,2})?"))) return null
        val value = raw.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || (!allowZero && value.signum() == 0)) return null
        return value.setScale(2, RoundingMode.UNNECESSARY).toDouble()
    }

    fun round(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun multiply(value: Double, count: Int): Double =
        BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(count.toLong()))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    fun format(value: Double): String = BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP).toPlainString()
}
