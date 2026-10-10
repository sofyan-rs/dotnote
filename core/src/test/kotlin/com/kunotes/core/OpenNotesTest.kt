package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Desktop `tabs.rs` tests that still apply on Android, with the "most recent first" order. */
class OpenNotesTest {
    private fun path(name: String) = VaultPath("$name.md")

    /** Short labels: "a" for a.md, "a*" when pinned. */
    private fun names(notes: OpenNotes) = notes.entries.map { Paths.noteTitle(it.path) + if (it.pinned) "*" else "" }

    private fun openAll(vararg names: String): OpenNotes {
        var notes = OpenNotes()
        for (name in names) notes = notes.open(path(name))
        return notes
    }

    @Test
    fun `new notes open at the front`() {
        assertEquals(listOf("c", "b", "a"), names(openAll("a", "b", "c")))
    }

    @Test
    fun `opening an open note moves it to the front`() {
        val notes = openAll("a", "b", "c").open(path("a"))
        assertEquals(listOf("a", "c", "b"), names(notes))
    }

    @Test
    fun `closing a note keeps the others in order`() {
        var notes = openAll("a", "b", "c")
        notes = notes.close(path("b"))
        assertEquals(listOf("c", "a"), names(notes))
        notes = notes.close(path("c")).close(path("a"))
        assertTrue(notes.isEmpty)
        assertEquals(notes, notes.close(path("zzz")), "closing a note that isn't open does nothing")
    }

    @Test
    fun `pinned notes move to the front and survive bulk closes`() {
        var notes = openAll("d", "c", "b", "a") // a, b, c, d
        notes = notes.pin(path("c"))
        assertEquals(listOf("c*", "a", "b", "d"), names(notes))

        notes = notes.closeOthers(path("a"))
        assertEquals(listOf("c*", "a"), names(notes))

        notes = notes.open(path("e")).closeUnpinned()
        assertEquals(listOf("c*"), names(notes))
    }

    @Test
    fun `pinning adds to the end of the pinned group`() {
        val notes = openAll("c", "b", "a").pin(path("b")).pin(path("c"))
        assertEquals(listOf("b*", "c*", "a"), names(notes))
    }

    @Test
    fun `unpinning puts the note first after the pinned group`() {
        var notes = openAll("c", "b", "a") // a, b, c
        notes = notes.togglePin(path("b")).togglePin(path("c"))
        assertEquals(listOf("b*", "c*", "a"), names(notes))
        notes = notes.togglePin(path("b"))
        assertEquals(listOf("c*", "b", "a"), names(notes))
    }

    @Test
    fun `new notes never open among pinned notes`() {
        var notes = openAll("a").pin(path("a"))
        notes = notes.open(path("b")).open(path("c"))
        assertEquals(listOf("a*", "c", "b"), names(notes))
    }

    @Test
    fun `opening a pinned note keeps its place`() {
        val notes =
            openAll("b", "a")
                .pin(path("a"))
                .pin(path("b"))
                .open(path("x"))
                .open(path("b"))
        assertEquals(listOf("a*", "b*", "x"), names(notes))
    }

    @Test
    fun `remap follows renamed notes and folders`() {
        var notes = OpenNotes().open(VaultPath("Projects/Plan.md")).open(VaultPath("Welcome.md"))
        notes = notes.pin(VaultPath("Projects/Plan.md"))
        notes = notes.remap(VaultPath("Projects"), VaultPath("Work"))
        assertEquals(listOf(VaultPath("Work/Plan.md"), VaultPath("Welcome.md")), notes.paths)
        assertTrue(notes.entries[0].pinned)

        notes = notes.remap(VaultPath("Welcome.md"), VaultPath("日本 🎉.md"))
        assertTrue(VaultPath("日本 🎉.md") in notes)
        assertFalse(VaultPath("Welcome.md") in notes)
    }

    @Test
    fun `close inside closes notes in a trashed folder`() {
        val notes = OpenNotes().open(VaultPath("A/x.md")).open(VaultPath("A2/y.md")).open(VaultPath("A/B/z.md"))
        assertEquals(listOf(VaultPath("A2/y.md")), notes.closeInside(VaultPath("A")).paths)
    }
}
