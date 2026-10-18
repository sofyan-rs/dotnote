package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathsTest {
    private fun p(value: String) = VaultPath(value)

    @Test
    fun `image links resolve next to the note`() {
        val dir = p("Projects")
        assertEquals(p("Projects/.img/cat.png"), Paths.imageFile(dir, ".img/cat.png"))
        assertNull(Paths.imageFile(dir, "https://x.dev/cat.png"))
        assertNull(Paths.imageFile(dir, "data:image/png;base64,AAAA"))
    }

    @Test
    fun `image links stay inside the vault`() {
        assertEquals(p("cat.png"), Paths.imageFile(p("Projects"), "../cat.png"))
        assertEquals(p("img/cat.png"), Paths.imageFile(VaultPath.ROOT, "./img//cat.png"))
        assertNull(Paths.imageFile(p("Projects"), "../../cat.png"))
        assertNull(Paths.imageFile(p("Projects"), "/Users/me/cat.png"))
    }

    @Test
    fun `note title strips only markdown extension`() {
        assertEquals("Plan", Paths.noteTitle(p("a/Plan.md")))
        assertEquals("Keys", Paths.noteTitle(p("a/Keys.md.age")))
        assertEquals("Shout", Paths.noteTitle(p("a/Shout.MD")))
        assertEquals("v1.2", Paths.noteTitle(p("a/v1.2")))
        assertEquals("Folder", Paths.noteTitle(p("a/Folder")))
        assertEquals("日本 🎉", Paths.noteTitle(p("日本 🎉.md")))
    }

    @Test
    fun `breadcrumb omits vault name and extension`() {
        assertEquals(listOf("Projects", "Archive", "old"), Paths.breadcrumb(p("Projects/Archive/old.md")))
        assertEquals(listOf("Top"), Paths.breadcrumb(p("Top.md")))
        assertEquals(emptyList<String>(), Paths.breadcrumb(VaultPath.ROOT))
    }

    @Test
    fun `remap handles exact match and descendants`() {
        val old = p("Projects")
        val new = p("Work")
        assertEquals(new, Paths.remapPath(old, old, new))
        assertEquals(p("Work/Plan.md"), Paths.remapPath(p("Projects/Plan.md"), old, new))
        assertNull(Paths.remapPath(p("Other.md"), old, new))
        // "Projects2" only shares a text prefix with "Projects"; it is not inside it.
        assertNull(Paths.remapPath(p("Projects2"), old, new))
        // Moving into the root and out of it.
        assertEquals(p("Plan.md"), Paths.remapPath(p("Projects/Plan.md"), p("Projects/Plan.md"), p("Plan.md")))
        assertEquals(p("A/Projects/x.md"), Paths.remapPath(p("Projects/x.md"), old, p("A/Projects")))
    }

    @Test
    fun `hidden paths are detected anywhere below root`() {
        assertTrue(Paths.isHidden(p(".git/HEAD")))
        assertTrue(Paths.isHidden(p("a/.b.md.dotnote.tmp")))
        assertFalse(Paths.isHidden(p("a/b.md")))
        assertFalse(Paths.isHidden(VaultPath.ROOT), "the vault root itself is visible")
    }

    @Test
    fun `can move into rejects self descendants and current folder`() {
        val projects = p("Projects")
        val plan = p("Projects/Plan.md")
        assertTrue(Paths.canMoveInto(plan, VaultPath.ROOT))
        assertTrue(Paths.canMoveInto(plan, p("Other")))
        assertFalse(Paths.canMoveInto(plan, projects), "already there")
        assertFalse(Paths.canMoveInto(projects, projects), "into itself")
        assertFalse(Paths.canMoveInto(projects, p("Projects/Archive")), "into its own child")
        // "Projects2" only shares a text prefix with "Projects".
        assertTrue(Paths.canMoveInto(projects, p("Projects2")))
    }

    @Test
    fun `rename selection skips only the md extension of notes`() {
        assertEquals(TextRange(0, 4), Paths.renameSelection("PLAN.md", false))
        assertEquals(TextRange(0, 5), Paths.renameSelection("Shout.MD", false))
        assertEquals(TextRange(0, 4), Paths.renameSelection("v1.2.md", false))
        assertEquals(TextRange(0, 9), Paths.renameSelection("notes.txt", false))
        assertEquals(TextRange(0, 9), Paths.renameSelection("Folder.md", true), "folders: whole name")
        assertEquals(TextRange(0, 3), Paths.renameSelection(".md", false))
        assertEquals(TextRange(0, 4), Paths.renameSelection("Keys.md.age", false))
        // Desktop: 0..6 bytes; in UTF-16 the two CJK characters are 2 units.
        assertEquals(TextRange(0, 2), Paths.renameSelection("日本.md", false))
    }

    @Test
    fun `rename selection keeps emoji whole`() {
        val name = "Party 🎉.md"
        val range = Paths.renameSelection(name, false)
        assertEquals("Party 🎉", name.substring(range))
        assertEquals(TextRange(0, 2), Paths.renameSelection("🎉", false))
        assertEquals("🎉", "🎉.md.age".substring(Paths.renameSelection("🎉.md.age", false)))
    }

    @Test
    fun `ancestors within lists folders outermost first`() {
        assertEquals(listOf(p("a"), p("a/b")), Paths.ancestorsWithin(p("a/b/c.md")))
        assertTrue(Paths.ancestorsWithin(p("top.md")).isEmpty())
        assertEquals(listOf(p("a/b")), Paths.ancestorsWithin(p("a/b/c.md"), root = p("a")))
    }
}
