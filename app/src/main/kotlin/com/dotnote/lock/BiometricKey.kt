/**
 * "Unlock with fingerprint" (ARCHITECTURE §8.3).
 *
 * The **vault key** (not the password) is encrypted ("wrapped") with an AES key that lives in the
 * Android Keystore and can only be used right after a fingerprint (BIOMETRIC_STRONG) matches.
 * The wrapped key is a small file in the app's private storage, one per vault id; the set of
 * vaults with fingerprint on is `Settings.biometricVaults`.
 *
 * Why the vault key and not the password: unlocking skips the slow scrypt step, and it keeps
 * working when the password is changed on another device (the vault key never changes).
 *
 * Adding a new fingerprint to the phone makes Android invalidate the Keystore key. Then we drop
 * the wrapped key and the user unlocks with the password again (and can turn fingerprint on again).
 */
package com.dotnote.lock

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import com.dotnote.core.VaultKey
import com.dotnote.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The result of [BiometricKey.unlock]. */
sealed class FingerprintUnlock {
    data class Unlocked(
        val key: VaultKey,
    ) : FingerprintUnlock()

    /** The user closed the fingerprint dialog. */
    data object Cancelled : FingerprintUnlock()

    /** Fingerprint unlock can't be used (any more); [message] says why. Use the password. */
    data class UsePassword(
        val message: String,
    ) : FingerprintUnlock()
}

