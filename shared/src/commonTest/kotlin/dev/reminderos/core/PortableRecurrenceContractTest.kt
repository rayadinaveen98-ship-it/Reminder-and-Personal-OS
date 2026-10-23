package dev.reminderos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableRecurrenceContractTest {
    private val clock = WallClock(LocalMinute(8, 30), TimeZonePolicy.FOLLOW_DEVICE)
    private fun d(y: Int, m: Int, day: Int) = CalendarDate(y, m, day)
    private fun slots(
        mode: PortableCadence, start: CalendarDate,
        from: CalendarDate, through: CalendarDate, mask: Int = 0,
    ): List<String> = portableOccurrenceKeys(
        PortableRecurrenceSpec("series", start, clock, mode, mask),
        from, through
    ).map { it.slotKey }

    @Test fun dailyDateKeysMatchBothPlatformAlphas() {
        assertEquals(listOf("2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26"),
            slots(PortableCadence.DAILY, d(2026, 10, 23), d(2026, 10, 23), d(2026, 10, 26)))
    }

    @Test fun isoMondayWednesdayFridayMaskMatchesNativeAdapters() {
        val mask = portableWeekdayMask(setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY))
        assertEquals(21, mask)
        assertEquals(listOf("2026-10-23", "2026-10-26", "2026-10-28", "2026-10-30"),
            slots(PortableCadence.WEEKLY, d(2026, 10, 23),
                d(2026, 10, 23), d(2026, 10, 30), mask))
    }

    @Test fun selectedTuesdayThursdaySpansWeekendCorrectly() {
        assertEquals(listOf("2026-10-27", "2026-10-29", "2026-11-03"),
            slots(PortableCadence.WEEKLY, d(2026, 10, 26),
                d(2026, 10, 26), d(2026, 11, 3), 10))
    }

    @Test fun monthlyThirtyFirstSkipsUnavailableDates() {
        assertEquals(listOf("2026-01-31", "2026-03-31"),
            slots(PortableCadence.MONTHLY, d(2026, 1, 31),
                d(2026, 1, 1), d(2026, 4, 30)))
    }

    @Test fun monthlyTwentyNinthSupportsLeapYears() {
        assertEquals(listOf("2024-01-29", "2024-02-29", "2024-03-29"),
            slots(PortableCadence.MONTHLY, d(2024, 1, 29),
                d(2024, 1, 1), d(2024, 3, 31)))
    }

    @Test fun onceDoesNotRepeat() {
        assertEquals(listOf("2026-10-23"),
            slots(PortableCadence.ONCE, d(2026, 10, 23),
                d(2026, 10, 1), d(2026, 11, 30)))
    }

    @Test fun emptyWeekdaysAndMisappliedMaskAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            PortableRecurrenceSpec("s", d(2026, 10, 23), clock, PortableCadence.WEEKLY, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PortableRecurrenceSpec("s", d(2026, 10, 23), clock, PortableCadence.DAILY, 1)
        }
    }

    @Test fun eachSlotRetainsSeriesAndOriginalDateKey() {
        val spec = PortableRecurrenceSpec("routine-123", d(2026, 10, 23), clock,
            PortableCadence.DAILY)
        val keys = portableOccurrenceKeys(spec, d(2026, 10, 23), d(2026, 10, 25))
        assertEquals(3, keys.size)
        assertTrue(keys.all { it.seriesId == "routine-123" })
        assertEquals(3, keys.map { it.seriesId to it.slotKey }.toSet().size)
    }

    @Test fun lateNotificationActionIsRejectedAfterSnoozeOrDone() {
        val key = PortableOccurrenceKey("s", d(2026, 10, 23))
        assertTrue(PortableActionGate(key, 2).accepts(2))
        assertFalse(PortableActionGate(key, 2).accepts(1))
        assertFalse(PortableActionGate(key, 2, Outcome.DONE).accepts(2))
        assertFalse(PortableActionGate(key, 2, Outcome.SKIPPED).accepts(2))
    }

    @Test fun unknownActionNeverCountsAsCompleted() {
        val summary = summarize(listOf(
            OccurrenceState("today", 1000, Outcome.UNKNOWN),
            OccurrenceState("yesterday", 1000, Outcome.DONE)
        ), 0, 2000)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.doneCount)
        assertEquals(0, summary.skippedCount)
    }
}
