/**
 * Keeps the git sign-in (HTTPS user name + personal access token) of each synced vault
 * (SPEC 7.1 "Sign-in", ARCHITECTURE §9.2).
 *
 * The token is a password, so it is never stored in plain text and never logged:
 * - an AES key lives in the Android Keystore (the key never leaves the secure hardware/OS store);
 * - each vault's sign-in is encrypted with that key (AES-GCM) and written to
 *   `files/git-sign-in/<vaultId>.bin` in app storage.
 *
 * `EncryptedSharedPreferences` would do the same, but it is deprecated, so we use the Keystore directly.
 */
package com.kunotes.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kunotes.core.GitCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(
    context: Context,
) {
    private val folder = File(context.applicationContext.filesDir, "git-sign-in")

    /** The sign-in saved for [vaultId], or `null` if there is none (or it can't be decrypted). */
    suspend fun load(vaultId: String): GitCredentials? =
        withContext(Dispatchers.IO) {
            val file = fileFor(vaultId)
            if (!file.exists()) return@withContext null
            try {
                val bytes = file.readBytes()
                if (bytes.size < 2) return@withContext null
                // File layout: [1 byte IV length][IV][encrypted JSON].
                val ivLength = bytes[0].toInt()
                val iv = bytes.copyOfRange(1, 1 + ivLength)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                val json = cipher.doFinal(bytes, 1 + ivLength, bytes.size - 1 - ivLength).decodeToString()
                val saved = Json.decodeFromString(SavedSignIn.serializer(), json)
                GitCredentials(saved.username, saved.token)
            } catch (error: GeneralSecurityException) {
                // E.g. the Keystore key was wiped (screen lock removed on some phones): ask again.
                android.util.Log.w(TAG, "Saved git sign-in can't be decrypted; it must be entered again (${error.javaClass.simpleName})")
                null
            } catch (error: IllegalArgumentException) {
                // A damaged file (bad JSON, bad IV length): ask again. Only the error's type is
                // logged: a JSON error message could quote the decrypted text, token included.
                android.util.Log.w(TAG, "Saved git sign-in is damaged; it must be entered again (${error.javaClass.simpleName})")
                null
            } catch (error: IndexOutOfBoundsException) {
                android.util.Log.w(TAG, "Saved git sign-in is damaged; it must be entered again (${error.javaClass.simpleName})")
                null
            }
        }

    /** Saves (or replaces) the sign-in of [vaultId]. */
    suspend fun save(
        vaultId: String,
        credentials: GitCredentials,
    ) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore picks a fresh random IV for every encryption; we store it with the data.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = Json.encodeToString(SavedSignIn.serializer(), SavedSignIn(credentials.username, credentials.token))
        val encrypted = cipher.doFinal(json.toByteArray())
        val iv = cipher.iv
        folder.mkdirs()
        // Write to a temporary file, then rename: a crash never leaves half a file.
        val file = fileFor(vaultId)
        val temp = File(folder, "${file.name}.tmp")
        temp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("Couldn't save the git sign-in")
        }
    }

    /** Forgets the sign-in of every vault not in [vaultIds] (e.g. after a vault is removed). */
    suspend fun keepOnly(vaultIds: Set<String>) =
        withContext(Dispatchers.IO) {
            folder.listFiles()?.forEach { file ->
                if (file.name.removeSuffix(".bin") !in vaultIds) file.delete()
            }
        }

    /** Vault ids are random UUIDs; keep only safe characters anyway, so the name is a plain file. */
    private fun fileFor(vaultId: String) = File(folder, vaultId.filter { it.isLetterOrDigit() || it == '-' } + ".bin")

    /** Gets the app's AES key from the Keystore, creating it the first time. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /** What is encrypted on disk. */
    @Serializable
    private data class SavedSignIn(
        val username: String,
        val token: String,
    ) {
        override fun toString(): String = "SavedSignIn(username=$username, token=***)"
    }

    private companion object {
        const val TAG = "TokenStore"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kunotes-git-sign-in"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
