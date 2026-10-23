package dev.reminderos.preview

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RPOSB2 fixture independently generated using Python hashlib.pbkdf2_hmac
 * and cryptography AESGCM. These are synthetic reminders and test-only keys.
 */
@RunWith(AndroidJUnit4::class)
class PortableEncryptedBackupInstrumentedTest {
    private val password="Portable test password 2026".toCharArray()
    private val fixture=Base64.decode(
        "UlBPU0IyAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG1x4GCFNhRHr9qELisfDotqvh9PfeoIYMPgfzQTDA3nug0Ro0VVthqOAfCdvlOX9fut2S76NUGjBlvG33KCHnWIfgOM2Pk2Ob4jgiSrWxHu4qjC7SUhZ/EIpzAHNzM/ajtXK1Q243qpQHopO8JRYMlaw4byBVTK9AyA3lnzqHlwRNm1YKDqJ2T/96J8PiNXWGXhchSzl+Mhbcjd80b5L4id3SROC8hTc3kKPlfDMzWuY3vB+Q50bc64Cfnpb6VL+QrdHPaxYs3BV3bWaRomHh4/PdbBqiY7SB8jS8j04oGMBK2JFNHt6yjz2/loSkTiV5l2wS0kbVrnRklT7dNZ1qzmZ7NKnS6EQJxTIJ3V/IdCGKsZd51RiuGFs0Q==",Base64.DEFAULT
    )
    private lateinit var context: Context

    @Before fun emptyDatabase() {
        context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.deleteDatabase("reminders_alpha.db") ||
            !context.getDatabasePath("reminders_alpha.db").exists())
    }
    @After fun cleanup() {
        context.deleteDatabase("reminders_alpha.db")
        password.fill('\u0000')
    }

    @Test fun independentPythonFixtureDecryptsAndPreservesUnknown() {
        val clear=EncryptedBackup.decryptPortable(fixture,password)
        val snapshot=PortableEncryptedTransfer.decode(clear)
        assertEquals(1,snapshot.reminders.size)
        assertEquals("UNKNOWN",snapshot.reminders.single().outcome.name)
        assertEquals("Test reminder",snapshot.reminders.single().title)
        assertEquals(0,snapshot.events.size)
        clear.fill(0)
    }

    @Test fun wrongPasswordTamperAndLegacyDowngradeNeverAuthenticate() {
        val altered=fixture.copyOf()
        altered[altered.lastIndex]=(altered.last().toInt() xor 1).toByte()
        try {
            EncryptedBackup.decryptPortable(altered,password)
            fail("Authenticated encryption must reject corrupt payload")
        } catch (_: IllegalArgumentException) { }
        try {
            EncryptedBackup.decryptPortable(fixture,"Bad incorrect password".toCharArray())
            fail("Incorrect password must reject backup")
        } catch (_: IllegalArgumentException) { }
        try {
            EncryptedBackup.decrypt(fixture,password)
            fail("Legacy RPOSB1 must not read RPOSB2")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun actualAndroidSqlitePortableExportDoesNotChangeUserData() {
        ReminderDatabase(context).use { db ->
            val future=System.currentTimeMillis()+86_400_000
            val id=db.add("Persist me",future)
            assertTrue(db.act(id,1,"DONE"))
            db.addRecurring("Repeat me",future,RecurrenceSchedule.DAILY,0)
            val before=AndroidPortableStorageInspector(db).inspect()
            val exported=PortableEncryptedTransfer(db).exportEncrypted(password)
            try {
                val text=PortableEncryptedTransfer.inspectEncrypted(exported,password)
                assertTrue(text.contains("Verified cross-platform encrypted backup"))
                assertEquals(before,AndroidPortableStorageInspector(db).inspect())
                val data=EncryptedBackup.decryptPortable(exported,password)
                try {
                    val restored=PortableEncryptedTransfer.decode(data)
                    assertEquals(before.reminderCount,restored.reminders.size)
                    assertEquals(before.eventCount,restored.events.size)
                    assertTrue(restored.reminders.any { it.outcome.name=="DONE" })
                } finally { data.fill(0) }
            } finally { exported.fill(0) }
        }
    }
}
