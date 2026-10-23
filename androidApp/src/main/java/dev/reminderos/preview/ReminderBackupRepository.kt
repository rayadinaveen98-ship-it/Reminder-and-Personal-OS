package dev.reminderos.preview

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Android alpha V0.3c backup repository. Export includes user reminder/series data
 * and audit events; native OS registrations and pending operation handles are
 * intentionally excluded because they are invalid on a different installation.
 *
 * Restore is an all-or-nothing import into an EMPTY app database. It will never
 * replace or merge existing reminders. The caller rebuilds native scheduling only
 * after the SQLite commit returns successfully.
 */
internal class ReminderBackupRepository(context: Context) {
    private val store = ReminderDatabase(context.applicationContext)

    private fun cursorArray(cursor: Cursor): JSONArray {
        val out = JSONArray()
        cursor.use { c ->
            while (c.moveToNext()) {
                val obj = JSONObject()
                for (i in 0 until c.columnCount) {
                    val key = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> obj.put(key, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> obj.put(key, c.getLong(i))
                        Cursor.FIELD_TYPE_STRING -> obj.put(key, c.getString(i))
                        else -> throw IllegalArgumentException("Unsupported field type in backup")
                    }
                }
                out.put(obj)
                require(out.length() <= 30_000) { "Too many records for this backup version" }
            }
        }
        return out
    }

    private fun snapshot(): ByteArray {
        val db = store.readableDatabase
        // No native schedule jobs or platform handles. The backup is portable.
        val root = JSONObject()
            .put("backup_format", 1)
            .put("created_at_ms", System.currentTimeMillis())
            .put("recurrence_rules", cursorArray(db.rawQuery(
                "SELECT id,title,anchor_ms,mode,days_mask,paused,archived,created_at,updated_at FROM recurrence_rules",null)))
            .put("reminders", cursorArray(db.rawQuery(
                """SELECT id,title,due_at,original_due_at,revision,status,
                   last_fired_revision,updated_at,paused,archived,series_id,slot_key FROM reminders""", null)))
            .put("events", cursorArray(db.rawQuery(
                "SELECT id,reminder_id,kind,revision,at_ms FROM events",null)))
        return root.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun exportEncrypted(password: CharArray): ByteArray {
        val plain = snapshot()
        return try { EncryptedBackup.encrypt(plain,password) }
        finally { plain.fill(0) }
    }

    fun hasUserRecords(): Boolean {
        val db = store.readableDatabase
        return count(db,"reminders") > 0 || count(db,"recurrence_rules") > 0 || count(db,"events") > 0
    }

    private fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table",null).use {
            it.moveToFirst()
            it.getLong(0)
        }

    private fun identifier(s: String, label: String): String {
        require(s.length in 1..128 && s.none { it.isISOControl() }) { "Invalid $label identifier" }
        return s
    }

