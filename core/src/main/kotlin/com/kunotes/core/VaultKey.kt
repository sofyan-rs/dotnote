/**
 * The unlocked vault key: the secret that encrypts and decrypts locked notes (`Name.md.age`).
 * Get one from [Lock.createPassword], [Lock.unlock] or [Lock.changePassword]. Port of desktop's
 * `VaultKey` in `lock.rs`.
 */
package com.kunotes.core

import kage.Age
import kage.crypto.x25519.X25519Identity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Keep it only in memory, and only while notes are unlocked. Never log it or save it in plain text.
 *
 * Under the hood it is an [age](https://age-encryption.org) X25519 identity, so locked notes can
 * also be opened with the `age` command-line tool.
 */
class VaultKey internal constructor(
    private val identity: X25519Identity,
) {
    /** Encrypts note text. The result is ASCII-armored, so git sees text, not binary. */
    fun encrypt(text: String): ByteArray =
        try {
            AgeFormat.encryptArmored(identity.recipient(), text.toByteArray(Charsets.UTF_8))
        } catch (error: Exception) {
            throw CoreError.Lock("Couldn't encrypt the note: ${error.message}")
        }

    /** Decrypts the bytes of a locked note back to its text. */
    fun decrypt(bytes: ByteArray): String {
        val plain =
            try {
                AgeFormat.decrypt(identity, bytes)
            } catch (_: Exception) {
                // Any failure here (wrong key, damaged file) means we can't open it with this key.
                throw CoreError.Lock("This note was locked with another vault password.")
            }
        return AgeFormat.decodeUtf8(plain)
            ?: throw CoreError.Lock("The unlocked note isn't valid text.")
    }

    /** The secret key text (`AGE-SECRET-KEY-1...`), only for saving it encrypted in the key file. */
    internal fun secretText(): String = identity.encodeToString()

    companion object {
        /** A new random vault key. */
        internal fun generate(): VaultKey = VaultKey(X25519Identity.new())

        /** Reads a key from its secret text, or returns `null` if the text isn't a key. */
        internal fun parse(secretText: String): VaultKey? =
            try {
                VaultKey(X25519Identity.decode(secretText.trim()))
            } catch (_: Exception) {
                null
            }
    }
}

/** Small wrappers around the kage library, so the rest of the code doesn't deal with streams. */
internal object AgeFormat {
    /** Encrypts [plain] to [recipient] and returns the ASCII-armored age file. */
    fun encryptArmored(
        recipient: kage.Recipient,
        plain: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        // The last argument asks kage to armor the output ("-----BEGIN AGE ENCRYPTED FILE-----").
        Age.encryptStream(listOf(recipient), ByteArrayInputStream(plain), output, true)
        return output.toByteArray()
    }

    /** Decrypts an age file (armored or binary; kage detects which). Throws if [identity] can't open it. */
    fun decrypt(
        identity: kage.Identity,
        encrypted: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        Age.decryptStream(listOf(identity), ByteArrayInputStream(encrypted), output)
        return output.toByteArray()
    }

    /**
     * Decodes UTF-8 strictly, like Rust's `String::from_utf8`: returns `null` for invalid bytes
     * instead of quietly replacing them with `�` (which `String(bytes)` would do).
     */
    fun decodeUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
}
