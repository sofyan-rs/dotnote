package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

class TrashTest {
    @TempDir
    lateinit var temp: File

    private fun p(value: String) = VaultPath(value)

    private val day = LocalDate.of(2026, 10, 10)

    @Test
    fun `trash moves the item into today's folder and restore puts it back`() {
        val fs = MemoryVaultFs()
        fs.put("Projects/Plan.md", "plan")
        val item = Trash.trash(fs, p("Projects/Plan.md"), day)
        assertEquals(TrashedItem(p("Projects/Plan.md"), p(".trash/2026-10-10/Plan.md")), item)
        assertEquals("plan", fs.text(".trash/2026-10-10/Plan.md"))
        assertFalse(fs.exists(p("Projects/Plan.md")))
        // The trash is hidden from the tree.
        assertEquals(listOf("Projects"), VaultNode.scan(fs).children.map { it.name })

        assertEquals(p("Projects/Plan.md"), Trash.restore(fs, item))
        assertEquals("plan", fs.text("Projects/Plan.md"))
        assertFalse(fs.exists(p(".trash/2026-10-10/Plan.md")))
    }

    @Test
    fun `trash gives a unique name when the day folder already has one`() {
        val fs = MemoryVaultFs()
        fs.put("a/Plan.md", "first")
        fs.put("b/Plan.md", "second")
        fs.put("b/Plan 2.md", "sibling")
        fs.put("Keys.md.age", "locked")
        fs.put(".trash/2026-10-10/Keys.md.age", "older locked")
        Trash.trash(fs, p("a/Plan.md"), day)
        val second = Trash.trash(fs, p("b/Plan.md"), day)
        // "Plan 2.md" is free in the trash, even though a sibling of the original has that name.
        assertEquals(p(".trash/2026-10-10/Plan 2.md"), second.trashPath)
        assertEquals("second", fs.text(".trash/2026-10-10/Plan 2.md"))
        assertEquals("sibling", fs.text("b/Plan 2.md"))
        assertEquals(p(".trash/2026-10-10/Keys 2.md.age"), Trash.trash(fs, p("Keys.md.age"), day).trashPath)

        // The original name is free again in "b".
        assertEquals(p("b/Plan.md"), Trash.restore(fs, second))
        assertEquals("second", fs.text("b/Plan.md"))
        assertEquals("first", fs.text(".trash/2026-10-10/Plan.md"))
        assertTrue(fs.allPaths().none { it.contains("kunotes-move") }, "no temporary name left behind")
    }

    @Test
    fun `same name on another day needs no number`() {
        val fs = MemoryVaultFs()
        fs.put("Plan.md", "one")
        Trash.trash(fs, p("Plan.md"), day)
        fs.put("Plan.md", "two")
        assertEquals(p(".trash/2026-10-11/Plan.md"), Trash.trash(fs, p("Plan.md"), day.plusDays(1)).trashPath)
    }

    @Test
    fun `trash and restore a folder with its contents`() {
        val fs = FileVaultFs(temp)
        fs.createDir(p("Projects/Archive"))
        fs.writeAtomic(p("Projects/Archive/old.md"), "old".toByteArray())
        val item = Trash.trash(fs, p("Projects"), day)
        assertEquals(p(".trash/2026-10-10/Projects"), item.trashPath)
        assertFalse(fs.exists(p("Projects")))
        Trash.restore(fs, item)
        assertEquals("old", fs.read(p("Projects/Archive/old.md")).decodeToString())
    }

    @Test
    fun `restore uses a unique name if the original name was taken meanwhile`() {
        val fs = MemoryVaultFs()
        fs.put("Plan.md", "trashed")
        val item = Trash.trash(fs, p("Plan.md"), day)
        fs.put("Plan.md", "new one")
        assertEquals(p("Plan 2.md"), Trash.restore(fs, item))
        assertEquals("trashed", fs.text("Plan 2.md"))
        assertEquals("new one", fs.text("Plan.md"))
    }

    @Test
    fun `restore recreates a missing folder`() {
        val fs = MemoryVaultFs()
        fs.put("Gone/Note.md", "x")
        val item = Trash.trash(fs, p("Gone/Note.md"), day)
        fs.delete(p("Gone"))
        assertEquals(p("Gone/Note.md"), Trash.restore(fs, item))
    }

    @Test
    fun `trash refuses the root and items already in the trash`() {
        val fs = MemoryVaultFs()
        fs.put(".trash/a.md")
        assertThrows<CoreError.Io> { Trash.trash(fs, VaultPath.ROOT, day) }
        assertThrows<CoreError.Io> { Trash.trash(fs, p(".trash/a.md"), day) }
        assertThrows<CoreError.NotFound> { Trash.trash(fs, p("missing.md"), day) }
    }

    @Test
    fun `purge goes by the day an item was deleted, not when it was edited`() {
        val fs = MemoryVaultFs()
        fs.put("Old note.md", "not edited for a year") // its edit time doesn't matter
        Trash.trash(fs, p("Old note.md"), day)
        assertTrue(Trash.purge(fs, today = day.plusDays(30)).isEmpty(), "kept for the full 30 days")
        assertEquals(listOf(p(".trash/2026-10-10")), Trash.purge(fs, today = day.plusDays(31)))
        assertFalse(fs.exists(p(".trash/2026-10-10/Old note.md")))
    }

    @Test
    fun `purge leaves anything that isn't a day folder`() {
        val fs = MemoryVaultFs()
        fs.put(".trash/loose.md")
        fs.put(".trash/Not a date/x.md")
        fs.put(".trash/2020-01-01/old.md")
        assertEquals(listOf(p(".trash/2020-01-01")), Trash.purge(fs, today = day))
        assertEquals(listOf(".trash/Not a date/x.md", ".trash/loose.md"), fs.allPaths().filter { it.endsWith(".md") })
    }

    @Test
    fun `purge without a trash folder does nothing`() {
        assertTrue(Trash.purge(MemoryVaultFs(), today = day).isEmpty())
    }
}
