package dev.reminderos.preview

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

internal data class ReminderRecord(
    val id: String,
    val title: String,
    val dueAt: Long,
    val originalDueAt: Long,
    val revision: Int,
    val status: String,
    val lastFiredRevision: Int
)
internal data class ScheduleJob(
    val id: String,
    val reminderId: String,
    val kind: String,
    val revision: Int,
    val fireAt: Long?
)

/**
 * Android alpha SQLite store. All changes to a reminder and its pending native
 * schedule operations occur in one SQLite transaction. The OS operation follows
 * separately and can be retried using a deterministic PendingIntent identity.
 *
 * This Android-local implementation is NOT the shared KMP database adapter.
 */
internal class ReminderDatabase(context: Context) :
    SQLiteOpenHelper(context, "reminders_alpha.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("""CREATE TABLE reminders(
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            due_at INTEGER NOT NULL,
            original_due_at INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            status TEXT NOT NULL CHECK(status IN ('PENDING','DONE','SKIPPED')),
            last_fired_revision INTEGER NOT NULL DEFAULT -1,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE events(
            id TEXT PRIMARY KEY NOT NULL,
            reminder_id TEXT NOT NULL REFERENCES reminders(id),
            kind TEXT NOT NULL,
            revision INTEGER NOT NULL,
            at_ms INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE schedule_jobs(
            id TEXT PRIMARY KEY NOT NULL,
            reminder_id TEXT NOT NULL REFERENCES reminders(id),
            kind TEXT NOT NULL CHECK(kind IN ('REGISTER','CANCEL')),
            revision INTEGER NOT NULL,
            fire_at INTEGER,
            state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','FAILED','SUPERSEDED')),
            attempts INTEGER NOT NULL DEFAULT 0,
            error TEXT,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX schedule_jobs_state_idx ON schedule_jobs(state, created_at)")
        db.execSQL("CREATE INDEX reminders_due_idx ON reminders(status,due_at)")
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("No destructive migration allowed from $oldVersion to $newVersion")
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    private fun mapReminder(c: android.database.Cursor) = ReminderRecord(
        id = c.getString(0), title = c.getString(1), dueAt = c.getLong(2),
        originalDueAt = c.getLong(3), revision = c.getInt(4),
        status = c.getString(5), lastFiredRevision = c.getInt(6)
    )
    private fun byId(db: SQLiteDatabase, id: String): ReminderRecord? =
        db.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision FROM reminders WHERE id=?",
            arrayOf(id)
        ).use { c -> if (c.moveToFirst()) mapReminder(c) else null }

    @Synchronized fun all(): List<ReminderRecord> =
        readableDatabase.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision FROM reminders " +
                "ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, due_at ASC",
            null
        ).use { c ->
            val result = mutableListOf<ReminderRecord>()
            while (c.moveToNext()) result.add(mapReminder(c))
            result
        }

    @Synchronized fun get(id: String): ReminderRecord? = byId(readableDatabase, id)

    private fun newJob(db: SQLiteDatabase, reminderId: String, kind: String, revision: Int,
                       due: Long?, now: Long) {
        db.execSQL(
            "INSERT INTO schedule_jobs(id,reminder_id,kind,revision,fire_at,state,created_at) " +
                "VALUES(?,?,?,?,?,'PENDING',?)",
            arrayOf(UUID.randomUUID().toString(), reminderId, kind, revision, due, now)
        )
    }
    private fun retireRegistrations(db: SQLiteDatabase, id: String) {
        db.execSQL(
            "UPDATE schedule_jobs SET state='SUPERSEDED' WHERE reminder_id=? AND kind='REGISTER' " +
                "AND state IN ('PENDING','FAILED')",
            arrayOf(id)
        )
    }
    private fun event(db: SQLiteDatabase, id: String, kind: String, rev: Int, now: Long) {
        db.execSQL(
            "INSERT INTO events(id,reminder_id,kind,revision,at_ms) VALUES(?,?,?,?,?)",
            arrayOf(UUID.randomUUID().toString(), id, kind, rev, now)
        )
    }

    @Synchronized fun add(title: String, due: Long): String {
        require(title.isNotBlank() && due > System.currentTimeMillis() + 1000)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        transaction { db ->
            db.execSQL(
                "INSERT INTO reminders(id,title,due_at,original_due_at,revision,status,updated_at) " +
                    "VALUES(?,?,?,?,1,'PENDING',?)",
                arrayOf(id, title.trim(), due, due, now)
            )
            event(db, id, "CREATED", 1, now)
            newJob(db, id, "REGISTER", 1, due, now)
        }
        return id
    }

    /**
     * Expected revision prevents a delayed notification-action callback from
     * applying after a snooze or any other state change.
     */
    @Synchronized fun act(id: String, expectedRevision: Int, action: String, now: Long = System.currentTimeMillis()): Boolean =
        transaction { db ->
            val old = byId(db, id) ?: return@transaction false
            if (old.revision != expectedRevision || old.status != "PENDING") return@transaction false
            if (action !in setOf("DONE", "SKIP", "SNOOZE")) return@transaction false
            val updatedRev = if (action == "SNOOZE") old.revision + 1 else old.revision
            val updatedDue = if (action == "SNOOZE") now + 10 * 60_000L else old.dueAt
            val updatedStatus = when (action) {
                "DONE" -> "DONE"
                "SKIP" -> "SKIPPED"
                else -> "PENDING"
            }
            db.execSQL(
                "UPDATE reminders SET due_at=?,status=?,revision=?,updated_at=? WHERE id=?",
                arrayOf(updatedDue, updatedStatus, updatedRev, now, id)
            )
            event(db, id, action, expectedRevision, now)
            retireRegistrations(db, id)
            newJob(db, id, "CANCEL", expectedRevision, null, now)
            if (action == "SNOOZE") newJob(db, id, "REGISTER", updatedRev, updatedDue, now)
            true
        }

    /** Recording a callback does not imply notification visibility or task completion. */
    @Synchronized fun receivedAlarm(id: String, revision: Int, now: Long = System.currentTimeMillis()): ReminderRecord? =
        transaction { db ->
            val old = byId(db, id) ?: return@transaction null
            if (old.status != "PENDING" || old.revision != revision ||
                old.lastFiredRevision == revision) return@transaction null
            db.execSQL("UPDATE reminders SET last_fired_revision=? WHERE id=?",
                arrayOf(revision, id))
            event(db, id, "ALARM_CALLBACK", revision, now)
            old
        }

    @Synchronized fun jobs(): List<ScheduleJob> =
        readableDatabase.rawQuery(
            "SELECT id,reminder_id,kind,revision,fire_at FROM schedule_jobs " +
                "WHERE state IN ('PENDING','FAILED') " +
                "ORDER BY CASE kind WHEN 'CANCEL' THEN 0 ELSE 1 END, created_at,id LIMIT 250",
            null
        ).use { c ->
            val result = mutableListOf<ScheduleJob>()
            while (c.moveToNext()) result.add(ScheduleJob(
                c.getString(0), c.getString(1), c.getString(2), c.getInt(3),
                if (c.isNull(4)) null else c.getLong(4)
            ))
            result
        }

    @Synchronized fun currentJob(job: ScheduleJob): Boolean {
        val row = get(job.reminderId)
        return row != null && row.status == "PENDING" &&
            row.revision == job.revision && row.dueAt == job.fireAt
    }
    @Synchronized fun completeJob(id: String, success: Boolean, error: String? = null) {
        transaction { db ->
            db.execSQL(
                "UPDATE schedule_jobs SET state=?,attempts=attempts+1,error=? " +
                    "WHERE id=? AND state IN ('PENDING','FAILED')",
                arrayOf(if (success) "APPLIED" else "FAILED", error, id)
            )
        }
    }
    @Synchronized fun retireJob(id: String) {
        writableDatabase.execSQL("UPDATE schedule_jobs SET state='SUPERSEDED' WHERE id=? AND state IN ('PENDING','FAILED')",
            arrayOf(id))
    }

    /** OS alarms do not survive reboot: re-project only outstanding future reminders. */
    @Synchronized fun recoverFuture(): Int {
        val now = System.currentTimeMillis()
        return transaction { db ->
            val pending = db.rawQuery(
                "SELECT id,revision,due_at FROM reminders WHERE status='PENDING' AND due_at>?",
                arrayOf(now.toString())
            ).use { c ->
                val out = mutableListOf<Triple<String, Int, Long>>()
                while (c.moveToNext()) out.add(Triple(c.getString(0), c.getInt(1), c.getLong(2)))
                out
            }
            pending.forEach { (id, rev, due) ->
                retireRegistrations(db, id)
                newJob(db, id, "REGISTER", rev, due, now)
            }
            pending.size
        }
    }

    @Synchronized fun counts(): Triple<Int, Int, Int> =
        readableDatabase.rawQuery(
            "SELECT status,COUNT(*) FROM reminders GROUP BY status", null
        ).use { c ->
            var pending = 0
            var done = 0
            var skipped = 0
            while (c.moveToNext()) when (c.getString(0)) {
                "PENDING" -> pending = c.getInt(1)
                "DONE" -> done = c.getInt(1)
                "SKIPPED" -> skipped = c.getInt(1)
            }
            Triple(pending, done, skipped)
        }
}
