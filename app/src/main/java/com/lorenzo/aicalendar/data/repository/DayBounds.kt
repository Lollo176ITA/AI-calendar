package com.lorenzo.aicalendar.data.repository

import java.time.LocalDate
import java.time.ZoneId

/**
 * UTC epoch-millis bounds `[startInclusive, endExclusive)` for [date] in [zone].
 *
 * Zone-aware: uses the zone's actual midnight transitions, so on a DST spring-forward
 * day the span is 23h (and 25h on fall-back) rather than a naive 24h.
 */
fun dayBoundsUtcMillis(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}
