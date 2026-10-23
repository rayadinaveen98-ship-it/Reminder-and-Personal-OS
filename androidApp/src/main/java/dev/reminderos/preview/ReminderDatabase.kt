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
    val archived: Boolean,
    val seriesId: String? = null,
    val slotKey: String? = null
)
internal data class RecurrenceRule(
    val id: String, val title: String, val anchorMs: Long,
    val mode: String, val daysMask: Int, val paused: Boolean, val archived: Boolean
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
    SQLiteOpenHelper(context, "reminders_alpha.db", null, 3) {
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
        upgradeRecurrenceTables(db)
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..2 && newVersion == 3) { "Unsupported migration $oldVersion -> $newVersion" }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN paused INTEGER NOT NULL DEFAULT 0 CHECK(paused IN (0,1))")
            db.execSQL("ALTER TABLE reminders ADD COLUMN archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1))")
        }
        upgradeRecurrenceTables(db)
    }

    private fun upgradeRecurrenceTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS recurrence_rules(
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            anchor_ms INTEGER NOT NULL,
            mode TEXT NOT NULL CHECK(mode IN ('DAILY','WEEKLY','MONTHLY')),
            days_mask INTEGER NOT NULL DEFAULT 0,
            paused INTEGER NOT NULL DEFAULT 0 CHECK(paused IN (0,1)),
            archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)),
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("ALTER TABLE reminders ADD COLUMN series_id TEXT REFERENCES recurrence_rules(id)")
        db.execSQL("ALTER TABLE reminders ADD COLUMN slot_key TEXT")
        db.execSQL("CREATE UNIQUE INDEX recurring_slot_unique ON reminders(series_id,slot_key)")
        db.execSQL("CREATE INDEX recurring_series_due ON reminders(series_id,due_at)")
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
        paused = c.getInt(7) != 0, archived = c.getInt(8) != 0,
        seriesId = if (c.isNull(9)) null else c.getString(9),
        slotKey = if (c.isNull(10)) null else c.getString(10)
    )
    private fun byId(db: SQLiteDatabase, id: String): ReminderRecord? =
        db.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,paused,archived,series_id,slot_key FROM reminders WHERE id=?",
            arrayOf(id)
        ).use { c -> if (c.moveToFirst()) mapReminder(c) else null }

    @Synchronized fun all(): List<ReminderRecord> =
        readableDatabase.rawQuery(
            "SELECT id,title,due_at,original_due_at,revision,status,last_fired_revision,paused,archived,series_id,slot_key FROM reminders " +
                "WHERE archived=0 ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, due_at ASC",
            null
        ).use { c ->
            val result = mutableListOf<ReminderRecord>()
            while (c.moveToNext()) result.add(mapReminder(c))
            // Keep one-time history visible. For each repeating routine show
            // its nearest two upcoming occurrences and one unresolved overdue.
            val singles = result.filter { it.seriesId == null }
            val series = result.filter { it.seriesId != null }.groupBy { it.seriesId!! }
            val now = System.currentTimeMillis()
            singles + series.values.flatMap { items ->
                val upcoming = items.filter { it.status == "PENDING" && it.dueAt >= now }
                    .sortedBy { it.dueAt }.take(2)
                val overdue = items.filter { it.status == "PENDING" && it.dueAt < now }
                    .maxByOrNull { it.dueAt }
                (listOfNotNull(overdue) + upcoming).distinctBy { it.id }
            }.sortedBy { it.dueAt }
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

    /**
     * An existing pending/failed job can already be retried by flush(). For a
     * normal app resume, an APPLIED registration is also considered sufficient:
     * do not append another job just because the UI was reopened.
     *
     * After an actual device restart (or explicit user repair), OS alarm state
     * may have disappeared even though APPLIED was durably recorded. In that
     * case force=true allows one new job while still deduplicating queued work.
     */
    private fun ensureRegisterJob(
        db: SQLiteDatabase, id: String, revision: Int, due: Long,
        now: Long, force: Boolean
    ): Boolean {
        val states = if (force) "'PENDING','FAILED'" else "'PENDING','FAILED','APPLIED'"
        val alreadyExists = db.rawQuery(
            "SELECT 1 FROM schedule_jobs WHERE reminder_id=? AND kind='REGISTER' " +
                "AND revision=? AND fire_at=? AND state IN ($states) LIMIT 1",
            arrayOf(id, revision.toString(), due.toString())
        ).use { it.moveToFirst() }
        if (alreadyExists) return false
        newJob(db, id, "REGISTER", revision, due, now)
        return true
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
            // Reboot invalidates OS alarms, not the SQLite APPLIED row. Requeue
            // once per current occurrence, but never duplicate a queued retry.
            pending.count { (id, rev, due) ->
                ensureRegisterJob(db, id, rev, due, now, force = true)
            }
        }
    }



    @Synchronized fun rules(): List<RecurrenceRule> =
        readableDatabase.rawQuery(
            "SELECT id,title,anchor_ms,mode,days_mask,paused,archived FROM recurrence_rules WHERE archived=0 ORDER BY created_at",
            null
        ).use { c ->
            val result = mutableListOf<RecurrenceRule>()
            while(c.moveToNext()) result.add(
                RecurrenceRule(c.getString(0),c.getString(1),c.getLong(2),c.getString(3),
                    c.getInt(4),c.getInt(5)!=0,c.getInt(6)!=0)
            )
            result
        }

    @Synchronized fun pendingOccurrenceIdsForRoutine(seriesId: String): List<String> =
        readableDatabase.rawQuery(
            "SELECT id FROM reminders WHERE series_id=? AND status='PENDING'",
            arrayOf(seriesId)
        ).use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }

    @Synchronized fun rule(id: String): RecurrenceRule? =
        readableDatabase.rawQuery(
            "SELECT id,title,anchor_ms,mode,days_mask,paused,archived FROM recurrence_rules WHERE id=?",
            arrayOf(id)
        ).use { c ->
            if(!c.moveToFirst()) null else RecurrenceRule(
                c.getString(0),c.getString(1),c.getLong(2),c.getString(3),
                c.getInt(4),c.getInt(5)!=0,c.getInt(6)!=0
            )
        }

    @Synchronized fun addRecurring(title: String, anchorMs: Long, mode: String, daysMask: Int): String {
        require(title.isNotBlank() && anchorMs > System.currentTimeMillis() + 1000)
        require(mode in setOf("DAILY","WEEKLY","MONTHLY"))
        require(mode!="WEEKLY" || daysMask in 1..127)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        transaction { db ->
            db.execSQL(
                """INSERT INTO recurrence_rules(id,title,anchor_ms,mode,days_mask,created_at,updated_at)
                   VALUES(?,?,?,?,?,?,?)""",
                arrayOf(id,title.trim(),anchorMs,mode,daysMask,now,now)
            )
            materialize(db, id, title.trim(), anchorMs, mode, daysMask, now)
        }
        return id
    }

    /**
     * Materialize up to 7 future distinct calendar slots. Missed slots stay UNKNOWN
     * and do not prevent later slots being generated. Cap catch-up per transaction
     * to avoid unbounded work after a long time away from the app.
     */
    private fun materialize(db: SQLiteDatabase, id: String, title: String,
                            anchor: Long, mode: String, mask: Int, now: Long) {
        val last = db.rawQuery("SELECT MAX(original_due_at) FROM reminders WHERE series_id=?",
            arrayOf(id)).use { c -> if(c.moveToFirst() && !c.isNull(0)) c.getLong(0) else anchor-1L }
        var after = last
        var future = db.rawQuery(
            "SELECT COUNT(*) FROM reminders WHERE series_id=? AND original_due_at>?",
            arrayOf(id,now.toString())
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        var generated=0
        while (future < 7 && generated < 90) {
            val due = RecurrenceSchedule.next(anchor,after,mode,mask)
            val slot = RecurrenceSchedule.slotKey(due)
            val occurrence = UUID.randomUUID().toString()
            db.execSQL(
                """INSERT OR IGNORE INTO reminders
                  (id,title,due_at,original_due_at,revision,status,updated_at,series_id,slot_key)
                  VALUES(?,?,?,?,1,'PENDING',?,?,?)""",
                arrayOf(occurrence,title,due,due,now,id,slot)
            )
            if (due>now) {
                val present = db.rawQuery("SELECT id FROM reminders WHERE series_id=? AND slot_key=?",
                    arrayOf(id,slot)).use { c -> c.moveToFirst() }
                if(present) {
                    // Only newly inserted slots get registration jobs.
                    val inserted = db.rawQuery("SELECT 1 FROM reminders WHERE id=?",
                        arrayOf(occurrence)).use { c -> c.moveToFirst() }
                    if(inserted) {
                        event(db,occurrence,"CREATED",1,now)
                        newJob(db,occurrence,"REGISTER",1,due,now)
                    }
                }
                future++
            } else {
                val inserted = db.rawQuery("SELECT 1 FROM reminders WHERE id=?",
                    arrayOf(occurrence)).use { c -> c.moveToFirst() }
                if(inserted) event(db,occurrence,"MISSED_SLOT_UNKNOWN",1,now)
            }
            after=due
            generated++
        }
    }

    @Synchronized fun ensureRecurring(): Int {
        val now=System.currentTimeMillis()
        return transaction { db ->
            val list = db.rawQuery(
                "SELECT id,title,anchor_ms,mode,days_mask FROM recurrence_rules WHERE paused=0 AND archived=0",
                null
            ).use { c ->
                val rows=mutableListOf<RecurrenceRule>()
                while(c.moveToNext()) rows.add(
                    RecurrenceRule(c.getString(0),c.getString(1),c.getLong(2),
                        c.getString(3),c.getInt(4),false,false))
                rows
            }
            for (rule in list) materialize(db,rule.id,rule.title,rule.anchorMs,rule.mode,rule.daysMask,now)
            list.size
        }
    }

    @Synchronized fun setRoutinePaused(id: String, paused: Boolean): Boolean =
        transaction { db ->
            val row=rule(id) ?: return@transaction false
            if(row.paused==paused || row.archived) return@transaction false
            val now=System.currentTimeMillis()
            db.execSQL("UPDATE recurrence_rules SET paused=?,updated_at=? WHERE id=?",
                arrayOf(if(paused) 1 else 0,now,id))
            val futures=db.rawQuery(
                "SELECT id,revision,due_at FROM reminders WHERE series_id=? AND status='PENDING' AND archived=0",
                arrayOf(id)
            ).use { c ->
                val rows=mutableListOf<Triple<String,Int,Long>>()
                while(c.moveToNext()) rows.add(Triple(c.getString(0),c.getInt(1),c.getLong(2)))
                rows
            }
            for ((rid,rev,due) in futures) {
                val nextRev=rev+1
                db.execSQL("UPDATE reminders SET paused=?,revision=?,updated_at=? WHERE id=?",
                    arrayOf(if(paused) 1 else 0,nextRev,now,rid))
                retireRegistrations(db,rid)
                newJob(db,rid,"CANCEL",rev,null,now)
                if(!paused && due>now) newJob(db,rid,"REGISTER",nextRev,due,now)
            }
            true
        }

    @Synchronized fun archiveRoutine(id: String): Boolean =
        transaction { db ->
            val row=rule(id) ?: return@transaction false
            if(row.archived) return@transaction false
            val now=System.currentTimeMillis()
            db.execSQL("UPDATE recurrence_rules SET archived=1,updated_at=? WHERE id=?",
                arrayOf(now,id))
            val records=db.rawQuery(
                "SELECT id,revision FROM reminders WHERE series_id=? AND archived=0",
                arrayOf(id)
            ).use { c ->
                val rows=mutableListOf<Pair<String,Int>>()
                while(c.moveToNext()) rows.add(c.getString(0) to c.getInt(1))
                rows
            }
            for ((rid,rev) in records) {
                db.execSQL("UPDATE reminders SET archived=1,revision=?,updated_at=? WHERE id=?",
                    arrayOf(rev+1,now,rid))
                retireRegistrations(db,rid)
                newJob(db,rid,"CANCEL",rev,null,now)
            }
            true
        }

    @Synchronized fun routineHistory(id: String): String =
        readableDatabase.rawQuery(
            """SELECT slot_key,status,original_due_at FROM reminders
               WHERE series_id=? AND (original_due_at <=? OR status <> 'PENDING')
               ORDER BY original_due_at DESC LIMIT 25""",
            arrayOf(id,System.currentTimeMillis().toString())
        ).use { c ->
            val lines=mutableListOf<String>()
            while(c.moveToNext()) lines.add(c.getString(0)+" · "+c.getString(1))
            if(lines.isEmpty()) "No due occurrences yet" else lines.joinToString("\n")
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
    /**
     * Safe on every app resume. Explicit Repair uses force=true, which retries
     * previously applied native registrations when Android may have cleared
     * alarms after settings/OS changes.
     */
    @Synchronized fun reconcileFuture(force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        return transaction { db ->
            val future = db.rawQuery(
                "SELECT id,revision,due_at FROM reminders WHERE status='PENDING' " +
                    "AND archived=0 AND paused=0 AND due_at>?",
                arrayOf(now.toString())
            ).use { c ->
                val out = mutableListOf<Triple<String,Int,Long>>()
                while (c.moveToNext())
                    out.add(Triple(c.getString(0),c.getInt(1),c.getLong(2)))
                out
            }
            future.count { (id, revision, due) ->
                ensureRegisterJob(db, id, revision, due, now, force)
            }
        }
    }

    data class SchedulingHealth(
        val pendingJobs: Int, val failedJobs: Int,
        val activeFuture: Int, val overdueUnknown: Int
    )

    @Synchronized fun schedulingHealth(): SchedulingHealth {
        val db = readableDatabase
        val states = db.rawQuery(
            "SELECT state,COUNT(*) FROM schedule_jobs WHERE state IN ('PENDING','FAILED') GROUP BY state",
            null
        ).use { c ->
            val values = mutableMapOf<String,Int>()
            while (c.moveToNext()) values[c.getString(0)] = c.getInt(1)
            values
        }
        val now = System.currentTimeMillis()
        val active = db.rawQuery(
            "SELECT COUNT(*) FROM reminders WHERE status='PENDING' AND archived=0 AND paused=0 AND due_at>?",
            arrayOf(now.toString())
        ).use { it.moveToFirst(); it.getInt(0) }
        val overdue = db.rawQuery(
            "SELECT COUNT(*) FROM reminders WHERE status='PENDING' AND archived=0 AND due_at<=?",
            arrayOf(now.toString())
        ).use { it.moveToFirst(); it.getInt(0) }
        return SchedulingHealth(
            states["PENDING"] ?: 0, states["FAILED"] ?: 0, active, overdue
        )
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
