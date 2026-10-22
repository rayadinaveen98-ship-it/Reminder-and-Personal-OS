package dev.reminderos.core

private var checks = 0
private fun verify(pass: Boolean, label: String) {
    check(pass) { "FAILED: $label" }
    checks++
}

fun main() {
    val clock = WallClock(LocalMinute(8, 30), TimeZonePolicy.FOLLOW_DEVICE)
    val fixed = WallClock(LocalMinute(20, 0), TimeZonePolicy.FIXED_ZONE, "Asia/Kolkata")
    val feb28 = CalendarDate(2024, 2, 28)
    verify(feb28.nextDay() == CalendarDate(2024, 2, 29), "leap day rollover")
    verify(CalendarDate(2024, 2, 29).nextDay() == CalendarDate(2024, 3, 1), "leap month exit")
    verify(CalendarDate(2023, 12, 31).nextDay() == CalendarDate(2024, 1, 1), "year rollover")
    verify(!isLeapYear(1900) && isLeapYear(2000) && !isLeapYear(2025), "Gregorian century rules")
    verify(CalendarDate(2026, 10, 22).weekday() == Weekday.THURSDAY, "weekday today")
    verify(CalendarDate(2024, 2, 29).weekday() == Weekday.THURSDAY, "leap weekday")
    verify(CalendarDate(2026, 10, 25).weekday() == Weekday.SUNDAY, "Sunday weekday")
    verify(CalendarDate(2026, 10, 22).toString() == "2026-10-22", "ISO calendar format")
    verify(runCatching { CalendarDate(1900, 2, 29) }.isFailure, "invalid non-leap 1900")
    verify(runCatching { CalendarDate(2026, 13, 1) }.isFailure, "invalid month")
    verify(runCatching { LocalMinute(24, 0) }.isFailure, "invalid clock hour")
    verify(runCatching { WallClock(LocalMinute(1, 0), TimeZonePolicy.FIXED_ZONE) }.isFailure,
        "fixed zone must identify a zone")
    verify(runCatching { WallClock(LocalMinute(1, 0), TimeZonePolicy.FOLLOW_DEVICE, "UTC") }.isFailure,
        "device zone must not be mixed with fixed zone")
    val once = CalendarRule.Once(CalendarDate(2026, 10, 23), clock)
    verify(calendarSlots(once, CalendarDate(2026, 10, 22), CalendarDate(2026, 10, 24)).map { it.logicalKey } ==
        listOf("2026-10-23"), "once inside window")
    verify(calendarSlots(once, CalendarDate(2026, 10, 24), CalendarDate(2026, 10, 26)).isEmpty(),
        "once outside window")
    verify(calendarSlots(once, CalendarDate(2026, 10, 24), CalendarDate(2026, 10, 23)).isEmpty(),
        "reversed date window empty")
    val daily = CalendarRule.Daily(CalendarDate(2026, 12, 30), fixed)
    val dailySlots = calendarSlots(daily, CalendarDate(2026, 12, 29), CalendarDate(2027, 1, 2))
    verify(dailySlots.map { it.logicalKey } == listOf("2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02"),
        "daily date range with inclusive start")
    verify(dailySlots.all { it.clock == fixed }, "retain wall time and zone policy")
    verify(calendarSlots(daily, CalendarDate(2026, 12, 30), CalendarDate(2027, 1, 5), maxResults = 2)
        .size == 2, "result cap")
    val weekly = CalendarRule.Weekly(CalendarDate(2026, 10, 22), clock,
        setOf(Weekday.MONDAY, Weekday.FRIDAY))
    verify(calendarSlots(weekly, CalendarDate(2026, 10, 22), CalendarDate(2026, 11, 2))
        .map { it.logicalKey } == listOf("2026-10-23", "2026-10-26", "2026-10-30", "2026-11-02"),
        "weekdays crossing Sunday")
    verify(runCatching { CalendarRule.Weekly(CalendarDate(2026, 10, 22), clock, emptySet()) }.isFailure,
        "weekly requires weekdays")
    val monthly31 = CalendarRule.Monthly(CalendarDate(2026, 1, 1), clock, 31)
    verify(calendarSlots(monthly31, CalendarDate(2026, 1, 1), CalendarDate(2026, 4, 30))
        .map { it.logicalKey } == listOf("2026-01-31", "2026-03-31"),
        "monthly missing date is skipped, not shifted")
    val monthly29 = CalendarRule.Monthly(CalendarDate(2024, 1, 1), clock, 29)
    verify(calendarSlots(monthly29, CalendarDate(2024, 1, 1), CalendarDate(2024, 3, 31))
        .map { it.logicalKey } == listOf("2024-01-29", "2024-02-29", "2024-03-29"),
        "monthly 29 in leap year")
    verify(runCatching { CalendarRule.Monthly(CalendarDate(2026, 1, 1), clock, 32) }.isFailure,
        "invalid month day")
    verify(runCatching { calendarSlots(monthly31, CalendarDate(2026, 1, 1),
        CalendarDate(2038, 1, 1), maxResults = 1000) }.isFailure, "bounded calendar horizon")

    val x = AlertIntent("a", 1_000, 1)
    val y = AlertIntent("b", 2_000, 1)
    val regX = PlatformRegistration("os-x", "a", 1_000, 1)
    val regY = PlatformRegistration("os-y", "b", 2_000, 1)
    verify(planReconciliation(emptyList(), emptyList()).commands.isEmpty(), "empty plan")
    val fresh = planReconciliation(listOf(x, y), emptyList())
    verify(fresh.registrations.map { it.intent.occurrenceId } == listOf("a", "b"), "register absent")
    verify(fresh.cancellations.isEmpty(), "no unnecessary cancellations")
    val matched = planReconciliation(listOf(x, y), listOf(regX, regY))
    verify(matched.commands.isEmpty() && matched.unchanged == 2, "unchanged is idempotent")
    val stale = planReconciliation(emptyList(), listOf(regX, regY))
    verify(stale.cancellations.size == 2 && stale.registrations.isEmpty(), "cancel stale")
    val revised = planReconciliation(listOf(x.copy(revision = 2)), listOf(regX))
    verify(revised.commands.size == 2 && revised.commands.first() is ScheduleCommand.Cancel &&
        revised.commands.last() is ScheduleCommand.Register, "revision cancels before register")
    val retimed = planReconciliation(listOf(x.copy(fireAtEpochMillis = 4_000)), listOf(regX))
    verify(retimed.cancellations.size == 1 && retimed.registrations.size == 1, "retime replaces registration")
    val duplicates = planReconciliation(listOf(x), listOf(regX,
        PlatformRegistration("os-x2", "a", 1_000, 1)))
    verify(duplicates.cancellations.size == 2 && duplicates.registrations.size == 1,
        "duplicate native registrations repaired")
    verify(runCatching { planReconciliation(listOf(x, x), emptyList()) }.isFailure,
        "duplicate desired intents rejected")
    verify(runCatching { planReconciliation(emptyList(), listOf(regX, regX)) }.isFailure,
        "duplicate platform handles rejected")
    val mixed = planReconciliation(listOf(y, x), listOf(regY))
    verify(mixed.unchanged == 1 && mixed.registrations.single().intent.occurrenceId == "a",
        "partial reconciliation retains correct registration")
    val ordered = planReconciliation(listOf(AlertIntent("z", 3_000, 1), x), emptyList())
    verify(ordered.registrations.map { it.intent.occurrenceId } == listOf("a", "z"),
        "register earliest first")
    println("PASS: $checks recurrence and reconciliation JVM smoke checks")
}
