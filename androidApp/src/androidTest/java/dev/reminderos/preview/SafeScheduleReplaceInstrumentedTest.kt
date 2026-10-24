package dev.reminderos.preview

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Destructive *only to a test-package database* and never the user's app. */
@RunWith(AndroidJUnit4::class)
class SafeScheduleReplaceInstrumentedTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("reminders_alpha.db")
    }
    @After fun cleanup() { context.deleteDatabase("reminders_alpha.db") }

    @Test fun replacementKeepsOldOutcomeAndCreatesDistinctFutureSeries() {
        ReminderDatabase(context).use { db ->
            val tomorrow = System.currentTimeMillis() + 86_400_000L
            val oldId = db.addRecurring("Old plan", tomorrow, "DAILY", 0)
            val old = db.rule(oldId)!!
            val before = db.all().filter { it.seriesId == oldId }
            assertTrue(before.isNotEmpty())
            val completed = before.first()
            assertTrue(db.act(completed.id, completed.revision, "DONE"))
            val replacedId = db.replaceRoutine(
                old, "New plan", tomorrow + 3_600_000L, "WEEKLY", 31
            )
            assertNotNull(replacedId)
            assertNotEquals(oldId, replacedId)
            val original = db.get(completed.id)!!
            assertEquals("DONE", original.status)
            assertTrue(original.archived)
            assertTrue(db.recentEvents(completed.id).any { it.kind == "DONE" })
            assertTrue(db.recentEvents(completed.id).any { it.kind == "RULE_REPLACED" })
            assertEquals(1, db.archivedRules().count { it.id == oldId })
            assertEquals(1, db.rules().count { it.id == replacedId })
            assertTrue(db.all().any { it.seriesId == replacedId && it.status == "PENDING" })
            assertTrue(db.routineHistory(oldId).contains("DONE"))
        }
    }

    @Test fun staleOrInvalidReplacementDoesNotChangeOriginal() {
        ReminderDatabase(context).use { db ->
            val future = System.currentTimeMillis() + 172_800_000L
            val id = db.addRecurring("Original", future, "WEEKLY", 31)
            val expected = db.rule(id)!!
            assertNull(db.replaceRoutine(expected, "", future, "DAILY", 0))
            assertNull(db.replaceRoutine(expected, "Bad weekly", future, "WEEKLY", 0))
            assertEquals(expected, db.rule(id))
            assertTrue(db.setRoutinePaused(id, true))
            // No rule revision exists in schema v3; full snapshot protects edits.
            assertNull(db.replaceRoutine(expected, "Stale", future, "DAILY", 0))
            assertEquals(0, db.archivedRules().size)
            assertEquals(1, db.rules().size)
        }
    }

    @Test fun pausedReplacementStaysPausedAndKeepsOriginalHistory() {
        ReminderDatabase(context).use { db ->
            val future = System.currentTimeMillis() + 86_400_000L
            val id = db.addRecurring("Paused old", future, "DAILY", 0)
            assertTrue(db.setRoutinePaused(id, true))
            val source = db.rule(id)!!
            val newId = db.replaceRoutine(source, "Paused new", future, "MONTHLY", 0)!!
            assertTrue(db.rule(newId)!!.paused)
            assertTrue(db.archivedRules().any { it.id == id })
            assertTrue(db.all().none { it.seriesId == newId })
            assertTrue(db.setRoutinePaused(newId, false))
            db.ensureRecurring()
            assertTrue(db.all().any { it.seriesId == newId && !it.archived })
        }
    }
}
