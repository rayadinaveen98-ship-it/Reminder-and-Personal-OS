package dev.reminderos.core

/**
 * Canonical V1 reminder-storage *model*. Adapters normalize Android SQLite V3
 * and iOS JSON V2 into these fields without writing over source data.
 *
 * This is intentionally independent of a wire serializer or a DB vendor:
 * neither platform may delete its original store until a later write/restore
 * migrator passes round-trip and rollback tests.
 */
enum class PortableStoredOutcome { UNKNOWN, DONE, SKIPPED }

data class PortableStoredRule(
    val id: String,
    val title: String,
    val anchorEpochMillis: Long,
    val cadence: PortableCadence,
    val weekdayMask: Int,
    val paused: Boolean,
    val archived: Boolean,
)

data class PortableStoredReminder(
    val id: String,
    val title: String,
    val dueEpochMillis: Long,
    /** Never change this original logical slot when snoozing. */
    val originalDueEpochMillis: Long,
    val revision: Int,
    val outcome: PortableStoredOutcome,
    val paused: Boolean,
    val archived: Boolean,
    val seriesId: String? = null,
    val slotKey: String? = null,
)

data class PortableStoredEvent(
    val id: String,
    val reminderId: String,
    val kind: String,
    val revision: Int,
    val timestampEpochMillis: Long,
)

data class PortableStorageSnapshot(
    val schemaVersion: Int = 1,
    val rules: List<PortableStoredRule>,
    val reminders: List<PortableStoredReminder>,
    val events: List<PortableStoredEvent>,
)

data class PortableMigrationPreview(
    val ruleCount: Int,
    val reminderCount: Int,
    val eventCount: Int,
    val unresolvedCount: Int,
    val archivedReminderCount: Int,
) {
    val canImportIntoEmptyStore: Boolean get() = true
}

private fun requireId(id: String, label: String) {
    require(id.isNotBlank() && id.length <= 128 && id.none { it.isISOControl() }) {
        "Invalid $label identifier"
    }
}

fun portableValidSlotKey(value: String): Boolean {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return false
    return try {
        val parsed = CalendarDate(
            value.substring(0, 4).toInt(),
            value.substring(5, 7).toInt(),
            value.substring(8, 10).toInt(),
        )
        parsed.toString() == value
    } catch (_: IllegalArgumentException) {
        false
    }
}

/**
 * No side effects and no source-data changes. Rejects records that would
 * silently lose history, collapse two logical slots, or point to missing rows.
 * The native scheduling outbox is deliberately not in this snapshot.
 */
fun previewPortableMigration(source: PortableStorageSnapshot): PortableMigrationPreview {
    require(source.schemaVersion == 1) { "Unsupported storage snapshot version" }
    require(source.rules.size <= 3_000 && source.reminders.size <= 30_000 &&
        source.events.size <= 100_000) { "Snapshot is beyond tested size limits" }

    val ruleIds = mutableSetOf<String>()
    source.rules.forEach { rule ->
        requireId(rule.id, "rule")
        require(ruleIds.add(rule.id)) { "Duplicate rule ID" }
        require(rule.title.isNotBlank() && rule.title.length <= 2_000)
        require(rule.anchorEpochMillis > 0) { "Invalid recurrence anchor" }
        require(rule.cadence != PortableCadence.ONCE)
        require(rule.weekdayMask in 0..127)
        require(rule.cadence != PortableCadence.WEEKLY || rule.weekdayMask != 0)
        require(rule.cadence == PortableCadence.WEEKLY || rule.weekdayMask == 0)
    }

    val reminderIds = mutableSetOf<String>()
    val slotIdentities = mutableSetOf<Pair<String, String>>()
    source.reminders.forEach { reminder ->
        requireId(reminder.id, "reminder")
        require(reminderIds.add(reminder.id)) { "Duplicate reminder ID" }
        require(reminder.title.isNotBlank() && reminder.title.length <= 2_000)
        require(reminder.dueEpochMillis > 0 && reminder.originalDueEpochMillis > 0)
        require(reminder.revision >= 1) { "Invalid reminder revision" }
        if (reminder.seriesId == null) {
            require(reminder.slotKey == null) { "Orphaned occurrence date" }
        } else {
            require(reminder.seriesId in ruleIds) { "Missing series for occurrence" }
            require(reminder.slotKey != null && portableValidSlotKey(reminder.slotKey)) {
                "Invalid occurrence date"
            }
            require(slotIdentities.add(reminder.seriesId to reminder.slotKey)) {
                "Duplicate logical occurrence date"
            }
        }
    }

    val eventIds = mutableSetOf<String>()
    source.events.forEach { event ->
        requireId(event.id, "event")
        require(eventIds.add(event.id)) { "Duplicate event ID" }
        require(event.reminderId in reminderIds) { "Orphaned action event" }
        require(event.kind.isNotBlank() && event.kind.length <= 64)
        require(event.revision >= 1)
        require(event.timestampEpochMillis >= 0)
    }

    return PortableMigrationPreview(
        ruleCount = source.rules.size,
        reminderCount = source.reminders.size,
        eventCount = source.events.size,
        unresolvedCount = source.reminders.count {
            it.outcome == PortableStoredOutcome.UNKNOWN
        },
        archivedReminderCount = source.reminders.count { it.archived },
    )
}

/**
 * Simple Swift/Objective-C export through the existing ReminderCore framework.
 * Swift can validate its native reminder rows without mapping Kotlin collection
 * types or mutating its JSON store. Full referential validation remains in the
 * portable batch snapshot above or the native adapter's cross-row checks.
 */
class StorageMigrationBridge {
    fun validateReminderFields(
        id: String,
        title: String,
        dueEpochMillis: Long,
        originalDueEpochMillis: Long,
        revision: Int,
        outcome: String,
        seriesId: String?,
        slotKey: String?,
    ): Boolean = try {
        val record = PortableStoredReminder(
            id = id, title = title, dueEpochMillis = dueEpochMillis,
            originalDueEpochMillis = originalDueEpochMillis,
            revision = revision, outcome = PortableStoredOutcome.valueOf(outcome),
            paused = false, archived = false, seriesId = seriesId, slotKey = slotKey,
        )
        // Include the parent in actual batch validation; here only local row
        // invariants are evaluated.
        requireId(record.id, "reminder")
        require(record.title.isNotBlank() && record.title.length <= 2_000)
        require(record.dueEpochMillis > 0 && record.originalDueEpochMillis > 0)
        require(record.revision >= 1)
        require((record.seriesId == null && record.slotKey == null) ||
            (record.seriesId != null && record.slotKey != null &&
                portableValidSlotKey(record.slotKey)))
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    fun validateRuleFields(
        id: String, title: String, anchorEpochMillis: Long,
        cadence: String, weekdayMask: Int,
    ): Boolean = try {
        val parsedCadence = PortableCadence.valueOf(cadence)
        requireId(id, "rule")
        require(title.isNotBlank() && title.length <= 2_000)
        require(anchorEpochMillis > 0 && parsedCadence != PortableCadence.ONCE)
        require(weekdayMask in 0..127)
        require(parsedCadence != PortableCadence.WEEKLY || weekdayMask > 0)
        require(parsedCadence == PortableCadence.WEEKLY || weekdayMask == 0)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
