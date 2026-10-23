package dev.reminderos.preview

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RecurrenceScheduleTest {
    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int = 8): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, 30)
        }.timeInMillis

    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun dailyGeneratesNextCalendarDay() {
        val start = at(utc,2026,10,23)
        assertEquals("2026-10-23", RecurrenceSchedule.slotKey(
            RecurrenceSchedule.next(start,start-1,RecurrenceSchedule.DAILY,zone=utc),utc))
        assertEquals("2026-10-24", RecurrenceSchedule.slotKey(
            RecurrenceSchedule.next(start,start,RecurrenceSchedule.DAILY,zone=utc),utc))
    }

    @Test fun weekdaysExcludeSaturdayAndSunday() {
        val friday = at(utc,2026,10,23)
        val next = RecurrenceSchedule.next(friday,friday,RecurrenceSchedule.WEEKLY,31,utc)
        assertEquals("2026-10-26",RecurrenceSchedule.slotKey(next,utc))
    }

    @Test fun selectedWeekdaysUseIsoBitPositions() {
        val monday = at(utc,2026,10,26)
        val tuesdayThursday = (1 shl 1) or (1 shl 3)
        val tue = RecurrenceSchedule.next(monday,monday-1,RecurrenceSchedule.WEEKLY,tuesdayThursday,utc)
        assertEquals("2026-10-27",RecurrenceSchedule.slotKey(tue,utc))
        val thu = RecurrenceSchedule.next(monday,tue,RecurrenceSchedule.WEEKLY,tuesdayThursday,utc)
        assertEquals("2026-10-29",RecurrenceSchedule.slotKey(thu,utc))
    }

    @Test fun nonexistentMonthlyDateIsSkipped() {
        val january = at(utc,2026,1,31)
        val march = RecurrenceSchedule.next(january,january,RecurrenceSchedule.MONTHLY,zone=utc)
        assertEquals("2026-03-31",RecurrenceSchedule.slotKey(march,utc))
    }

    @Test fun leapDayMonthlyScheduleIncludesFebruary29() {
        val jan29 = at(utc,2024,1,29)
        val feb29 = RecurrenceSchedule.next(jan29,jan29,RecurrenceSchedule.MONTHLY,zone=utc)
        assertEquals("2024-02-29",RecurrenceSchedule.slotKey(feb29,utc))
    }

    @Test fun dailyWallClockSurvivesSpringDST() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val mar7 = at(ny,2026,3,7)
        val mar8 = RecurrenceSchedule.next(mar7,mar7,RecurrenceSchedule.DAILY,zone=ny)
        val time = Calendar.getInstance(ny).apply { timeInMillis=mar8 }
        assertEquals(8,time.get(Calendar.HOUR_OF_DAY))
        assertEquals(30,time.get(Calendar.MINUTE))
        assertEquals("2026-03-08",RecurrenceSchedule.slotKey(mar8,ny))
        assertEquals(23L * 60 * 60 * 1000, mar8-mar7)
    }

    @Test fun dailyWallClockSurvivesFallDST() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val oct31=at(ny,2026,10,31)
        val nov1=RecurrenceSchedule.next(oct31,oct31,RecurrenceSchedule.DAILY,zone=ny)
        assertEquals(25L * 60 * 60 * 1000,nov1-oct31)
        val date=Calendar.getInstance(ny).apply { timeInMillis=nov1 }
        assertEquals(8,date.get(Calendar.HOUR_OF_DAY))
    }

    @Test(expected=IllegalArgumentException::class)
    fun emptyWeekdayMaskRejected() {
        val mon=at(utc,2026,10,26)
        RecurrenceSchedule.next(mon,mon,RecurrenceSchedule.WEEKLY,0,utc)
    }

    @Test fun startNeverMovesBeforeAnchor() {
        val start=at(utc,2026,10,23)
        val next=RecurrenceSchedule.next(start,start-100000,RecurrenceSchedule.DAILY,zone=utc)
        assertEquals(start,next)
    }

    @Test fun descriptionsAreGrounded() {
        assertEquals("Mon, Wed, Fri",RecurrenceSchedule.description(
            RecurrenceSchedule.WEEKLY,(1 shl 0) or (1 shl 2) or (1 shl 4)))
    }
}
