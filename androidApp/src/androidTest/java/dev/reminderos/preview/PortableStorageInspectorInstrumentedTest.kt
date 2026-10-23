package dev.reminderos.preview

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableStorageInspectorInstrumentedTest {
    private lateinit var context: Context
    private val dbName="reminders_alpha.db"

    @Before fun startEmpty() {
        context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.deleteDatabase(dbName) ||
            !context.getDatabasePath(dbName).exists())
    }

    @After fun clean() { context.deleteDatabase(dbName) }

    private fun count(db: ReminderDatabase, table: String): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table",null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test fun inspectDoesNotChangeAnyRowsOrNativeScheduleJobs() {
        ReminderDatabase(context).use { db ->
            val tomorrow=System.currentTimeMillis()+86_400_000
            val once=db.add("Once",tomorrow)
            val current=requireNotNull(db.get(once))
            assertTrue(db.act(once,current.revision,"DONE"))
            db.addRecurring("Daily",tomorrow,"DAILY",31) // legacy nonweekly mask
            val before=mapOf(
                "reminders" to count(db,"reminders"),
                "events" to count(db,"events"),
                "schedule_jobs" to count(db,"schedule_jobs"),
                "recurrence_rules" to count(db,"recurrence_rules")
            )
            val result=AndroidPortableStorageInspector(db).inspect()
            assertEquals(1,result.ruleCount)
            assertEquals(before.getValue("reminders"),result.reminderCount)
            assertEquals(before.getValue("events"),result.eventCount)
            assertEquals(1,result.unresolvedCount.let { result.reminderCount-it })
            val after=before.keys.associateWith { count(db,it) }
            assertEquals(before,after)
        }
    }

    @Test fun archiveHistoryIsIncludedEvenWhenHiddenFromUi() {
        ReminderDatabase(context).use { db ->
            val reminderId=db.add("Archived",System.currentTimeMillis()+60_000)
            val record=requireNotNull(db.get(reminderId))
            assertTrue(db.archive(reminderId,record.revision))
            val preview=AndroidPortableStorageInspector(db).inspect()
            assertEquals(1,preview.reminderCount)
            assertEquals(1,preview.archivedReminderCount)
            assertEquals(0,db.all().size)
        }
    }

    @Test fun corruptLogicalSlotIsRejectedWithoutUpdatingSqlite() {
        ReminderDatabase(context).use { db ->
            db.addRecurring("Monthly",System.currentTimeMillis()+60_000,"MONTHLY",0)
            val before=count(db,"reminders")
            db.writableDatabase.execSQL(
                """UPDATE reminders SET slot_key='2026-02-29'
                   WHERE id=(SELECT id FROM reminders WHERE series_id IS NOT NULL
                             ORDER BY original_due_at LIMIT 1)"""
            )
            try {
                AndroidPortableStorageInspector(db).inspect()
                fail("Invalid recurring dates must block migration preview")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("Invalid occurrence date"))
            }
            assertEquals(before,count(db,"reminders"))
        }
    }
}
