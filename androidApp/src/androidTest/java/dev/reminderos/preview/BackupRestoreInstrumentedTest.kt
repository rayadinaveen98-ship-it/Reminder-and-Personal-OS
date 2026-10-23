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
 * Real Android SQLite integration tests on an isolated CI emulator.
 * No user data: every test starts with an empty synthetic test database.
 * Does not assert system alarm presentation or Android file picker UX.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    private lateinit var context: Context
    private val databaseName = "reminders_alpha.db"
    private val passphrase = "Test-only passphrase 2026".toCharArray()

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "Unexpected database from another process/test",
            context.deleteDatabase(databaseName) || !context.getDatabasePath(databaseName).exists()
        )
    }

    @After fun cleanup() {
        context.deleteDatabase(databaseName)
        passphrase.fill('\u0000')
    }

    private fun seed(): Int {
        ReminderDatabase(context).use { db ->
            val start = System.currentTimeMillis() + 12 * 60 * 60 * 1000L
            val one = db.add("Walk dog", start)
            val row = requireNotNull(db.get(one))
            assertTrue(db.act(one, row.revision, "DONE"))
            db.addRecurring("Hydrate", start, RecurrenceSchedule.DAILY, 0)
            assertEquals(1, db.rules().size)
            return countRows(db)
        }
    }

    @Test fun actualSqliteExportVerifyEmptyRestoreRoundTrip() {
        val before = seed()
        val file = ReminderBackupRepository(context).useRepo { backup ->
            val data = backup.exportEncrypted(passphrase)
            assertTrue(backup.inspectEncrypted(data, passphrase).contains("Valid encrypted backup"))
            data
        }
        assertTrue(context.deleteDatabase(databaseName))
        ReminderBackupRepository(context).useRepo { fresh ->
            assertFalse(fresh.hasUserRecords())
            val count = fresh.importEncrypted(file, passphrase)
            assertEquals(before, count)
            assertTrue(fresh.hasUserRecords())
        }
        ReminderDatabase(context).use { db ->
            assertEquals(1, db.rules().size)
            assertEquals(before, countRows(db))
            assertEquals(1, db.all().count { it.status == "DONE" })
            assertTrue(db.all().any { it.seriesId != null })
            assertEquals(0, db.jobs().size) // Native registrations intentionally excluded.
            assertTrue(db.recoverFuture() > 0)
            assertTrue(db.jobs().isNotEmpty())
        }
        file.fill(0)
    }

    @Test fun wrongPasswordDoesNotChangeExistingDatabase() {
        seed()
        val file = ReminderBackupRepository(context).useRepo { it.exportEncrypted(passphrase) }
        val before = ReminderDatabase(context).use { it.all().size }
        ReminderBackupRepository(context).useRepo { backup ->
            try {
                backup.importEncrypted(file, "Incorrect-passphrase-for-QA".toCharArray())
                fail("Wrong password must never restore")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("passphrase") ||
                    expected.message!!.contains("damaged"))
            }
        }
        assertEquals(before, ReminderDatabase(context).use { it.all().size })
        file.fill(0)
    }

    @Test fun populatedDatabaseCannotBeOverwritten() {
        seed()
        val file = ReminderBackupRepository(context).useRepo { it.exportEncrypted(passphrase) }
        val before = ReminderDatabase(context).use { it.all().size }
        ReminderBackupRepository(context).useRepo { backup ->
            try {
                backup.importEncrypted(file, passphrase)
                fail("Restore into non-empty DB must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("already contains"))
            }
        }
        assertEquals(before, ReminderDatabase(context).use { it.all().size })
        file.fill(0)
    }

    private fun countRows(db: ReminderDatabase): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM reminders", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private inline fun <T> ReminderBackupRepository.useRepo(
        action: (ReminderBackupRepository) -> T
    ): T = try { action(this) } finally { close() }
}
