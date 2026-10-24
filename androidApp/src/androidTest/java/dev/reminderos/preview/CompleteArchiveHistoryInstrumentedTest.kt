package dev.reminderos.preview

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Test package only. Creates/deletes fixture DB, never touches a user's install. */
@RunWith(AndroidJUnit4::class)
class CompleteArchiveHistoryInstrumentedTest {
    private lateinit var context: Context
    @Before fun before() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("reminders_alpha.db")
    }
    @After fun after() { context.deleteDatabase("reminders_alpha.db") }

    @Test fun archiveAndHistoryKeepExplicitOutcomesAndOriginalDue() {
        ReminderDatabase(context).use { db ->
            val future = System.currentTimeMillis() + 86_400_000L
            val doneId = db.add("Reading done", future)
            val archivedId = db.add("Walk archived", future + 60_000L)
            val futureId = db.add("Future untouched", future + 120_000L)
            val oldDue = db.get(doneId)!!.originalDueAt
            assertTrue(db.act(doneId, db.get(doneId)!!.revision, "DONE"))
            assertTrue(db.archive(archivedId, db.get(archivedId)!!.revision))

            val history = db.historyPage(archiveOnly = false, now = System.currentTimeMillis())
            assertTrue(history.any { it.id == doneId && it.status == "DONE" &&
                it.originalDueAt == oldDue })
            assertTrue(history.any { it.id == archivedId && it.archived })
            assertFalse(history.any { it.id == futureId })
            assertEquals(listOf(archivedId),
                db.historyPage(archiveOnly = true).map { it.id })
            assertEquals(listOf(doneId),
                db.historyPage(archiveOnly = false, outcome = "DONE").map { it.id })
            assertTrue(db.recentEvents(archivedId).any { it.kind == "ARCHIVED" })
        }
    }

    @Test fun archivedRecurringSlotsAndLiteralSearchAreAvailableWithoutMutation() {
        ReminderDatabase(context).use { db ->
            val now = System.currentTimeMillis()
            val series = db.addRecurring("Archive 50%_test", now + 86_400_000L,
                "DAILY", 0)
            val instances = db.all().filter { it.seriesId == series }
            assertTrue(instances.size >= 2)
            assertTrue(db.archiveRoutine(series))
            val archived = db.historyPage(true, search = "50%_test",
                limit = 5, offset = 0)
            assertTrue(archived.isNotEmpty())
            assertTrue(archived.all { it.archived && it.seriesId == series })
            assertEquals(emptyList<ReminderRecord>(),
                db.historyPage(true, search = "50X_test"))
            assertTrue(db.archivedRules().any { it.id == series })
            assertTrue(db.historyPage(true, outcome = "UNKNOWN").isNotEmpty())
        }
    }

    @Test fun pagingIsDeterministicAndBounded() {
        ReminderDatabase(context).use { db ->
            val now = System.currentTimeMillis()
            repeat(29) { index ->
                val id = db.add("Entry " + index, now + 86_400_000L + index * 1000L)
                assertTrue(db.archive(id, db.get(id)!!.revision))
            }
            val first = db.historyPage(true, limit = 15)
            val second = db.historyPage(true, limit = 15, offset = 15)
            assertEquals(15, first.size)
            assertEquals(14, second.size)
            assertEquals(29, (first + second).map { it.id }.distinct().size)
            assertEquals(first, db.historyPage(true, limit = 15))
            assertTrue(first.first().originalDueAt >= first.last().originalDueAt)
        }
    }
}
