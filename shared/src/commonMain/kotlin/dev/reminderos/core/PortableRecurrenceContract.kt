package dev.reminderos.core

/**
 * Portable recurrence contract shared by Android and iOS.
 *
 * The date slots and weekday-mask semantics are platform independent.
 * Native platforms still resolve each local wall clock to an absolute instant,
 * especially around DST gaps/folds and device time-zone changes.
 */
enum class PortableCadence { ONCE, DAILY, WEEKLY, MONTHLY }

data class PortableRecurrenceSpec(
    val seriesId: String,
    val start: CalendarDate,
    val clock: WallClock,
    val cadence: PortableCadence,
    /** ISO weekday mask: Monday bit 0 through Sunday bit 6. */
    val weekdayMask: Int = 0,
) {
    init {
        require(seriesId.isNotBlank() && seriesId.length <= 128) {
            "Series ID must contain 1 to 128 characters"
        }
        require(weekdayMask in 0..127) { "Invalid ISO weekday mask" }
        require(cadence != PortableCadence.WEEKLY || weekdayMask != 0) {
            "Weekly recurrence requires at least one selected day"
        }
        require(cadence == PortableCadence.WEEKLY || weekdayMask == 0) {
            "Weekday mask is only valid for weekly recurrence"
        }
    }

    fun calendarRule(): CalendarRule = when (cadence) {
        PortableCadence.ONCE -> CalendarRule.Once(start, clock)
        PortableCadence.DAILY -> CalendarRule.Daily(start, clock)
        PortableCadence.WEEKLY -> CalendarRule.Weekly(
            start, clock, Weekday.entries.filterTo(mutableSetOf()) {
                weekdayMask and (1 shl (it.iso - 1)) != 0
            }
        )
        PortableCadence.MONTHLY -> CalendarRule.Monthly(start, clock, start.day)
    }
}

/** Both native stores use (seriesId, ISO local date) as the logical identity. */
data class PortableOccurrenceKey(val seriesId: String, val date: CalendarDate) {
    init { require(seriesId.isNotBlank()) }
    val slotKey: String get() = date.toString()
}

/**
 * The result is bounded; no platform-specific Date/Calendar objects and no
 * invented UTC instants. The app can persist each occurrence independently,
 * keeping UNKNOWN when its notification was ignored.
 */
fun portableOccurrenceKeys(
    spec: PortableRecurrenceSpec,
    fromInclusive: CalendarDate,
    throughInclusive: CalendarDate,
    maxResults: Int = 64,
): List<PortableOccurrenceKey> = calendarSlots(
    spec.calendarRule(), fromInclusive, throughInclusive, maxResults
).map { PortableOccurrenceKey(spec.seriesId, it.date) }

/** Enables a native UI mask with the same weekday order on both platforms. */
fun portableWeekdayMask(days: Set<Weekday>): Int =
    days.fold(0) { bits, day -> bits or (1 shl (day.iso - 1)) }

/**
 * A revision is attached to the individual logical occurrence, never to the
 * entire recurring series. A late action for a superseded snooze is rejected.
 */
data class PortableActionGate(
    val occurrence: PortableOccurrenceKey,
    val revision: Int,
    val resolved: Outcome = Outcome.UNKNOWN,
) {
    init { require(revision >= 1) }
    fun accepts(expectedRevision: Int): Boolean =
        expectedRevision == revision && resolved == Outcome.UNKNOWN
}
