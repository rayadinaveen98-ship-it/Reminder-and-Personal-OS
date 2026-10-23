package dev.reminderos.preview

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android SQLite tests; intentionally do not call AlarmManager or claim
 * that a posted OS notification was seen by a user.
 */
@RunWith(AndroidJUnit4::class)
class ReminderRecoveryInstrumentedTest {
    private lateinit var context: Context
    private val dbName = "reminders_alpha.db"

    @Before fun startEmpty() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.deleteDatabase(dbName) || !context.getDatabasePath(dbName).exists())
    }
    @After fun cleanup() { context.deleteDatabase(dbName) }

    private fun due() = System.currentTimeMillis() + 6 * 60 * 60 * 1000L

    private fun outbox(db: ReminderDatabase, state: String, kind: String = "REGISTER"): Int =
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM schedule_jobs WHERE state=? AND kind=?",
            arrayOf(state,kind)
        ).use { it.moveToFirst(); it.getInt(0) }

    @Test fun repeatedAppResumeDoesNotDuplicateQueuedRegistration() {
        ReminderDatabase(context).use { db ->
            db.add("test", due())
            assertEquals(1,outbox(db,"PENDING"))
            repeat(3) { assertEquals(0,db.reconcileFuture()) }
            assertEquals(1,outbox(db,"PENDING"))
            assertEquals(0,db.reconcileFuture(force=true))
        }
    }

    @Test fun appliedRegistrationIsRespectedUntilExplicitRepair() {
        ReminderDatabase(context).use { db ->
            db.add("test",due())
            val job = db.jobs().single()
            db.completeJob(job.id,true)
            repeat(3) { assertEquals(0,db.reconcileFuture()) }
            assertEquals(0,outbox(db,"PENDING"))
            assertEquals(1,db.reconcileFuture(force=true))
            assertEquals(0,db.reconcileFuture(force=true))
            assertEquals(1,outbox(db,"PENDING"))
        }
    }

    @Test fun repeatedBootRecoveryReenqueuesExactlyOnce() {
        ReminderDatabase(context).use { db ->
            db.add("test",due())
            val job=db.jobs().single()
            db.completeJob(job.id,true)
            assertEquals(1,db.recoverFuture())
            assertEquals(0,db.recoverFuture())
            assertEquals(1,outbox(db,"PENDING"))
        }
    }

    @Test fun failedRegistrationGetsOneRetryNotDuplicateQueue() {
        ReminderDatabase(context).use { db ->
            db.add("test",due())
            val job=db.jobs().single()
            db.completeJob(job.id,false,"PERMISSION_DENIED")
            assertEquals(0,db.reconcileFuture())
            assertEquals(0,db.recoverFuture())
            assertEquals(1,outbox(db,"FAILED"))
        }
    }

    @Test fun obsoleteRevisionCannotBeCompletedAfterSnooze() {
        ReminderDatabase(context).use { db ->
            val id=db.add("snooze",due())
            val old=requireNotNull(db.get(id))
            assertTrue(db.act(id,old.revision,"SNOOZE",snoozeMinutes=5))
            assertFalse(db.act(id,old.revision,"DONE"))
            val current=requireNotNull(db.get(id))
            assertEquals(old.revision+1,current.revision)
            assertEquals("PENDING",current.status)
            assertEquals(1,outbox(db,"PENDING"))
            assertEquals(1,outbox(db,"PENDING","CANCEL"))
        }
    }

    @Test fun archivedAndPausedRemindersNeverRemainRegisterable() {
        ReminderDatabase(context).use { db ->
            val id=db.add("stop",due())
            val initial=requireNotNull(db.get(id))
            val registration=db.jobs().single()
            assertTrue(db.setPaused(id,initial.revision,true))
            assertFalse(db.currentJob(registration))
            val paused=requireNotNull(db.get(id))
            assertTrue(db.archive(id,paused.revision))
            assertFalse(db.currentJob(registration))
            assertEquals(0,db.reconcileFuture(force=true))
        }
    }

    @Test fun recurringOccurrencesHaveIndependentStateAndPauseAll() {
        ReminderDatabase(context).use { db ->
            val series=db.addRecurring("routine",due(),RecurrenceSchedule.DAILY,0)
            val ids=db.pendingOccurrenceIdsForRoutine(series)
            assertEquals(7,ids.size)
            val first=requireNotNull(db.get(ids.first()))
            assertTrue(db.act(first.id,first.revision,"DONE"))
            assertEquals("DONE",requireNotNull(db.get(first.id)).status)
            assertEquals(6,db.pendingOccurrenceIdsForRoutine(series).size)
            assertTrue(db.setRoutinePaused(series,true))
            assertEquals(0,db.reconcileFuture(force=true))
            val remaining=db.pendingOccurrenceIdsForRoutine(series)
            assertEquals(6,remaining.size)
            assertTrue(remaining.all { requireNotNull(db.get(it)).paused })
        }
    }
}
