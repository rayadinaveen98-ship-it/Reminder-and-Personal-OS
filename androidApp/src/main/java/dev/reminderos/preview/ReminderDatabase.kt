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
    val lastFiredRevision: Int,
    val paused: Boolean,
    val archived: Boolean
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
    SQLiteOpenHelper(context, "reminders_alpha.db", null, 2) {
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
            updated_at INTEGER NOT NULL,
            paused INTEGER NOT NULL DEFAULT 0 CHECK(paused IN (0,1)),
            archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1))
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
        require(oldVersion == 1 && newVersion == 2) { "Unsupported migration $oldVersion -> $newVersion" }
        // Additive migration: every existing v0.2 reminder and action stays intact.
        db.execSQL("ALTER TABLE reminders ADD COLUMN paused INTEGER NOT NULL DEFAULT 0 CHECK(paused IN (0,1))")
        db.execSQL("ALTER TABLE reminders ADD COLUMN archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1))")
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
        status = c.getString(5), lastFiredRevision = c.getInt(6),
        paused = c.getInt(7) != 0, archived = c.getInt(8) != 0
    )
    private fun byId(db: SQLiteDatabase, id: String): ReminderRecord? =
        db.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,paused,archived FROM reminders WHERE id=?",
            arrayOf(id)
        ).use { c -> if (c.moveToFirst()) mapReminder(c) else null }

    @Synchronized fun all(): List<ReminderRecord> =
        readableDatabase.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,paused,archived FROM reminders " +
                "WHERE archived=0 ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, due_at ASC",
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
    @Synchronized fun act(id: String, expectedRevision: Int, action: String,
                          now: Long = System.currentTimeMillis(), snoozeMinutes: Int = 10): Boolean =
        transaction { db ->
            val old = byId(db, id) ?: return@transaction false
            if (old.revision != expectedRevision || old.status != "PENDING" ||
                old.paused || old.archived) return@transaction false
            if (action !in setOf("DONE", "SKIP", "SNOOZE")) return@transaction false
            if (action == "SNOOZE" && snoozeMinutes !in setOf(5,10,30,60)) return@transaction false
            val updatedRev = if (action == "SNOOZE") old.revision + 1 else old.revision
            val updatedDue = if (action == "SNOOZE") now + snoozeMinutes * 60_000L else old.dueAt
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
            if (old.status != "PENDING" || old.paused || old.archived ||
                old.revision != revision || old.lastFiredRevision == revision)
                return@transaction null
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
        return row != null && row.status == "PENDING" && !row.paused && !row.archived &&
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
                "SELECT id,revision,due_at FROM reminders WHERE status='PENDING' AND archived=0 AND paused=0 AND due_at>?",
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


    /** Rev guards prohibit stale UI edits and stale notification actions. */
    @Synchronized fun edit(id: String, expectedRevision: Int, title: String, due: Long,
                           now: Long = System.currentTimeMillis()): Boolean {
        if (title.isBlank() || due <= now + 1000L) return false
        return transaction { db ->
            val old = byId(db, id) ?: return@transaction false
            if (old.revision != expectedRevision || old.archived || old.status != "PENDING") return@transaction false
            val next = old.revision + 1
            db.execSQL(
                "UPDATE reminders SET title=?,due_at=?,original_due_at=?,revision=?,last_fired_revision=-1,updated_at=? WHERE id=?",
                arrayOf(title.trim(), due, due, next, now, id))
            event(db, id, "EDITED", next, now)
            retireRegistrations(db, id)
            newJob(db, id, "CANCEL", expectedRevision, null, now)
            if (!old.paused) newJob(db, id, "REGISTER", next, due, now)
            true
        }
    }

    @Synchronized fun setPaused(id: String, expectedRevision: Int, paused: Boolean,
                                now: Long = System.currentTimeMillis()): Boolean =
        transaction { db ->
            val old = byId(db, id) ?: return@transaction false
            if (old.revision != expectedRevision || old.status != "PENDING" ||
                old.archived || old.paused == paused) return@transaction false
            // An overdue reminder is not silently moved to another date.
            if (!paused && old.dueAt <= now + 1000L) return@transaction false
            val next = old.revision + 1
            db.execSQL(
                "UPDATE reminders SET paused=?,revision=?,last_fired_revision=-1,updated_at=? WHERE id=?",
                arrayOf(if (paused) 1 else 0, next, now, id))
            event(db, id, if (paused) "PAUSED" else "RESUMED", next, now)
            retireRegistrations(db, id)
            newJob(db, id, "CANCEL", expectedRevision, null, now)
            if (!paused) newJob(db, id, "REGISTER", next, old.dueAt, now)
            true
        }

    /** Soft archive keeps history and does not delete the row referenced by events. */
    @Synchronized fun archive(id: String, expectedRevision: Int, now: Long = System.currentTimeMillis()): Boolean =
        transaction { db ->
            val old = byId(db, id) ?: return@transaction false
            if (old.revision != expectedRevision || old.archived) return@transaction false
            db.execSQL("UPDATE reminders SET archived=1,revision=?,updated_at=? WHERE id=?",
                arrayOf(old.revision + 1, now, id))
            event(db, id, "ARCHIVED", old.revision + 1, now)
            retireRegistrations(db, id)
            newJob(db, id, "CANCEL", old.revision, null, now)
            true
        }

    data class EventRow(val kind: String, val atMs: Long)
    @Synchronized fun recentEvents(id: String, maxRows: Int = 12): List<EventRow> =
        readableDatabase.rawQuery(
            "SELECT kind,at_ms FROM events WHERE reminder_id=? ORDER BY at_ms DESC,id DESC LIMIT ?",
            arrayOf(id,maxRows.toString())
        ).use { c ->
            val rows = mutableListOf<EventRow>()
            while (c.moveToNext()) rows.add(EventRow(c.getString(0),c.getLong(1)))
            rows
        }

    /** Recheck accepted OS registrations after opening app or recovering. */
    @Synchronized fun reconcileFuture(): Int {
        val now = System.currentTimeMillis()
        return transaction { db ->
            val pending = db.rawQuery(
                "SELECT id,revision,due_at FROM reminders WHERE status='PENDING' AND archived=0 AND paused=0 AND due_at>?",
                arrayOf(now.toString())
            ).use { c ->
                val out = mutableListOf<Triple<String,Int,Long>>()
                while(c.moveToNext()) out.add(Triple(c.getString(0),c.getInt(1),c.getLong(2)))
                out
            }
            for ((id,revision,due) in pending) {
                val hasPending = db.rawQuery(
                    """SELECT 1 FROM schedule_jobs WHERE reminder_id=? AND kind='REGISTER'
                       AND revision=? AND fire_at=? AND state IN ('PENDING','FAILED') LIMIT 1""",
                    arrayOf(id,revision.toString(),due.toString())
                ).use { it.moveToFirst() }
                if (!hasPending) newJob(db,id,"REGISTER",revision,due,now)
            }
            pending.size
        }
    }

    @Synchronized fun counts(): Triple<Int, Int, Int> =
        readableDatabase.rawQuery(
            "SELECT status,COUNT(*) FROM reminders WHERE archived=0 GROUP BY status", null
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
