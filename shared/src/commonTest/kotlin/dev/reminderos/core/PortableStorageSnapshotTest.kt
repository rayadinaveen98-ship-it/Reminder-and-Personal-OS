package dev.reminderos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableStorageSnapshotTest {
    private fun rule(id: String = "r1") = PortableStoredRule(
        id, "Hydrate", 1_700_000_000_000, PortableCadence.DAILY, 0,
        paused = false, archived = false
    )
    private fun reminder(
        id: String = "a1", status: PortableStoredOutcome = PortableStoredOutcome.UNKNOWN,
        date: String = "2026-10-23", series: String? = "r1",
    ) = PortableStoredReminder(
        id, "Hydrate", 1_700_000_010_000, 1_700_000_000_000, 2,
        status, false, false, series, if (series == null) null else date
    )
    private fun event(reminderId: String = "a1") =
        PortableStoredEvent("e1", reminderId, "SNOOZE", 1, 1_700_000_005_000)

    @Test fun healthySnapshotPreservesUnknownAndEventHistory() {
        val snapshot = PortableStorageSnapshot(
            rules = listOf(rule()),
            reminders = listOf(reminder(), reminder(
                id = "a2", date = "2026-10-24", status = PortableStoredOutcome.DONE
            ), reminder(id = "single", series = null, status = PortableStoredOutcome.SKIPPED)),
            events = listOf(event()),
        )
        val preview = previewPortableMigration(snapshot)
        assertEquals(1, preview.ruleCount)
        assertEquals(3, preview.reminderCount)
        assertEquals(1, preview.eventCount)
        assertEquals(1, preview.unresolvedCount)
        assertTrue(preview.canImportIntoEmptyStore)
    }

    @Test fun archivedReminderStillIncluded() {
        val archived = reminder().copy(archived = true)
        val preview = previewPortableMigration(
            PortableStorageSnapshot(rules = listOf(rule()), reminders = listOf(archived), events = emptyList())
        )
        assertEquals(1, preview.archivedReminderCount)
        assertEquals(1, preview.reminderCount)
    }

    @Test fun duplicateOccurrenceKeysFailBeforeAnyWrite() {
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules = listOf(rule()),
                reminders = listOf(reminder(), reminder("a2")),
                events = emptyList()
            ))
        }
    }

    @Test fun orphanedEventAndMissingRuleAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules = listOf(rule()), reminders = listOf(reminder()),
                events = listOf(event("missing"))
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules = emptyList(), reminders = listOf(reminder()),
                events = emptyList()
            ))
        }
    }

    @Test fun malformedDateAndOrphanSlotRejected() {
        assertFalse(portableValidSlotKey("2026-02-29"))
        assertTrue(portableValidSlotKey("2024-02-29"))
        assertFalse(portableValidSlotKey("2026-13-01"))
        assertFalse(portableValidSlotKey("2026-10-23T08:00"))
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules = emptyList(), reminders = listOf(reminder(series = null).copy(slotKey = "2026-10-23")),
                events = emptyList()
            ))
        }
    }

    @Test fun versionOneOnlyAndNoDuplicateIds() {
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                schemaVersion = 2, rules = emptyList(), reminders = emptyList(), events = emptyList()
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules = emptyList(), reminders = listOf(
                    reminder("a1",series=null), reminder("a1",series=null)),
                events = emptyList()
            ))
        }
    }

    @Test fun weeklyMaskAndNonweeklyMaskAreValidated() {
        val weekly = rule().copy(cadence=PortableCadence.WEEKLY, weekdayMask=21)
        assertEquals(1,previewPortableMigration(PortableStorageSnapshot(
            rules=listOf(weekly),reminders=emptyList(),events=emptyList())).ruleCount)
        assertFailsWith<IllegalArgumentException> {
            previewPortableMigration(PortableStorageSnapshot(
                rules=listOf(rule().copy(weekdayMask=31)),reminders=emptyList(),events=emptyList()
            ))
        }
    }

    @Test fun bridgeChecksLocalFieldsButNotGlobalReferences() {
        val bridge=StorageMigrationBridge()
        assertTrue(bridge.validateReminderFields(
            "id", "Test", 1000, 1000, 1, "UNKNOWN", null, null
        ))
        assertTrue(bridge.validateReminderFields(
            "id", "Test", 2000, 1000, 2, "DONE", "rule", "2026-10-23"
        ))
        assertFalse(bridge.validateReminderFields(
            "id", "Test", 2000, 1000, 2, "PENDING", null, null
        ))
        assertFalse(bridge.validateReminderFields(
            "id", "Test", 2000, 1000, 2, "DONE", "rule", "2026-02-29"
        ))
        assertTrue(bridge.validateRuleFields("r", "Rule", 1_700_000, "WEEKLY", 31))
        assertFalse(bridge.validateRuleFields("r", "Rule", 1_700_000, "WEEKLY", 0))
    }
}
