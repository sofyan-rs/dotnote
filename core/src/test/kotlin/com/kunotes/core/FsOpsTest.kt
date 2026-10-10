package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/** Desktop `tests/fs_ops.rs`, run on real files and in memory. Trash and recovery tests: see [FsOpsTrashTest]. */
class FsOpsTest {
    @TempDir
    lateinit var temp: File

    private val root = VaultPath.ROOT

    private fun fs(kind: String): VaultFs =
        when (kind) {
            "file" -> FileVaultFs(temp)
            "case-insensitive" -> CaseInsensitiveVaultFs()
            else -> MemoryVaultFs()
        }

    private fun VaultFs.write(
        path: String,
        text: String,
    ) = writeAtomic(VaultPath(path), text.toByteArray())

    private fun VaultFs.text(path: VaultPath) = read(path).decodeToString()

    /** Names inside [dir], sorted, so tests don't depend on listing order. */
    private fun VaultFs.namesIn(dir: VaultPath = root) = list(dir).map { it.name }.sorted()

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `create file seeds heading and never overwrites`(kind: String) {
        val fs = fs(kind)
        val first = FsOps.createFile(fs, root, "Untitled")
        val second = FsOps.createFile(fs, root, "Untitled")
        assertEquals("Untitled.md", first.name)
        assertEquals("Untitled 2.md", second.name)
        assertEquals("# Untitled 2\n", fs.text(second))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `create folder uses unique name`(kind: String) {
        val fs = fs(kind)
        FsOps.createFolder(fs, root, "New Folder")
        FsOps.createFolder(fs, root, "New Folder")
        assertEquals(listOf("New Folder", "New Folder 2"), fs.namesIn())
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename file adds md unless present`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "x")
        var renamed = FsOps.rename(fs, VaultPath("a.md"), "  Plan  ")
        assertEquals(VaultPath("Plan.md"), renamed)
        renamed = FsOps.rename(fs, renamed, "Report.MD")
        assertEquals(VaultPath("Report.MD"), renamed)
        renamed = FsOps.rename(fs, renamed, "v1.2")
        assertEquals(VaultPath("v1.2.md"), renamed)
        assertEquals("x", fs.text(renamed))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename locked note keeps it locked`(kind: String) {
        val fs = fs(kind)
        fs.write("Keys.md.age", "secret")
        assertEquals(VaultPath("Vault.md.age"), FsOps.rename(fs, VaultPath("Keys.md.age"), "Vault"))
        assertEquals(VaultPath("Box.md.age"), FsOps.rename(fs, VaultPath("Vault.md.age"), "Box.md"))
        assertEquals(VaultPath("Safe.MD.AGE"), FsOps.rename(fs, VaultPath("Box.md.age"), "Safe.MD.AGE"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename folder keeps name as typed`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("Old"))
        assertEquals(VaultPath("New"), FsOps.rename(fs, VaultPath("Old"), "New"))
        assertEquals(VaultPath("v2"), FsOps.rename(fs, VaultPath("New"), "v2"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename to same name is a no op`(kind: String) {
        val fs = fs(kind)
        fs.write("Same.md", "")
        assertEquals(VaultPath("Same.md"), FsOps.rename(fs, VaultPath("Same.md"), "Same"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory", "case-insensitive"])
    fun `rename only changing case works`(kind: String) {
        val fs = fs(kind)
        fs.write("note.md", "keep me")
        val renamed = FsOps.rename(fs, VaultPath("note.md"), "Note")
        assertEquals(listOf("Note.md"), fs.namesIn())
        assertEquals("keep me", fs.text(renamed))
    }

    @Test
    fun `rename only changing case refuses an exact match on case sensitive storage`() {
        val fs = MemoryVaultFs()
        fs.put("note.md", "lower")
        fs.put("Note.md", "upper")
        assertThrows<CoreError.AlreadyExists> { FsOps.rename(fs, VaultPath("note.md"), "Note") }
        assertEquals("upper", fs.text("Note.md"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory", "case-insensitive"])
    fun `rename refuses to overwrite`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "a")
        fs.write("b.md", "b")
        assertThrows<CoreError.AlreadyExists> { FsOps.rename(fs, VaultPath("a.md"), "b") }
        assertEquals("b", fs.text(VaultPath("b.md")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `rename rejects invalid names`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "")
        val a = VaultPath("a.md")
        val slash = assertThrows<CoreError.InvalidName> { FsOps.rename(fs, a, "sub/escape") }
        assertEquals("Invalid name: the name can't contain '/'", slash.message)
        // Caught even earlier: a leading "." would make the file hidden.
        assertThrows<CoreError.InvalidName> { FsOps.rename(fs, a, "../escape") }
        assertTrue(fs.exists(a), "a rejected rename must leave the file in place")
        val empty = assertThrows<CoreError.InvalidName> { FsOps.rename(fs, a, "   ") }
        assertEquals("Invalid name: ${NameError.Empty.message}", empty.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `move into folder`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "")
        fs.createDir(VaultPath("Folder"))
        val moved = FsOps.moveInto(fs, VaultPath("a.md"), VaultPath("Folder"))
        assertEquals(VaultPath("Folder/a.md"), moved)
        assertTrue(fs.exists(moved))
        assertFalse(fs.exists(VaultPath("a.md")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `move into current parent does nothing`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "")
        assertEquals(VaultPath("a.md"), FsOps.moveInto(fs, VaultPath("a.md"), root))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `move folder into itself or descendant is refused`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("A/B"))
        assertThrows<CoreError.MoveIntoSelf> { FsOps.moveInto(fs, VaultPath("A"), VaultPath("A")) }
        assertThrows<CoreError.MoveIntoSelf> { FsOps.moveInto(fs, VaultPath("A"), VaultPath("A/B")) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `move refuses to overwrite`(kind: String) {
        val fs = fs(kind)
        fs.write("a.md", "outside")
        fs.createDir(VaultPath("Folder"))
        fs.write("Folder/a.md", "inside")
        assertThrows<CoreError.AlreadyExists> { FsOps.moveInto(fs, VaultPath("a.md"), VaultPath("Folder")) }
        assertEquals("inside", fs.text(VaultPath("Folder/a.md")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `create file named uses the typed name and refuses duplicates`(kind: String) {
        val fs = fs(kind)
        val note = FsOps.createFileNamed(fs, root, "  Ideas ")
        assertEquals(VaultPath("Ideas.md"), note)
        assertEquals("# Ideas\n", fs.text(note))
        assertEquals(VaultPath("Report.md"), FsOps.createFileNamed(fs, root, "Report.md"))
        assertThrows<CoreError.AlreadyExists> { FsOps.createFileNamed(fs, root, "Ideas") }
        assertThrows<CoreError.InvalidName> { FsOps.createFileNamed(fs, root, "a/b") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `create file named with emoji and cjk`(kind: String) {
        val fs = fs(kind)
        val note = FsOps.createFileNamed(fs, root, "日本 🎉")
        assertEquals(VaultPath("日本 🎉.md"), note)
        assertEquals("# 日本 🎉\n", fs.text(note))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `create folder named uses the typed name and refuses duplicates`(kind: String) {
        val fs = fs(kind)
        assertEquals(VaultPath("Work"), FsOps.createFolderNamed(fs, root, "Work"))
        assertTrue(fs.stat(VaultPath("Work"))!!.isDir)
        assertThrows<CoreError.AlreadyExists> { FsOps.createFolderNamed(fs, root, "Work") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `import image copies next to the note and avoids name clashes`(kind: String) {
        val fs = fs(kind)
        fs.createDir(VaultPath("Projects"))
        fs.write("Projects/Plan.md", "# Plan\n")
        val note = VaultPath("Projects/Plan.md")
        val bytes = "png bytes".toByteArray()

        assertEquals(".img/my-photo.png", FsOps.importImage(fs, note, "my photo.PNG", bytes))
        assertEquals("png bytes", fs.text(VaultPath("Projects/.img/my-photo.png")))
        assertEquals(".img/my-photo-2.png", FsOps.importImage(fs, note, "my photo.PNG", bytes))
        assertEquals(".img/猫-🐱.jpg", FsOps.importImage(fs, note, "猫　🐱.jpg", bytes))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file", "memory"])
    fun `import image refuses files that are not images`(kind: String) {
        val fs = fs(kind)
        val note = VaultPath("Note.md")
        val error = assertThrows<CoreError.NotAnImage> { FsOps.importImage(fs, note, "notes.txt", ByteArray(0)) }
        assertEquals("\"notes.txt\" isn't an image (use PNG, JPEG, GIF, WebP, SVG or BMP)", error.message)
        assertThrows<CoreError.NotAnImage> { FsOps.importImage(fs, note, ".png", ByteArray(0)) }
        assertThrows<CoreError.NotAnImage> { FsOps.importImage(fs, note, "png", ByteArray(0)) }
        assertNull(fs.stat(VaultPath(".img")), "no folder is made for nothing")
    }
}

/**
 * A [MemoryVaultFs] that behaves like case-insensitive storage (Android shared storage):
 * `stat` finds names in any letter case, and `rename` refuses a name that matches in any case.
 */
internal class CaseInsensitiveVaultFs(
    private val inner: MemoryVaultFs = MemoryVaultFs(),
) : VaultFs by inner {
    // `VaultFs by inner` forwards every function to `inner`, except the ones overridden here.

    override fun stat(path: VaultPath): FsEntry? {
        val parent = path.parent ?: return inner.stat(path)
        if (inner.stat(parent) == null) return null
        val match = inner.list(parent).firstOrNull { it.name.equals(path.name, ignoreCase = true) }
        return match
    }

    override fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath {
        val parent = path.parent!! // tests never rename the root
        if (inner.list(parent).any { it.name.equals(newName, ignoreCase = true) }) {
            throw CoreError.AlreadyExists(parent.child(newName))
        }
        return inner.rename(path, newName)
    }
}
