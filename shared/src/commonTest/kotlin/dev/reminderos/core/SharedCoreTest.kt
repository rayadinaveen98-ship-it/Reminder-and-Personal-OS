package dev.reminderos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNull

class SharedCoreTest {
    @Test fun acknowledgementDoesNotClaimCompletion() {
        val initial = OccurrenceState("o1", 1000)
        val result = assertIs<ApplyResult.Applied>(
            applyAction(initial, Action("ack1", ActionKind.ACKNOWLEDGE, 1200))
        ).next
        assertEquals(Outcome.UNKNOWN, result.outcome)
        assertTrue(result.acknowledged)
        assertIs<ApplyResult.Duplicate>(
            applyAction(result, Action("ack1", ActionKind.ACKNOWLEDGE, 1200))
        )
    }

    @Test fun calendarRulesAreNotTwentyFourHourTimers() {
        val time = WallClock(LocalMinute(8, 0), TimeZonePolicy.FIXED_ZONE, "Asia/Kolkata")
        val rule = CalendarRule.Daily(CalendarDate(2026, 10, 22), time)
        val dates = calendarSlots(rule, CalendarDate(2026, 10, 22), CalendarDate(2026, 10, 24))
            .map { it.date.toString() }
        assertEquals(listOf("2026-10-22", "2026-10-23", "2026-10-24"), dates)
    }

    @Test fun monthlyThirtyFirstSkipsShortMonths() {
        val clock = WallClock(LocalMinute(8, 0), TimeZonePolicy.FOLLOW_DEVICE)
        val slots = calendarSlots(
            CalendarRule.Monthly(CalendarDate(2026, 1, 1), clock, 31),
            CalendarDate(2026, 1, 1), CalendarDate(2026, 4, 30)
        )
        assertEquals(listOf("2026-01-31", "2026-03-31"), slots.map { it.logicalKey })
    }

    @Test fun unknownIsNotCountedAsSkipped() {
        val summary = summarize(
            listOf(OccurrenceState("done", 100, Outcome.DONE), OccurrenceState("unknown", 100)),
            0, 200
        )
        assertEquals(0, summary.skippedCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(0.5, summary.confirmedCompletionCoverage)
        assertEquals(1.0, summary.knownOutcomeCompletionRate)
    }

    private class FakeGateway : NativeScheduleGateway {
        val events = mutableListOf<String>()
        var refuseCancellation = false
        var refuseRegistration = false
        override fun capabilities() = SchedulerCapabilities(DeliveryCapability.PRECISE)
        override fun cancel(registration: PlatformRegistration): Boolean {
            events.add("cancel:" + registration.occurrenceId)
            return !refuseCancellation
        }
        override fun schedule(intent: AlertIntent): PlatformScheduleResult {
            events.add("register:" + intent.occurrenceId)
            return if (refuseRegistration) PlatformScheduleResult.Rejected("NOT_AUTHORIZED")
                else PlatformScheduleResult.Accepted("handle-" + intent.occurrenceId)
        }
    }

    @Test fun cancellationFailureDoesNotCauseDuplicateNotification() {
        val existing = PlatformRegistration("old", "o", 1000, 1)
        val plan = planReconciliation(listOf(AlertIntent("o", 2000, 2)), listOf(existing))
        val gateway = FakeGateway().apply { refuseCancellation = true }
        val report = executeReconciliation(plan, gateway)
        assertEquals(listOf("cancel:o"), gateway.events)
        assertEquals(0, report.attemptedRegistrations)
        assertEquals(0, report.acceptedRegistrations.size)
        assertTrue(report.failedOperations.contains("REGISTER_BLOCKED:o"))
    }

    @Test fun successfulReplacementCancelsThenRegisters() {
        val existing = PlatformRegistration("old", "o", 1000, 1)
        val plan = planReconciliation(listOf(AlertIntent("o", 2000, 2)), listOf(existing))
        val gateway = FakeGateway()
        val report = executeReconciliation(plan, gateway)
        assertEquals(listOf("cancel:o", "register:o"), gateway.events)
        assertTrue(report.fullyApplied)
        assertEquals(2000L, report.acceptedRegistrations.single().fireAtEpochMillis)
    }

    @Test fun registrationDenialIsExplicitlyReported() {
        val gateway = FakeGateway().apply { refuseRegistration = true }
        val report = executeReconciliation(planReconciliation(listOf(AlertIntent("o", 1000, 1)), emptyList()), gateway)
        assertEquals(0, report.acceptedRegistrations.size)
        assertEquals("REGISTER_FAILED:o:NOT_AUTHORIZED", report.failedOperations.single())
        assertNull(summarize(emptyList(), 0, 1000).knownOutcomeCompletionRate)
    }
}
