package com.innovatewithomer.juiceforu

import com.innovatewithomer.juiceforu.models.Order
import com.innovatewithomer.juiceforu.utils.ReceiptPrinter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BusinessDayTest {
    @Test
    fun `one AM cutoff keeps after-midnight orders on previous business date`() {
        val zone = ZoneId.of("Asia/Karachi")
        val cutoff = LocalTime.of(1, 0)
        assertEquals(LocalDate.of(2026, 9, 15), BusinessDay.dateAt(ZonedDateTime.of(2026, 9, 16, 0, 59, 59, 0, zone), cutoff))
        assertEquals(LocalDate.of(2026, 9, 16), BusinessDay.dateAt(ZonedDateTime.of(2026, 9, 16, 1, 0, 0, 0, zone), cutoff))

        val range = BusinessDay.range(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16), cutoff, zone)
        assertEquals(ZonedDateTime.of(2026, 9, 15, 1, 0, 0, 0, zone).toInstant().toEpochMilli(), range.startInclusive)
        assertEquals(ZonedDateTime.of(2026, 9, 16, 1, 0, 0, 0, zone).toInstant().toEpochMilli(), range.endExclusive)
    }

    @Test
    fun `range uses local boundaries through daylight saving changes`() {
        val zone = ZoneId.of("Europe/Berlin")
        val range = BusinessDay.range(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30), LocalTime.of(1, 0), zone)
        assertEquals(23 * 60 * 60 * 1000L, range.endExclusive - range.startInclusive)
    }

    @Test
    fun `customer receipt attribution is the final text before paper cut`() {
        val bytes = ReceiptPrinter().buildReceiptContent(Order(orderNo = 201, total = 0, orderType = "Takeaway"))
        val footer = "Developed by InnovateWithOmer\ninnovatewithomer.dev\n\n".toByteArray(Charsets.US_ASCII)
        val cut = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
        assertTrue(bytes.copyOfRange(bytes.size - cut.size, bytes.size).contentEquals(cut))
        assertTrue(bytes.copyOfRange(bytes.size - cut.size - footer.size, bytes.size - cut.size).contentEquals(footer))
    }
}
