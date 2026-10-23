package dev.reminderos.preview

import dev.reminderos.core.CalendarDate
import dev.reminderos.core.LocalMinute
import dev.reminderos.core.PortableCadence
import dev.reminderos.core.PortableRecurrenceSpec
import dev.reminderos.core.TimeZonePolicy
import dev.reminderos.core.WallClock
import dev.reminderos.core.portableOccurrenceKeys
import java.util.Calendar
import java.util.TimeZone

/**
 * Android absolute-time adapter for the shared KMP recurrence contract.
 *
 * The calendar date and weekday selection now execute Kotlin Multiplatform
 * source at runtime. Android java.util.Calendar only resolves the selected
 * local wall-clock date into an epoch timestamp (DST / system time zone).
 */
internal object RecurrenceSchedule {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    private fun date(c: Calendar): CalendarDate = CalendarDate(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )

    fun isoWeekday(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun bitForDay(c: Calendar): Int = 1 shl isoWeekday(c)

    fun next(
        anchorMs: Long,
        afterExclusiveMs: Long,
        mode: String,
        daysMask: Int = 0,
        zone: TimeZone = TimeZone.getDefault()
    ): Long {
        require(mode in setOf(DAILY, WEEKLY, MONTHLY))
        require(mode != WEEKLY || daysMask in 1..127)
        val anchor = Calendar.getInstance(zone).apply { timeInMillis = anchorMs }
        val after = Calendar.getInstance(zone).apply { timeInMillis = maxOf(anchorMs, afterExclusiveMs) }
        val startDate = maxOf(date(anchor), date(after))
        // Shared calendarSlots enforces at most 3,660 scanned Gregorian days.
        val horizon = Calendar.getInstance(zone).apply {
            timeInMillis = after.timeInMillis
            add(Calendar.YEAR, 9)
        }
        val spec = PortableRecurrenceSpec(
            seriesId = "android-recurrence-slot",
            start = date(anchor),
            clock = WallClock(
                LocalMinute(anchor.get(Calendar.HOUR_OF_DAY), anchor.get(Calendar.MINUTE)),
                TimeZonePolicy.FOLLOW_DEVICE
            ),
            cadence = PortableCadence.valueOf(mode),
            weekdayMask = if (mode == WEEKLY) daysMask else 0
        )
        val candidates = portableOccurrenceKeys(
            spec, startDate, date(horizon), maxResults = 128
        )
        for (candidate in candidates) {
            // OS native calendar resolves UTC instant; shared code owns whether
            // this original local date is logically part of the routine.
            val native = Calendar.getInstance(zone).apply {
                clear()
                isLenient = false
                set(candidate.date.year, candidate.date.month - 1, candidate.date.day,
                    anchor.get(Calendar.HOUR_OF_DAY), anchor.get(Calendar.MINUTE), 0)
            }
            val epoch = try { native.timeInMillis } catch (_: IllegalArgumentException) {
                // Missing spring DST wall-clock time: skip this occurrence.
                continue
            }
            if (epoch >= anchorMs && epoch > afterExclusiveMs) return epoch
        }
        throw IllegalArgumentException("No valid recurrence slot in the next nine years")
    }

    fun slotKey(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
        date(Calendar.getInstance(zone).apply { timeInMillis = epochMs }).toString()

    fun description(mode: String, mask: Int = 0): String = when (mode) {
        DAILY -> "Daily"
        MONTHLY -> "Monthly (skip missing dates)"
        WEEKLY -> {
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            days.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.joinToString(", ")
        }
        else -> "Once"
    }
}
