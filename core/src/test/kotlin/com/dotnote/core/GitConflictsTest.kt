package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Conflict copy names and dates (desktop `git.rs` unit tests). */
class GitConflictsTest {
    @Test
    fun `dates from days`() {
        assertEquals("1970-01-01", GitConflicts.dateFromDays(0))
        assertEquals("2025-10-09", GitConflicts.dateFromDays(20_370))
        assertEquals("2024-02-29", GitConflicts.dateFromDays(19_782)) // leap day
    }

    @Test
    fun `today looks like a date`() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(GitConflicts.today()))
    }

    @Test
    fun `conflict copies sit next to the file`() {
        val fs = MemoryVaultFs()
        assertEquals(
            VaultPath("Projects/Plan (conflict 2026-10-09).md"),
            GitConflicts.conflictCopyPath(fs, VaultPath("Projects/Plan.md"), "2026-10-09"),
        )
        assertEquals(
            VaultPath("Keys (conflict 2026-10-09).md.age"),
            GitConflicts.conflictCopyPath(fs, VaultPath("Keys.md.age"), "2026-10-09"),
        )
    }

    @Test
    fun `a taken conflict name gets a number`() {
        val fs = MemoryVaultFs()
        fs.put("Plan (conflict 2026-10-09).md")
        assertEquals(
            VaultPath("Plan (conflict 2026-10-09) 2.md"),
            GitConflicts.conflictCopyPath(fs, VaultPath("Plan.md"), "2026-10-09"),
        )
    }

    @Test
    fun `names without an extension, dot files, emoji and CJK`() {
        val fs = MemoryVaultFs()
        val date = "2026-10-09"
        assertEquals(VaultPath("README (conflict $date)"), GitConflicts.conflictCopyPath(fs, VaultPath("README"), date))
        assertEquals(VaultPath(".gitignore (conflict $date)"), GitConflicts.conflictCopyPath(fs, VaultPath(".gitignore"), date))
        assertEquals(VaultPath("a.b (conflict $date).png"), GitConflicts.conflictCopyPath(fs, VaultPath("a.b.png"), date))
        assertEquals(VaultPath("日本 🔑 (conflict $date).md"), GitConflicts.conflictCopyPath(fs, VaultPath("日本 🔑.md"), date))
        // A locked note keeps the fixed lower-case extension, like desktop.
        assertEquals(VaultPath("K (conflict $date).md.age"), GitConflicts.conflictCopyPath(fs, VaultPath("K.MD.AGE"), date))
    }
}