class BiometricKey(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext

    /** `noBackupFilesDir`: never copied to cloud backups or other phones (the Keystore key isn't either). */
    private val dir = File(appContext.noBackupFilesDir, "biometric")

    /**
     * Null if fingerprint unlock can be offered on this phone; otherwise a short hint for the
     * disabled checkbox ("Add a fingerprint in Settings to use this.").
     */
    fun unavailableReason(): String? =
        when (BiometricManager.from(appContext).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Add a fingerprint in the phone's settings to use this."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The fingerprint sensor isn't available right now."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "The phone needs a security update for this."
            else -> "This phone has no fingerprint sensor that can protect keys."
        }

    /** True if fingerprint unlock is on for [vaultId] (setting on and a wrapped key saved). */
    suspend fun isEnabled(vaultId: String): Boolean =
        vaultId in settingsStore.current().biometricVaults && withContext(Dispatchers.IO) { file(vaultId).exists() }

    /**
     * Turns fingerprint unlock on for [vaultId]: asks for a fingerprint, then saves [key] wrapped.
     * Returns null on success, or a message to show (also when the user cancels).
     */
    suspend fun enable(
        vaultId: String,
        key: VaultKey,
        authenticator: BiometricAuthenticator,
    ): String? {
        val cipher =
            try {
                withContext(Dispatchers.IO) {
                    Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, newKeystoreKey(vaultId)) }
                }
            } catch (error: GeneralSecurityException) {
                Log.w(TAG, "Couldn't create the fingerprint key", error)
                return "Couldn't turn on fingerprint unlock on this phone."
            }
        return when (val result = authenticator.authenticate("Turn on fingerprint unlock", "Confirm with your fingerprint", cipher)) {
            is BiometricResult.Cancelled -> "Fingerprint unlock wasn't turned on."
            is BiometricResult.Failed -> result.message
            is BiometricResult.Success ->
                try {
                    withContext(Dispatchers.IO) { save(vaultId, result.cipher, key) }
                    settingsStore.update { it.copy(biometricVaults = it.biometricVaults + vaultId) }
                    null
                } catch (error: GeneralSecurityException) {
                    Log.w(TAG, "Couldn't wrap the vault key", error)
                    "Couldn't turn on fingerprint unlock."
                }
        }
    }

    /** Unlocks [vaultId] with a fingerprint: asks for it, then unwraps the saved vault key. */
    suspend fun unlock(
        vaultId: String,
        authenticator: BiometricAuthenticator,
    ): FingerprintUnlock {
        val saved = withContext(Dispatchers.IO) { load(vaultId) } ?: return FingerprintUnlock.UsePassword(NOT_SET_UP)
        val cipher =
            try {
                withContext(Dispatchers.IO) {
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, keystoreKey(vaultId), GCMParameterSpec(GCM_TAG_BITS, saved.iv))
                    }
                }
            } catch (_: KeyPermanentlyInvalidatedException) {
                // A fingerprint was added or removed: Android threw the key away on purpose.
                disable(vaultId)
                return FingerprintUnlock.UsePassword("Fingerprints changed on this phone. Unlock with the password.")
            } catch (error: GeneralSecurityException) {
                Log.w(TAG, "Fingerprint key unusable", error)
                disable(vaultId)
                return FingerprintUnlock.UsePassword(NOT_SET_UP)
            }
        return when (val result = authenticator.authenticate("Unlock notes", "Locked notes in this vault", cipher)) {
            is BiometricResult.Cancelled -> FingerprintUnlock.Cancelled
            is BiometricResult.Failed -> FingerprintUnlock.UsePassword(result.message)
            is BiometricResult.Success -> {
                val key =
                    try {
                        val secret = result.cipher.doFinal(saved.ciphertext)
                        VaultKey.parse(secret.decodeToString()).also { secret.fill(0) }
                    } catch (error: GeneralSecurityException) {
                        Log.w(TAG, "Couldn't unwrap the vault key", error)
                        null
                    }
                if (key == null) {
                    disable(vaultId)
                    FingerprintUnlock.UsePassword(NOT_SET_UP)
                } else {
                    FingerprintUnlock.Unlocked(key)
                }
            }
        }
    }

    /** Turns fingerprint unlock off for [vaultId]: deletes the wrapped key and the Keystore key. */
    suspend fun disable(vaultId: String) {
        withContext(Dispatchers.IO) {
            file(vaultId).delete() // our own app file, not vault content
            try {
                keyStore().deleteEntry(alias(vaultId))
            } catch (error: GeneralSecurityException) {
                Log.w(TAG, "Couldn't delete the fingerprint key", error)
            }
        }
        settingsStore.update { it.copy(biometricVaults = it.biometricVaults - vaultId) }
    }

    // ---- Keystore and file details ----

    /** A wrapped vault key as saved on disk: GCM nonce + encrypted secret. */
    private class Wrapped(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    /** Makes a fresh AES key for [vaultId] (replacing an old one) that needs a strong fingerprint. */
    private fun newKeystoreKey(vaultId: String): SecretKey {
        val builder =
            KeyGenParameterSpec
                .Builder(alias(vaultId), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setUserAuthenticationRequired(true)
                // Adding a fingerprint invalidates the key, so a new finger can't open old notes.
                .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 0 seconds = a fingerprint is needed for every single use (through CryptoObject).
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION") // the pre-Android-11 way to say "every use"
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun keystoreKey(vaultId: String): SecretKey =
        keyStore().getKey(alias(vaultId), null) as? SecretKey
            ?: throw KeyPermanentlyInvalidatedException("No fingerprint key")

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    /** Encrypts the key with the fingerprint-unlocked [cipher] and saves it (temp file + rename). */
    private fun save(
        vaultId: String,
        cipher: Cipher,
        key: VaultKey,
    ) {
        val secret = key.secretText().toByteArray()
        val ciphertext = cipher.doFinal(secret)
        secret.fill(0)
        val iv = cipher.iv
        dir.mkdirs()
        val temp = File(dir, "$vaultId.tmp")
        temp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + ciphertext)
        if (!temp.renameTo(file(vaultId))) throw GeneralSecurityException("Couldn't save the wrapped key")
    }

    private fun load(vaultId: String): Wrapped? {
        val bytes = file(vaultId).takeIf { it.exists() }?.readBytes() ?: return null
        val ivLength = bytes.firstOrNull()?.toInt() ?: return null
        if (ivLength <= 0 || bytes.size <= 1 + ivLength) return null
        return Wrapped(iv = bytes.copyOfRange(1, 1 + ivLength), ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size))
    }

    private fun file(vaultId: String) = File(dir, "$vaultId.key")

    private fun alias(vaultId: String) = "dotnote-vault-$vaultId"

    private companion object {
        const val TAG = "BiometricKey"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val NOT_SET_UP = "Fingerprint unlock is off for this vault. Unlock with the password."
    }
}
