package dev.reminderos.core

/**
 * Narrow ObjC/Swift-friendly entry point for the iOS native calendar adapter.
 * The Kotlin common model decides whether a calendar date belongs to a rule;
 * Foundation.Calendar remains responsible for DST and local time -> epoch.
 *
 * Keep public method parameters primitive/String to make Kotlin/Native interop
 * predictable and avoid exporting internal platform-specific model objects.
 */
class RecurrenceBridge {
    fun matchesDate(
        startYear: Int, startMonth: Int, startDay: Int,
        mode: String, weekdayMask: Int,
        year: Int, month: Int, day: Int,
    ): Boolean {
        val cadence = PortableCadence.valueOf(mode)
        val start = CalendarDate(startYear, startMonth, startDay)
        val candidate = CalendarDate(year, month, day)
        val spec = PortableRecurrenceSpec(
            seriesId = "swift-calendar-bridge",
            start = start,
            clock = WallClock(LocalMinute(0, 0), TimeZonePolicy.FOLLOW_DEVICE),
            cadence = cadence,
            weekdayMask = if (cadence == PortableCadence.WEEKLY) weekdayMask else 0,
        )
        return portableOccurrenceKeys(spec, candidate, candidate, 1).isNotEmpty()
    }

    fun originalSlotKey(year: Int, month: Int, day: Int): String =
        CalendarDate(year, month, day).toString()

    fun isActionCurrent(expectedRevision: Int, storedRevision: Int, unresolved: Boolean): Boolean =
        expectedRevision == storedRevision && storedRevision >= 1 && unresolved
}