    private fun validate(payload: JSONObject) {
        require(payload.getInt("backup_format") == 1) { "Unsupported backup format" }
        val rules = payload.getJSONArray("recurrence_rules")
        val reminders = payload.getJSONArray("reminders")
        val events = payload.getJSONArray("events")
        require(rules.length() <= 3_000 && reminders.length() <= 30_000 &&
                events.length() <= 30_000) { "Too many backup records" }

        val ruleIds = mutableSetOf<String>()
        for(i in 0 until rules.length()) {
            val r=rules.getJSONObject(i)
            val id=identifier(r.getString("id"),"rule")
            require(ruleIds.add(id)) { "Duplicate recurrence rule" }
            val mode=r.getString("mode")
            require(mode in setOf("DAILY","WEEKLY","MONTHLY"))
            require(r.getLong("anchor_ms") > 0)
            val mask=r.getInt("days_mask")
            require(mask in 0..127 && (mode != "WEEKLY" || mask != 0))
            require(r.getString("title").isNotBlank())
            for(flag in listOf("paused","archived")) require(r.getInt(flag) in 0..1)
        }
        val reminderIds=mutableSetOf<String>()
        val slotIds=mutableSetOf<String>()
        for(i in 0 until reminders.length()) {
            val r=reminders.getJSONObject(i)
            val id=identifier(r.getString("id"),"reminder")
            require(reminderIds.add(id)) { "Duplicate reminder ID" }
            require(r.getString("title").isNotBlank() && r.getString("title").length <= 2000)
            require(r.getLong("due_at") > 0 && r.getLong("original_due_at") > 0)
            require(r.getInt("revision") > 0)
            require(r.getString("status") in setOf("PENDING","DONE","SKIPPED"))
            for(flag in listOf("paused","archived")) require(r.getInt(flag) in 0..1)
            val series = r.optString("series_id","").takeUnless { r.isNull("series_id") }
            if(series != null) {
                require(series in ruleIds) { "Missing recurrence rule" }
                val slot=r.getString("slot_key")
                require(slot.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid calendar slot" }
                require(slotIds.add(series+":"+slot)) { "Duplicate occurrence date" }
            } else {
                require(r.isNull("slot_key")) { "Unlinked occurrence date" }
            }
        }
        val eventIds=mutableSetOf<String>()
        for(i in 0 until events.length()) {
            val e=events.getJSONObject(i)
            require(eventIds.add(identifier(e.getString("id"),"event")))
            require(e.getString("reminder_id") in reminderIds) { "Orphan event" }
            require(e.getString("kind").length in 1..64)
            require(e.getInt("revision") > 0)
            require(e.getLong("at_ms") >= 0)
        }
    }

    /**
     * Decrypt/validate first, then one SQLite transaction; never replace records
     * in a populated app. Password failures cannot modify the database.
     */
    fun importEncrypted(encrypted: ByteArray, password: CharArray): Int {
        val plain=EncryptedBackup.decrypt(encrypted,password)
        val root=try { JSONObject(String(plain,StandardCharsets.UTF_8)) }
                 finally { plain.fill(0) }
        validate(root)
        val rules=root.getJSONArray("recurrence_rules")
        val reminders=root.getJSONArray("reminders")
        val events=root.getJSONArray("events")
        val db=store.writableDatabase
        db.beginTransaction()
        try {
            require(count(db,"reminders")==0L && count(db,"recurrence_rules")==0L &&
                    count(db,"events")==0L) {
                "This app already contains reminders. Restore only into an empty installation."
            }
            for(i in 0 until rules.length()) {
                val r=rules.getJSONObject(i)
                db.execSQL(
                    """INSERT INTO recurrence_rules
                    (id,title,anchor_ms,mode,days_mask,paused,archived,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?)""",
                    arrayOf<Any?>(
                        r.getString("id"),r.getString("title"),r.getLong("anchor_ms"),
                        r.getString("mode"),r.getInt("days_mask"),r.getInt("paused"),
                        r.getInt("archived"),r.getLong("created_at"),r.getLong("updated_at"))
                )
            }
            val now=System.currentTimeMillis()
            for(i in 0 until reminders.length()) {
                val r=reminders.getJSONObject(i)
                val activeFuture = r.getString("status")=="PENDING" &&
                    r.getInt("paused")==0 && r.getInt("archived")==0 &&
                    r.getLong("due_at")>now
                db.execSQL(
                    """INSERT INTO reminders(id,title,due_at,original_due_at,revision,status,
                       last_fired_revision,updated_at,paused,archived,series_id,slot_key)
                       VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
                    arrayOf<Any?>(
                        r.getString("id"),r.getString("title"),r.getLong("due_at"),
                        r.getLong("original_due_at"),r.getInt("revision"),
                        r.getString("status"),
                        if(activeFuture) -1 else r.getInt("last_fired_revision"),
                        r.getLong("updated_at"),r.getInt("paused"),r.getInt("archived"),
                        if(r.isNull("series_id")) null else r.getString("series_id"),
                        if(r.isNull("slot_key")) null else r.getString("slot_key")
                    )
                )
            }
            for(i in 0 until events.length()) {
                val e=events.getJSONObject(i)
                db.execSQL("INSERT INTO events(id,reminder_id,kind,revision,at_ms) VALUES(?,?,?,?,?)",
                    arrayOf<Any?>(
                        e.getString("id"),e.getString("reminder_id"),e.getString("kind"),
                        e.getInt("revision"),e.getLong("at_ms")
                    )
                )
            }
            // Fresh native OS handles must be regenerated on this device. Pending
            // future reminders are persisted first; app calls recoverFuture() after commit.
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return reminders.length()
    }
}
