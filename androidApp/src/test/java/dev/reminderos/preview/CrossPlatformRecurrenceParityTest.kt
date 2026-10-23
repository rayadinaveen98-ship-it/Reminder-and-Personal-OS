package dev.reminderos.preview

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Same authoritative date-key scenarios checked in KMP and iOS simulator. */
class CrossPlatformRecurrenceParityTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance(utc).apply {
        clear()
        set(y,m-1,d,8,30)
    }.timeInMillis
    private fun slots(
        anchor: Long, mode: String, mask: Int, through: Long
    ): List<String> {
        val keys = mutableListOf<String>()
        var after = anchor-1
        repeat(30) {
            val due = RecurrenceSchedule.next(anchor,after,mode,mask,utc)
            if (due > through) return keys
            keys.add(RecurrenceSchedule.slotKey(due,utc))
            after = due
        }
        return keys
    }

    @Test fun sharedDailyFixture() {
        assertEquals(listOf("2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26"),
            slots(at(2026,10,23),RecurrenceSchedule.DAILY,0,at(2026,10,26)))
    }

    @Test fun sharedMondayWednesdayFridayFixture() {
        assertEquals(listOf("2026-10-23", "2026-10-26", "2026-10-28", "2026-10-30"),
            slots(at(2026,10,23),RecurrenceSchedule.WEEKLY,21,at(2026,10,30)))
    }

    @Test fun sharedTuesdayThursdayFixture() {
        assertEquals(listOf("2026-10-27", "2026-10-29", "2026-11-03"),
            slots(at(2026,10,26),RecurrenceSchedule.WEEKLY,10,at(2026,11,3)))
    }

    @Test fun sharedMonthlyThirtyFirstFixture() {
        assertEquals(listOf("2026-01-31", "2026-03-31"),
            slots(at(2026,1,31),RecurrenceSchedule.MONTHLY,0,at(2026,4,30)))
    }

    @Test fun sharedMonthlyLeapFixture() {
        assertEquals(listOf("2024-01-29", "2024-02-29", "2024-03-29"),
            slots(at(2024,1,29),RecurrenceSchedule.MONTHLY,0,at(2024,3,31)))
    }
}
