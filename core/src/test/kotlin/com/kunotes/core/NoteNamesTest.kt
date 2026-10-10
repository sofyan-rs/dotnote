package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteNamesTest {
    @Test
    fun `locked names`() {
        val note = VaultPath("vault/Keys.md")
        val locked = NoteNames.lockedPath(note)
        assertEquals(VaultPath("vault/Keys.md.age"), locked)
        assertTrue(NoteNames.isLockedNote(locked))
        assertTrue(NoteNames.isLockedNote(VaultPath("A.MD.AGE")))
        assertFalse(NoteNames.isLockedNote(note))
        assertFalse(NoteNames.isLockedNote(VaultPath("photo.age")))
        assertEquals(note, NoteNames.unlockedPath(locked))
    }

    @Test
    fun `markdown extension is case insensitive`() {
        assertTrue(NoteNames.isNote(VaultPath("a.md")))
        assertTrue(NoteNames.isNote(VaultPath("a.MD")))
        assertFalse(NoteNames.isNote(VaultPath("a.txt")))
        assertFalse(NoteNames.isNote(VaultPath("md")))
    }

    @Test
    fun `unique path counts up`() {
        val fs = MemoryVaultFs()
        assertEquals(VaultPath("Untitled.md"), NoteNames.uniquePath(fs, VaultPath.ROOT, "Untitled", "md"))
        fs.put("Untitled.md")
        fs.put("Untitled 2.md")
        assertEquals(VaultPath("Untitled 3.md"), NoteNames.uniquePath(fs, VaultPath.ROOT, "Untitled", "md"))
        assertEquals(VaultPath("Folder"), NoteNames.uniquePath(fs, VaultPath.ROOT, "Folder", null))
    }
}
