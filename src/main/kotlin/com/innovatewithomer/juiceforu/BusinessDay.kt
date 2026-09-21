package com.innovatewithomer.juiceforu

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A business day starts at the configured local time, not necessarily at midnight. */
object BusinessDay {
    data class Range(val startInclusive: Long, val endExclusive: Long)

    fun dateAt(now: ZonedDateTime, cutoff: LocalTime): LocalDate =
        now.toLocalDate().let { if (now.toLocalTime() < cutoff) it.minusDays(1) else it }

    fun range(startDate: LocalDate, endDateExclusive: LocalDate, cutoff: LocalTime, zone: ZoneId): Range =
        Range(
            startDate.atTime(cutoff).atZone(zone).toInstant().toEpochMilli(),
            endDateExclusive.atTime(cutoff).atZone(zone).toInstant().toEpochMilli()
        )

    fun currentDate(): LocalDate {
        val now = ZonedDateTime.now()
        return dateAt(now, AppSettings.businessDayStart)
    }

    fun today(): Range = currentRange { date -> date to date.plusDays(1) }

    fun lastDays(days: Int): Range {
        require(days > 0)
        return currentRange { date -> date.minusDays((days - 1).toLong()) to date.plusDays(1) }
    }

    fun thisMonth(): Range = currentRange { date ->
        date.withDayOfMonth(1) to date.withDayOfMonth(1).plusMonths(1)
    }

    private fun currentRange(dates: (LocalDate) -> Pair<LocalDate, LocalDate>): Range {
        val now = ZonedDateTime.now()
        val cutoff = AppSettings.businessDayStart
        val (start, end) = dates(dateAt(now, cutoff))
        return range(start, end, cutoff, now.zone)
    }
}
