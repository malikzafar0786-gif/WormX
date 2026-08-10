package com.wormx.app.vault

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Same encryption approach as the standalone File Safe app: AES-256-GCM for
 * file contents, with the PIN/biometric gate backed by EncryptedSharedPreferences
 * so the key material never sits in plaintext prefs.
 */
class VaultCryptoManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "wormx_vault_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Returns the existing per-vault file key, generating one on first use. */
    private fun fileEncryptionKey(): SecretKey {
        val existing = securePrefs.getString(KEY_FILE_SECRET, null)
        if (existing != null) {
            val bytes = Base64.decode(existing, Base64.NO_WRAP)
            return SecretKeySpec(bytes, "AES")
        }
        val newKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        securePrefs.edit()
            .putString(KEY_FILE_SECRET, Base64.encodeToString(newKey.encoded, Base64.NO_WRAP))
            .apply()
        return newKey
    }

    fun setPinHash(pin: String) {
        securePrefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean =
        securePrefs.getString(KEY_PIN_HASH, null) == hash(pin)

    fun isPinSet(): Boolean = securePrefs.contains(KEY_PIN_HASH)

    // ---------- Decoy vault ----------
    // A second, independent PIN that opens an empty/dummy vault view instead of
    // the real one. Useful if someone pressures the user to unlock the app.

    fun setDecoyPinHash(pin: String) {
        securePrefs.edit().putString(KEY_DECOY_PIN_HASH, hash(pin)).apply()
    }

    fun isDecoyPinSet(): Boolean = securePrefs.contains(KEY_DECOY_PIN_HASH)

    fun verifyDecoyPin(pin: String): Boolean =
        isDecoyPinSet() && securePrefs.getString(KEY_DECOY_PIN_HASH, null) == hash(pin)

    // ---------- Break-in detection ----------
    // Counts consecutive failed PIN attempts; the caller decides what to do
    // with the count (e.g. show a warning, add a short lockout delay).

    fun recordFailedAttempt(): Int {
        val count = securePrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        securePrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, count)
            .putLong(KEY_LAST_FAILED_AT, System.currentTimeMillis())
            .apply()
        return count
    }

    fun clearFailedAttempts() {
        securePrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
    }

    fun failedAttemptCount(): Int = securePrefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    private fun hash(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(digest.digest(pin.toByteArray()), Base64.NO_WRAP)
    }

    /** Encrypts [sourceFile] into [destFile] (prefixed with a random 12-byte IV) and deletes the source. */
    fun encryptFileIntoVault(sourceFile: File, destFile: File) {
        val key = fileEncryptionKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        destFile.parentFile?.mkdirs()
        destFile.outputStream().use { out ->
            out.write(iv)
            sourceFile.inputStream().use { input ->
                val encrypted = cipher.doFinal(input.readBytes())
                out.write(encrypted)
            }
        }
        sourceFile.delete()
    }

    /** Decrypts a vault file into a temporary plaintext file for viewing/exporting. */
    fun decryptFileFromVault(encryptedFile: File, outFile: File) {
        val key = fileEncryptionKey()
        encryptedFile.inputStream().use { input ->
            val iv = ByteArray(12).also { input.read(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(input.readBytes())
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(decrypted)
        }
    }

    companion object {
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_FILE_SECRET = "vault_file_secret"
        private const val KEY_DECOY_PIN_HASH = "vault_decoy_pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "vault_failed_attempts"
        private const val KEY_LAST_FAILED_AT = "vault_last_failed_at"
    }
}
