package dev.reminderos.preview

import dev.reminderos.core.PortableCadence
import dev.reminderos.core.PortableStorageSnapshot
import dev.reminderos.core.PortableStoredOutcome
import dev.reminderos.core.PortableStoredEvent
import dev.reminderos.core.PortableStoredReminder
import dev.reminderos.core.PortableStoredRule
import dev.reminderos.core.previewPortableMigration
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Versioned, cross-platform RPOSB2 portable export/verification. Legacy
 * Android RPOSB1 export/empty-store restore are deliberately unchanged.
 *
 * NO portable import writes exist. Inspection decrypts into memory, validates
 * ALL rows with the shared Kotlin contract and never changes SQLite or alarms.
 */
internal class PortableEncryptedTransfer(private val database: ReminderDatabase) {
    companion object {
        const val PORTABLE_FORMAT = 1

        private fun nullableString(obj: JSONObject, name: String): String? =
            if (obj.isNull(name) || !obj.has(name)) null else obj.getString(name)

        fun encode(source: PortableStorageSnapshot): ByteArray {
            previewPortableMigration(source)
            val rules = JSONArray()
            for (r in source.rules) rules.put(JSONObject()
                .put("id",r.id).put("title",r.title)
                .put("anchor_at_ms",r.anchorEpochMillis)
                .put("cadence",r.cadence.name).put("weekday_mask",r.weekdayMask)
                .put("paused",r.paused).put("archived",r.archived))
            val reminders = JSONArray()
            for (r in source.reminders) reminders.put(JSONObject()
                .put("id",r.id).put("title",r.title)
                .put("due_at_ms",r.dueEpochMillis)
                .put("original_due_at_ms",r.originalDueEpochMillis)
                .put("revision",r.revision).put("outcome",r.outcome.name)
                .put("paused",r.paused).put("archived",r.archived)
                .put("series_id",r.seriesId ?: JSONObject.NULL)
                .put("slot_key",r.slotKey ?: JSONObject.NULL))
            val events = JSONArray()
            for (e in source.events) events.put(JSONObject()
                .put("id",e.id).put("reminder_id",e.reminderId)
                .put("kind",e.kind).put("revision",e.revision)
                .put("at_ms",e.timestampEpochMillis))
            val bytes = JSONObject()
                .put("portable_format",PORTABLE_FORMAT)
                .put("rules",rules).put("reminders",reminders).put("events",events)
                .toString().toByteArray(StandardCharsets.UTF_8)
            require(bytes.size in 1..EncryptedBackup.MAX_PLAINTEXT_BYTES) {
                "Portable backup is larger than supported; no file was created"
            }
            return bytes
        }

        fun decode(data: ByteArray): PortableStorageSnapshot {
            require(data.size in 1..EncryptedBackup.MAX_PLAINTEXT_BYTES) {
                "Invalid portable snapshot size"
            }
            val root=JSONObject(String(data,StandardCharsets.UTF_8))
            require(root.getInt("portable_format")==PORTABLE_FORMAT) {
                "Unsupported portable snapshot schema"
            }
            val rs=root.getJSONArray("rules")
            val items=root.getJSONArray("reminders")
            val history=root.getJSONArray("events")
            require(rs.length()<=3000 && items.length()<=30000 && history.length()<=100000)
            val rules=(0 until rs.length()).map { i ->
                val o=rs.getJSONObject(i)
                PortableStoredRule(o.getString("id"),o.getString("title"),
                    o.getLong("anchor_at_ms"),PortableCadence.valueOf(o.getString("cadence")),
                    o.getInt("weekday_mask"),o.getBoolean("paused"),o.getBoolean("archived"))
            }
            val reminders=(0 until items.length()).map { i ->
                val o=items.getJSONObject(i)
                PortableStoredReminder(o.getString("id"),o.getString("title"),
                    o.getLong("due_at_ms"),o.getLong("original_due_at_ms"),
                    o.getInt("revision"),PortableStoredOutcome.valueOf(o.getString("outcome")),
                    o.getBoolean("paused"),o.getBoolean("archived"),
                    nullableString(o,"series_id"),nullableString(o,"slot_key"))
            }
            val events=(0 until history.length()).map { i ->
                val o=history.getJSONObject(i)
                PortableStoredEvent(o.getString("id"),o.getString("reminder_id"),
                    o.getString("kind"),o.getInt("revision"),o.getLong("at_ms"))
            }
            return PortableStorageSnapshot(
                rules=rules,reminders=reminders,events=events
            ).also(::previewPortableMigration)
        }

        fun inspectEncrypted(bytes: ByteArray,password: CharArray): String {
            val plain=EncryptedBackup.decryptPortable(bytes,password)
            val data=try { decode(plain) } finally { plain.fill(0) }
            val report=previewPortableMigration(data)
            return "Verified cross-platform encrypted backup (RPOSB2)\n" +
                "Rules: " + report.ruleCount + "\n" +
                "Reminders: " + report.reminderCount + "\n" +
                "Unresolved: " + report.unresolvedCount + "\n" +
                "History events: " + report.eventCount + "\n" +
                "No reminder was changed or imported."
        }
    }

    fun exportEncrypted(password: CharArray): ByteArray {
        val source=AndroidPortableStorageInspector(database).snapshotReadOnly()
        val plain=encode(source)
        return try { EncryptedBackup.encryptPortable(plain,password) }
        finally { plain.fill(0) }
    }
}
