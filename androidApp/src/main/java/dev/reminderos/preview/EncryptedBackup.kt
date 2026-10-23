package dev.reminderos.preview

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based portable backup container V1:
 * "RPOSB1" (6 bytes) | random 16-byte salt | random 12-byte nonce | AES-GCM ciphertext+tag.
 *
 * The authenticated header is bound as AAD. Never write plaintext to disk.
 * Format version/algorithm are intentionally strict (no silent fallbacks).
 *
 * User passwords are not stored and cannot be recovered. A 12+ character
 * passphrase is required for export. Wrong passwords and corrupted files fail
 * authentication without altering the SQLite database.
 */
internal object EncryptedBackup {
    private val magic = "RPOSB1".toByteArray(StandardCharsets.US_ASCII)
    private val portableMagic = "RPOSB2".toByteArray(StandardCharsets.US_ASCII)
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    const val MAX_FILE_BYTES = MAX_PLAINTEXT_BYTES + 4096
    private val random = SecureRandom()

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") }
            finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray =
        encryptWithMagic(plaintext, password, magic)

    fun encryptPortable(plaintext: ByteArray, password: CharArray): ByteArray =
        encryptWithMagic(plaintext, password, portableMagic)

    private fun encryptWithMagic(plaintext: ByteArray, password: CharArray, magic: ByteArray): ByteArray {
        require(password.size >= 12) { "Use a passphrase of at least 12 characters" }
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES) { "Backup data size is unsupported" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = ByteBuffer.allocate(magic.size + SALT + NONCE)
            .put(magic).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return header + ciphertext
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray =
        decryptWithMagic(data, password, magic)

    fun decryptPortable(data: ByteArray, password: CharArray): ByteArray =
        decryptWithMagic(data, password, portableMagic)

    private fun decryptWithMagic(data: ByteArray, password: CharArray, magic: ByteArray): ByteArray {
        require(data.size in (magic.size + SALT + NONCE + 16)..MAX_FILE_BYTES) {
            "Backup file is empty, truncated or too large"
        }
        require(password.isNotEmpty()) { "Enter the backup passphrase" }
        val headerLen = magic.size + SALT + NONCE
        val header = data.copyOfRange(0, headerLen)
        require(header.copyOfRange(0, magic.size).contentEquals(magic)) {
            "This is not a supported Reminder OS encrypted backup"
        }
        val salt = header.copyOfRange(magic.size, magic.size + SALT)
        val nonce = header.copyOfRange(magic.size + SALT, headerLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        try {
            val plain = cipher.doFinal(data, headerLen, data.size - headerLen)
            require(plain.size <= MAX_PLAINTEXT_BYTES) { "Decoded backup too large" }
            return plain
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect passphrase or damaged backup", e)
        }
    }
}
