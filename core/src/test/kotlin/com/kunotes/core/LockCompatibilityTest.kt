package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Locked files must open on both apps. The fixture in `resources/lock-fixture/` was made by the
 * desktop app (password `correct horse`).
 *
 * To check the other direction, run this class with the environment variable
 * `KUNOTES_EXPORT_LOCK_DIR` set: the Kotlin-made vault is copied there, so desktop can open it.
 */
class LockCompatibilityTest {
    @TempDir
    lateinit var temp: File

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/lock-fixture/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    @Test
    fun `a vault locked by desktop opens here`() {
        val fs = MemoryVaultFs()
        fs.writeAtomic(Lock.KEY_FILE, fixture(".kunotes-lock.age"))
        fs.writeAtomic(VaultPath("Secret.md.age"), fixture("Secret.md.age"))

        assertTrue(Lock.hasPassword(fs))
        assertEquals("Wrong password.", runCatching { Lock.unlock(fs, "wrong horse") }.exceptionOrNull()?.message)
        val key = Lock.unlock(fs, "correct horse")
        assertEquals("# Secret\n\nAPI key: 🔑 sk-123\r\n", Lock.readNote(fs, key, VaultPath("Secret.md.age")))
    }

    @Test
    fun `a vault locked here has the desktop layout`() {
        val dir = File(temp, "vault").apply { mkdirs() }
        val fs = FileVaultFs(dir)
        val key = Lock.createPassword(fs, "kotlin pass 1")
        fs.writeAtomic(VaultPath("FromKotlin.md"), "made in kotlin 🔐\n".toByteArray())
        val locked = Lock.lockNote(fs, key, VaultPath("FromKotlin.md"))
        fs.delete(VaultPath("FromKotlin.md")) // (the app trashes it)

        for (path in listOf(Lock.KEY_FILE, locked)) {
            val text = fs.read(path).decodeToString()
            assertTrue(text.startsWith("-----BEGIN AGE ENCRYPTED FILE-----\n"), "$path is armored")
            assertTrue(text.trimEnd().endsWith("-----END AGE ENCRYPTED FILE-----"), "$path is armored")
        }
        // ISO-8859-1 maps every byte to a character, so the binary part after the header can't fail.
        val keyHeader = String(armorBody(fs.read(Lock.KEY_FILE)), Charsets.ISO_8859_1)
        assertTrue(keyHeader.contains("\n-> scrypt "), "the key file uses an age scrypt recipient")
        assertEquals("made in kotlin 🔐\n", Lock.readNote(fs, Lock.unlock(fs, "kotlin pass 1"), locked))

        val export = System.getenv("KUNOTES_EXPORT_LOCK_DIR")
        if (!export.isNullOrBlank()) {
            File(export).deleteRecursively()
            dir.copyRecursively(File(export))
        }
    }

    /** The age bytes inside an armored file (only the header is text, which is all we look at). */
    private fun armorBody(armored: ByteArray): ByteArray {
        val lines = armored.decodeToString().trim().lines()
        val base64 = lines.drop(1).dropLast(1).joinToString("")
        return java.util.Base64
            .getDecoder()
            .decode(base64)
            .copyOf(200)
    }
}
