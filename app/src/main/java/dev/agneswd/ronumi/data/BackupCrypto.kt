package dev.agneswd.ronumi.data

import dev.agneswd.ronumi.R
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password encryption. Version 1 fixes the algorithms and KDF cost; files cannot raise it. */
object BackupCrypto {
    const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
    // STLPBAK! is the Stillpoint backup header. Ronumi accepts those files.
    private val magic = "STLPBAK!".encodeToByteArray()
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + NONCE_BYTES
    const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + HEADER_BYTES + TAG_BYTES
    // OWASP recommends 600,000 iterations for PBKDF2-HMAC-SHA256.
    private const val ITERATIONS = 600_000
    private val INVALID = R.string.backup_error_authentication

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        requireBackup(plaintext.size <= MAX_PLAINTEXT_BYTES) { R.string.backup_error_size }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).put(VERSION.toByte())
            .putInt(plaintext.size).put(salt).put(nonce).array()
        return header + crypt(Cipher.ENCRYPT_MODE, plaintext, password, salt, nonce, header)
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        requireBackup(encrypted.size <= MAX_ENCRYPTED_BYTES) { R.string.backup_error_size }
        requireBackup(encrypted.size >= magic.size && encrypted.copyOfRange(0, magic.size).contentEquals(magic)) {
            R.string.backup_error_format
        }
        requireBackup(encrypted.size > magic.size) { INVALID }
        requireBackup(encrypted[magic.size].toInt() == VERSION) { R.string.backup_error_encrypted_version }
        requireBackup(encrypted.size >= HEADER_BYTES + TAG_BYTES) { INVALID }
        val header = encrypted.copyOfRange(0, HEADER_BYTES)
        val parsed = ByteBuffer.wrap(header).apply { position(magic.size + 1) }
        val plaintextSize = parsed.int
        requireBackup(plaintextSize in 0..MAX_PLAINTEXT_BYTES && encrypted.size == HEADER_BYTES + plaintextSize + TAG_BYTES) { INVALID }
        val salt = ByteArray(SALT_BYTES).also(parsed::get)
        val nonce = ByteArray(NONCE_BYTES).also(parsed::get)
        return try {
            crypt(Cipher.DECRYPT_MODE, encrypted.copyOfRange(HEADER_BYTES, encrypted.size), password, salt, nonce, header)
        } catch (_: BadPaddingException) {
            throw BackupTextException(INVALID)
        } catch (_: IllegalBlockSizeException) {
            throw BackupTextException(INVALID)
        }
    }

    private fun crypt(mode: Int, input: ByteArray, password: CharArray, salt: ByteArray, nonce: ByteArray, header: ByteArray): ByteArray {
        requireBackup(password.size in 1..1024) { R.string.backup_error_password_length }
        val copy = password.copyOf()
        val spec = PBEKeySpec(copy, salt, ITERATIONS, 256)
        copy.fill('\u0000')
        val keyBytes = try {
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            try { derived.encoded } finally { runCatching { derived.destroy() } }
        } finally {
            spec.clearPassword()
        }
        val key = SecretKeySpec(keyBytes, "AES")
        keyBytes.fill(0)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
                updateAAD(header)
                doFinal(input)
            }
        } finally {
            // Providers can retain internal key copies. Clear those we own and request destruction.
            runCatching { key.destroy() }
        }
    }
}
