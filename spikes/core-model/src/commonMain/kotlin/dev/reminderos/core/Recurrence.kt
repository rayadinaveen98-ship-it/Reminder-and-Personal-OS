package dev.reminderos.core

/** Pure Gregorian date and recurrence model. Time-zone and DST resolution is a native-adapter responsibility. */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(year in 1900..9999) { "Year outside supported range" }
        require(month in 1..12) { "Month outside 1..12" }
        require(day in 1..daysInMonth(year, month)) { "Invalid day for month" }
    }

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun nextDay(): CalendarDate = when {
        day < daysInMonth(year, month) -> copy(day = day + 1)
        month < 12 -> CalendarDate(year, month + 1, 1)
        else -> CalendarDate(year + 1, 1, 1)
    }

    /** ISO weekdays: Monday = 1, Sunday = 7. Gregorian Sakamoto algorithm. */
    fun weekday(): Weekday {
        val table = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        val y = if (month < 3) year - 1 else year
        val sundayZero = (y + y / 4 - y / 100 + y / 400 + table[month - 1] + day) % 7
        return Weekday.entries.first { it.iso == if (sundayZero == 0) 7 else sundayZero }
    }

    override fun toString(): String =
        year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0') +
            "-" + day.toString().padStart(2, '0')
}

fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

fun daysInMonth(year: Int, month: Int): Int {
    require(month in 1..12)
    return when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

enum class Weekday(val iso: Int) {
    MONDAY(1), TUESDAY(2), WEDNESDAY(3), THURSDAY(4), FRIDAY(5), SATURDAY(6), SUNDAY(7)
}

data class LocalMinute(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23)
        require(minute in 0..59)
    }
}

enum class TimeZonePolicy { FOLLOW_DEVICE, FIXED_ZONE }

/**
 * FIXED_ZONE stores an IANA zone identifier; validation and wall-clock resolution belong
 * to the native platform adapter. FOLLOW_DEVICE stores no fixed zone identifier.
 */
data class WallClock(val time: LocalMinute, val policy: TimeZonePolicy, val fixedZoneId: String? = null) {
    init {
        require((policy == TimeZonePolicy.FIXED_ZONE) == (!fixedZoneId.isNullOrBlank())) {
            "Fixed zone required only for FIXED_ZONE mode"
        }
    }
}

sealed class CalendarRule(open val start: CalendarDate, open val clock: WallClock) {
    data class Once(override val start: CalendarDate, override val clock: WallClock) : CalendarRule(start, clock)
    data class Daily(override val start: CalendarDate, override val clock: WallClock) : CalendarRule(start, clock)
    data class Weekly(
        override val start: CalendarDate,
        override val clock: WallClock,
        val weekdays: Set<Weekday>,
    ) : CalendarRule(start, clock) {
        init { require(weekdays.isNotEmpty()) { "Select at least one weekday" } }
    }
    /** Monthly on chosen calendar day; absent dates (e.g. Feb 30) are skipped, not shifted. */
    data class Monthly(
        override val start: CalendarDate,
        override val clock: WallClock,
        val dayOfMonth: Int,
    ) : CalendarRule(start, clock) {
        init { require(dayOfMonth in 1..31) }
    }
}

data class LocalSlot(val date: CalendarDate, val clock: WallClock) {
    val logicalKey: String get() = date.toString()
}

/**
 * Generate local calendar slots within [fromInclusive, throughInclusive]. Never manufacture
 * a UTC instant here: gaps/folds in a time zone require explicit native resolution policy.
 * Caller supplies a bounded horizon (at most 3660 scanned days) and a result cap.
 */
fun calendarSlots(
    rule: CalendarRule,
    fromInclusive: CalendarDate,
    throughInclusive: CalendarDate,
    maxResults: Int = 64,
): List<LocalSlot> {
    require(maxResults in 1..1000)
    if (fromInclusive > throughInclusive) return emptyList()
    val result = mutableListOf<LocalSlot>()
    var date = maxOf(rule.start, fromInclusive)
    var scannedDays = 0
    while (date <= throughInclusive && result.size < maxResults) {
        require(++scannedDays <= 3660) { "Horizon exceeds 3660 days; request a shorter window" }
        val matches = when (rule) {
            is CalendarRule.Once -> date == rule.start
            is CalendarRule.Daily -> true
            is CalendarRule.Weekly -> date.weekday() in rule.weekdays
            is CalendarRule.Monthly -> date.day == rule.dayOfMonth
        }
        if (matches) result.add(LocalSlot(date, rule.clock))
        if (rule is CalendarRule.Once || date == CalendarDate(9999, 12, 31)) break
        date = date.nextDay()
    }
    return result
}
