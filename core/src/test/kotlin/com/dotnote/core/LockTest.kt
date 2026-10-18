package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Locked notes on a real disk: password, lock, unlock, wrong password (desktop `tests/lock.rs`). */
class LockTest {
    @TempDir
    lateinit var temp: File

    private fun vault(name: String): FileVaultFs = FileVaultFs(File(temp, name).apply { mkdirs() })

    @Test
    fun `a locked note is unreadable on disk and opens with the password`() {
        val fs = vault("v")
        val note = VaultPath("Keys.md")
        fs.writeAtomic(note, "api_key = sk-日本-🔑\n".toByteArray())

        assertFalse(Lock.hasPassword(fs))
        assertThrows<CoreError.Lock>("too short") { Lock.createPassword(fs, "short") }
        val key = Lock.createPassword(fs, "correct horse")
        assertTrue(Lock.hasPassword(fs))
        assertThrows<CoreError.Lock>("only one") { Lock.createPassword(fs, "another password") }

        val locked = Lock.lockNote(fs, key, note)
        assertEquals(VaultPath("Keys.md.age"), locked)
        assertTrue(fs.exists(note), "the app moves the readable copy to the trash")
        fs.delete(note) // (the app trashes it)
        val onDisk = fs.read(locked).decodeToString()
        assertTrue(onDisk.startsWith("-----BEGIN AGE ENCRYPTED FILE-----"))
        assertFalse(onDisk.contains("sk-"), "the secret isn't on disk")

        // Later (e.g. after a restart): unlock with the password.
        val wrong = assertThrows<CoreError.Lock> { Lock.unlock(fs, "wrong password") }
        assertEquals("Wrong password.", wrong.message)
        val unlocked = Lock.unlock(fs, "correct horse")
        assertEquals("api_key = sk-日本-🔑\n", Lock.readNote(fs, unlocked, locked))

        Lock.writeNote(fs, unlocked, locked, "changed\n")
        assertEquals("changed\n", Lock.readNote(fs, unlocked, locked))

        val back = Lock.unlockNote(fs, unlocked, locked)
        assertEquals(note, back)
        assertEquals("changed\n", fs.read(back).decodeToString())
    }

    @Test
    fun `a note from another vault key is refused`() {
        val a = vault("a")
        val b = vault("b")
        val keyA = Lock.createPassword(a, "password a")
        val keyB = Lock.createPassword(b, "password b")
        a.writeAtomic(VaultPath("N.md"), "x".toByteArray())
        val locked = Lock.lockNote(a, keyA, VaultPath("N.md"))
        val error = assertThrows<CoreError.Lock> { Lock.readNote(a, keyB, locked) }
        assertEquals("This note was locked with another vault password.", error.message)
    }

    @Test
    fun `changing the password keeps locked notes readable`() {
        val fs = vault("v")
        val note = VaultPath("Keys.md")
        fs.writeAtomic(note, "pin = 1234 🔑\n".toByteArray())
        val key = Lock.createPassword(fs, "old password")
        val locked = Lock.lockNote(fs, key, note)
        val lockedBytes = fs.read(locked)

        val wrong = assertThrows<CoreError.Lock> { Lock.changePassword(fs, "wrong one!", "new password") }
        assertEquals("The current password is wrong.", wrong.message)
        assertThrows<CoreError.Lock>("too short") { Lock.changePassword(fs, "old password", "short") }
        val changed = Lock.changePassword(fs, "old password", "new password")
        assertEquals("pin = 1234 🔑\n", Lock.readNote(fs, changed, locked))
        assertArrayEquals(lockedBytes, fs.read(locked), "notes untouched")

        assertThrows<CoreError.Lock> { Lock.unlock(fs, "old password") }
        val unlocked = Lock.unlock(fs, "new password")
        assertEquals("pin = 1234 🔑\n", Lock.readNote(fs, unlocked, locked))
    }

    @Test
    fun `password length counts characters, not UTF-16 units`() {
        val fs = MemoryVaultFs()
        // 4 emoji are 8 UTF-16 units but only 4 characters: too short, like on desktop.
        val error = assertThrows<CoreError.Lock> { Lock.createPassword(fs, "🔑🔑🔑🔑") }
        assertEquals("Use at least 8 characters.", error.message)
        assertFalse(Lock.hasPassword(fs))
        Lock.createPassword(fs, "日本語のパスワード")
        assertTrue(Lock.hasPassword(fs))
    }

    @Test
    fun `locking refuses to overwrite and unlocking refuses to overwrite`() {
        val fs = MemoryVaultFs()
        fs.put("Keys.md", "secret")
        fs.put("Keys.md.age", "already here")
        val key = Lock.createPassword(fs, "correct horse")
        assertThrows<CoreError.AlreadyExists> { Lock.lockNote(fs, key, VaultPath("Keys.md")) }

        fs.put("Other.md", "x")
        val locked = Lock.lockNote(fs, key, VaultPath("Other.md"))
        assertThrows<CoreError.AlreadyExists> { Lock.unlockNote(fs, key, locked) }
        assertEquals("x", fs.text("Other.md"))
    }

    @Test
    fun `a damaged locked note gives a lock error`() {
        val fs = MemoryVaultFs()
        val key = Lock.createPassword(fs, "correct horse")
        fs.put("Bad.md.age", "not age at all")
        assertThrows<CoreError.Lock> { Lock.readNote(fs, key, VaultPath("Bad.md.age")) }
    }
}
