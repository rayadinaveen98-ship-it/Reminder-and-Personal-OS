package dev.reminderos.preview

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class EncryptedBackupTest {
    private val password = "Correct Horse Battery Staple 2026".toCharArray()
    private fun payload() = """{"backup_format":1,"reminders":[],"events":[]}""".toByteArray()

    @Test fun roundTripPreservesExactBytes() {
        val data=payload()
        assertArrayEquals(data,EncryptedBackup.decrypt(EncryptedBackup.encrypt(data,password),password))
    }

    @Test fun wrongPassphraseCannotDecrypt() {
        val ciphertext=EncryptedBackup.encrypt(payload(),password)
        try {
            EncryptedBackup.decrypt(ciphertext,"wrong but sufficiently long password".toCharArray())
            fail("Incorrect password must never succeed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("passphrase") || expected.message!!.contains("damaged"))
        }
    }

    @Test fun authenticatedCiphertextTamperingFails() {
        val data=EncryptedBackup.encrypt(payload(),password)
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        try {
            EncryptedBackup.decrypt(data,password)
            fail("Tampered data should fail authenticated decryption")
        } catch (expected: IllegalArgumentException) { /* intentionally rejected */ }
    }

    @Test fun authenticatedHeaderTamperingFails() {
        val data=EncryptedBackup.encrypt(payload(),password)
        data[7]=(data[7].toInt() xor 2).toByte() // salt changed
        try {
            EncryptedBackup.decrypt(data,password)
            fail("Tampered header should fail")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun independentExportsUseRandomSaltAndNonce() {
        val a=EncryptedBackup.encrypt(payload(),password)
        val b=EncryptedBackup.encrypt(payload(),password)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(payload(),EncryptedBackup.decrypt(a,password))
        assertArrayEquals(payload(),EncryptedBackup.decrypt(b,password))
    }

    @Test fun rejectsUnsupportedHeader() {
        val data=EncryptedBackup.encrypt(payload(),password)
        data[0]='Z'.code.toByte()
        try {
            EncryptedBackup.decrypt(data,password)
            fail("Wrong format should not be accepted")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun rejectsShortPassphraseOnExport() {
        try {
            EncryptedBackup.encrypt(payload(),"12345678901".toCharArray())
            fail("Short passphrase must be refused")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun rejectsOversizeFileBeforeWork() {
        val huge=ByteArray(EncryptedBackup.MAX_FILE_BYTES + 1)
        try {
            EncryptedBackup.decrypt(huge,password)
            fail("Oversized backup must be rejected")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun rejectsTruncatedFile() {
        val data=EncryptedBackup.encrypt(payload(),password)
        try {
            EncryptedBackup.decrypt(data.copyOfRange(0,12),password)
            fail("Truncated file must be rejected")
        } catch (expected: IllegalArgumentException) { }
    }

    @Test fun rejectsEmptyPlaintext() {
        try {
            EncryptedBackup.encrypt(ByteArray(0),password)
            fail("Empty payload not valid")
        } catch (expected: IllegalArgumentException) { }
    }
}
