package dev.reminderos.preview

import dev.reminderos.core.PortableCadence
import dev.reminderos.core.PortableMigrationPreview
import dev.reminderos.core.PortableStorageSnapshot
import dev.reminderos.core.PortableStoredEvent
import dev.reminderos.core.PortableStoredOutcome
import dev.reminderos.core.PortableStoredReminder
import dev.reminderos.core.PortableStoredRule
import dev.reminderos.core.previewPortableMigration

/**
 * Read-only compatibility inspection of the Android SQLite v3 store.
 *
 * Includes archived reminders, all generated occurrence rows and ALL audit
 * events, rather than relying on the UI's intentionally condensed list.
 * Explicitly excludes schedule_jobs: PendingIntent/OS registrations cannot
 * be migrated to a different app or device.
 *
 * Migration writes are NOT performed in this phase.
 */
internal class AndroidPortableStorageInspector(private val store: ReminderDatabase) {
    private fun snapshot(): PortableStorageSnapshot {
        val db = store.readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val rules = db.rawQuery(
                "SELECT id,title,anchor_ms,mode,days_mask,paused,archived FROM recurrence_rules",
                null
            ).use { c ->
                val out=mutableListOf<PortableStoredRule>()
                while(c.moveToNext()) {
                    val mode=PortableCadence.valueOf(c.getString(3))
                    // Older alpha UI supplied the weekly day mask even for
                    // daily/monthly rules. It has no meaning for those rules.
                    // Normalize in memory; do NOT edit the source SQLite rows.
                    val days=if(mode==PortableCadence.WEEKLY) c.getInt(4) else 0
                    out.add(PortableStoredRule(c.getString(0),c.getString(1),
                        c.getLong(2),mode,days,c.getInt(5)!=0,c.getInt(6)!=0))
                }
                out
            }
            val reminders = db.rawQuery(
                """SELECT id,title,due_at,original_due_at,revision,status,
                   paused,archived,series_id,slot_key FROM reminders""", null
            ).use { c ->
                val out=mutableListOf<PortableStoredReminder>()
                while(c.moveToNext()) {
                    val status=when(c.getString(5)) {
                        "PENDING" -> PortableStoredOutcome.UNKNOWN
                        "DONE" -> PortableStoredOutcome.DONE
                        "SKIPPED" -> PortableStoredOutcome.SKIPPED
                        else -> throw IllegalArgumentException("Unknown Android status")
                    }
                    out.add(PortableStoredReminder(
                        id=c.getString(0),title=c.getString(1),
                        dueEpochMillis=c.getLong(2),
                        originalDueEpochMillis=c.getLong(3),
                        revision=c.getInt(4),outcome=status,
                        paused=c.getInt(6)!=0,archived=c.getInt(7)!=0,
                        seriesId=if(c.isNull(8)) null else c.getString(8),
                        slotKey=if(c.isNull(9)) null else c.getString(9)
                    ))
                }
                out
            }
            val events = db.rawQuery(
                "SELECT id,reminder_id,kind,revision,at_ms FROM events", null
            ).use { c ->
                val out=mutableListOf<PortableStoredEvent>()
                while(c.moveToNext())
                    out.add(PortableStoredEvent(c.getString(0),c.getString(1),
                        c.getString(2),c.getInt(3),c.getLong(4)))
                out
            }
            db.setTransactionSuccessful()
            return PortableStorageSnapshot(
                rules=rules,reminders=reminders,events=events
            )
        } finally { db.endTransaction() }
    }

    fun inspect(): PortableMigrationPreview = previewPortableMigration(snapshot())
}
