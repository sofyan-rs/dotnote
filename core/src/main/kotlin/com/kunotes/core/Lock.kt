/**
 * Locked notes: notes encrypted on disk, so a credential written in a note isn't readable in the
 * vault folder or its git repository. Port of desktop's `lock.rs`; the files are shared with
 * desktop, so the format must stay exactly the same.
 */
package com.kunotes.core

import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient

/**
 * How it works (same as desktop):
 * - A locked note is `Name.md.age`, in the [age](https://age-encryption.org) format, so it can
 *   also be opened without KuNotes.
 * - Each vault has one password. It protects the vault key: a random age key stored, encrypted
 *   with the password, in `.kunotes-lock.age` at the vault root. Notes are encrypted with that key.
 *   Typing the password unlocks the key once (slow on purpose, so passwords are hard to guess);
 *   after that, opening and saving locked notes is fast.
 * - Forgetting the password means the locked notes can't be opened. Deleting `.kunotes-lock.age`
 *   has the same effect.
 *
 * Every function reads or writes files and some are slow (scrypt), so call them off the main thread.
 */
object Lock {
    /** The encrypted vault key, at the vault root (hidden, but synced with git). */
    val KEY_FILE = VaultPath(".kunotes-lock.age")

    /** Passwords shorter than this (in characters) are refused when setting a password. */
    const val MIN_PASSWORD_LEN = 8

    /** The error text of [unlock] when the password is wrong. */
    private const val WRONG_PASSWORD = "Wrong password."

    /**
     * How hard scrypt works when we save the key, as a power of two: 2^n steps and 2^n KiB of
     * memory (18 → 256 MiB). Callers pass it to [createPassword] / [changePassword]; the app picks
     * a value the phone's memory allows (see ARCHITECTURE §8.1).
     */
    const val DEFAULT_SCRYPT_WORK_FACTOR = 18

    /**
     * The hardest scrypt setting we accept when unlocking. Desktop picks its setting by timing the
     * computer (about one second), so a fast computer can go above 18. Each step up doubles the
     * memory; 2^20 needs 1 GiB, which is about the most a phone can give.
     */
    private const val MAX_SCRYPT_WORK_FACTOR = 20

    /** True if the vault already has a password (a key file). */
    fun hasPassword(fs: VaultFs): Boolean = fs.exists(KEY_FILE)

    /**
     * Sets the vault password: makes a new vault key and saves it encrypted with [password].
     * Refuses if the vault already has one (that would make its locked notes unreadable).
     */
    fun createPassword(
        fs: VaultFs,
        password: String,
        workFactor: Int = DEFAULT_SCRYPT_WORK_FACTOR,
    ): VaultKey {
        if (hasPassword(fs)) throw CoreError.Lock("This vault already has a password.")
        checkNewPassword(password)
        val key = VaultKey.generate()
        saveKey(fs, key, password, workFactor)
        return key
    }

    /**
     * Changes the vault password from [current] to [new]. The vault key stays the same, so locked
     * notes don't change: only `.kunotes-lock.age` is rewritten. Returns the unlocked key.
     */
    fun changePassword(
        fs: VaultFs,
        current: String,
        new: String,
        workFactor: Int = DEFAULT_SCRYPT_WORK_FACTOR,
    ): VaultKey {
        checkNewPassword(new)
        val key =
            try {
                unlock(fs, current)
            } catch (error: CoreError.Lock) {
                // Same check, but here the user should know *which* password was wrong.
                if (error.message == WRONG_PASSWORD) throw CoreError.Lock("The current password is wrong.")
                throw error
            }
        saveKey(fs, key, new, workFactor)
        return key
    }

    /** Unlocks the vault key with [password]. Takes about a second (on purpose). */
    fun unlock(
        fs: VaultFs,
        password: String,
    ): VaultKey {
        val bytes = fs.read(KEY_FILE)
        val passwordIdentity = ScryptIdentity(password.toByteArray(Charsets.UTF_8), MAX_SCRYPT_WORK_FACTOR)
        val keyBytes =
            try {
                AgeFormat.decrypt(passwordIdentity, bytes)
            } catch (_: Exception) {
                throw CoreError.Lock(WRONG_PASSWORD)
            }
        val keyText = AgeFormat.decodeUtf8(keyBytes) ?: throw CoreError.Lock("The vault key file is damaged.")
        return VaultKey.parse(keyText) ?: throw CoreError.Lock("The vault key file is damaged.")
    }

    /** Reads a locked note and returns its text. */
    fun readNote(
        fs: VaultFs,
        key: VaultKey,
        path: VaultPath,
    ): String = key.decrypt(fs.read(path))

    /** Saves a locked note's text, encrypted. A locked note is never written in plain text. */
    fun writeNote(
        fs: VaultFs,
        key: VaultKey,
        path: VaultPath,
        text: String,
    ) {
        fs.writeAtomic(path, key.encrypt(text))
    }

    /**
     * Locks a note: writes `Name.md.age` next to [note] (and checks it opens again).
     * Returns the locked path. The caller then moves `Name.md` to the trash.
     */
    fun lockNote(
        fs: VaultFs,
        key: VaultKey,
        note: VaultPath,
    ): VaultPath {
        val locked = NoteNames.lockedPath(note)
        if (fs.exists(locked)) throw CoreError.AlreadyExists(locked)
        val text = AgeFormat.decodeUtf8(fs.read(note)) ?: throw CoreError.Io("\"${note.name}\" isn't valid text")
        writeNote(fs, key, locked, text)
        // Check it opens again before the caller gives up the readable copy.
        if (readNote(fs, key, locked) != text) {
            throw CoreError.Lock("Locking didn't round-trip; the note was left as it is.")
        }
        return locked
    }

    /**
     * Removes the lock: writes `Name.md` next to [locked]. Returns its path.
     * The caller then moves `Name.md.age` to the trash.
     */
    fun unlockNote(
        fs: VaultFs,
        key: VaultKey,
        locked: VaultPath,
    ): VaultPath {
        val note = NoteNames.unlockedPath(locked)
        if (fs.exists(note)) throw CoreError.AlreadyExists(note)
        val text = readNote(fs, key, locked)
        fs.writeAtomic(note, text.toByteArray(Charsets.UTF_8))
        return note
    }

    private fun checkNewPassword(password: String) {
        // Count characters like desktop (Unicode code points), so an emoji counts as one, not two.
        if (password.codePointCount(0, password.length) < MIN_PASSWORD_LEN) {
            throw CoreError.Lock("Use at least $MIN_PASSWORD_LEN characters.")
        }
    }

    /** Writes the vault key to `.kunotes-lock.age`, encrypted with [password] (age scrypt, armored). */
    private fun saveKey(
        fs: VaultFs,
        key: VaultKey,
        password: String,
        workFactor: Int,
    ) {
        val recipient = ScryptRecipient(password.toByteArray(Charsets.UTF_8), workFactor)
        val encrypted =
            try {
                AgeFormat.encryptArmored(recipient, key.secretText().toByteArray(Charsets.UTF_8))
            } catch (error: Exception) {
                throw CoreError.Lock("Couldn't save the vault key: ${error.message}")
            }
        fs.writeAtomic(KEY_FILE, encrypted)
    }
}
