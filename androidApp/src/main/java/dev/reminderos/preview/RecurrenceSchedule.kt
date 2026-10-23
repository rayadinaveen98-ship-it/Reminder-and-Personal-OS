package dev.reminderos.preview

import java.util.Calendar
import java.util.TimeZone

/**
 * Calendar-based recurrence uses local wall-clock fields from the anchor.
 * Android preview uses the device time zone at generation time. Fixed-zone
 * schedules and full DST policy/zone-travel coverage are not implemented yet.
 */
internal object RecurrenceSchedule {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    // ISO weekday bit positions: Monday=bit0 through Sunday=bit6.
    fun isoWeekday(date: Calendar): Int = (date.get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun bitForDay(date: Calendar): Int = 1 shl isoWeekday(date)

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
        // Starting from the anchor (instead of a millis duration) prevents
        // daily repeats drifting after DST transitions.
        val slot = Calendar.getInstance(zone).apply {
            timeInMillis = anchorMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val after = maxOf(afterExclusiveMs, anchorMs - 1)
        val anchorDay = anchor.get(Calendar.DAY_OF_MONTH)
        for (attempt in 0..4000) {
            val matches = when (mode) {
                DAILY -> true
                WEEKLY -> (bitForDay(slot) and daysMask) != 0
                MONTHLY -> slot.get(Calendar.DAY_OF_MONTH) == anchorDay
                else -> false
            }
            if (matches && slot.timeInMillis > after && slot.timeInMillis >= anchorMs)
                return slot.timeInMillis
            slot.add(Calendar.DAY_OF_MONTH, 1)
        }
        throw IllegalArgumentException("No matching occurrence within 4000 days")
    }

    fun slotKey(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
        fun part(v: Int, n: Int) = v.toString().padStart(n, '0')
        return part(c.get(Calendar.YEAR), 4) + "-" +
            part(c.get(Calendar.MONTH) + 1, 2) + "-" +
            part(c.get(Calendar.DAY_OF_MONTH), 2)
    }

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
