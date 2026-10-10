package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/** Runs the same checks against both VaultFs implementations, so they behave alike. */
class VaultFsTest {
    @TempDir
    lateinit var temp: File

    private fun fs(kind: String): VaultFs = if (kind == "file") FileVaultFs(temp) else MemoryVaultFs()

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `write, read, list`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("Projects"))
        fs.writeAtomic(VaultPath("Projects/Plan.md"), "# Plan\n".toByteArray())
        assertArrayEquals("# Plan\n".toByteArray(), fs.read(VaultPath("Projects/Plan.md")))
        assertEquals(listOf("Projects"), fs.list(VaultPath.ROOT).map { it.name })
        assertTrue(fs.list(VaultPath.ROOT).single().isDir)
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `atomic write replaces and leaves no temp file`(kind: String) {
        val fs = fs(kind)
        fs.writeAtomic(VaultPath("a.md"), "one".toByteArray())
        fs.writeAtomic(VaultPath("a.md"), "two".toByteArray())
        assertEquals("two", fs.read(VaultPath("a.md")).decodeToString())
        assertEquals(listOf("a.md"), fs.list(VaultPath.ROOT).map { it.name })
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `createFile refuses to overwrite`(kind: String) {
        val fs = fs(kind)
        fs.createFile(VaultPath("a.md"), "one".toByteArray())
        assertThrows<CoreError.AlreadyExists> { fs.createFile(VaultPath("a.md"), "two".toByteArray()) }
        assertEquals("one", fs.read(VaultPath("a.md")).decodeToString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename and move folders with content`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("a/inner"))
        fs.writeAtomic(VaultPath("a/inner/n.md"), "x".toByteArray())
        fs.createDir(VaultPath("b"))
        val renamed = fs.rename(VaultPath("a"), "c")
        val moved = fs.move(renamed, VaultPath("b"))
        assertEquals(VaultPath("b/c"), moved)
        assertEquals("x", fs.read(VaultPath("b/c/inner/n.md")).decodeToString())
        assertFalse(fs.exists(VaultPath("a")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `delete removes everything inside`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("a/b"))
        fs.writeAtomic(VaultPath("a/b/n.md"), "x".toByteArray())
        fs.delete(VaultPath("a"))
        assertFalse(fs.exists(VaultPath("a")))
        assertFalse(fs.exists(VaultPath("a/b/n.md")))
    }

    @Test
    fun `a symlinked folder is not followed`() {
        val fs = FileVaultFs(temp)
        fs.createDir(VaultPath("real"))
        java.nio.file.Files
            .createSymbolicLink(File(temp, "real/loop").toPath(), temp.toPath())
        val loop = fs.list(VaultPath("real")).single()
        assertFalse(loop.isDir)
    }
}
