package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [FsOps.recoverInterruptedWrites]: fixing what an interrupted safe save left behind. */
class FsOpsRecoveryTest {
    @Test
    fun `recovery puts a backup back when the note is missing`() {
        val fs = MemoryVaultFs()
        fs.put("Projects/.Plan.md.kunotes.bak", "last saved")
        fs.put("Projects/.Plan.md.kunotes.tmp", "half writ")
        val handled = FsOps.recoverInterruptedWrites(fs)
        assertEquals(2, handled.size)
        assertEquals(listOf("Projects", "Projects/Plan.md"), fs.allPaths())
        assertEquals("last saved", fs.text("Projects/Plan.md"))
    }

    @Test
    fun `recovery deletes a backup when the note is there`() {
        val fs = MemoryVaultFs()
        fs.put("日本 🎉.md", "new")
        fs.put(".日本 🎉.md.kunotes.bak", "old")
        FsOps.recoverInterruptedWrites(fs)
        assertEquals(listOf("日本 🎉.md"), fs.allPaths())
        assertEquals("new", fs.text("日本 🎉.md"))
    }

    @Test
    fun `recovery leaves other files and git alone`() {
        val fs = MemoryVaultFs()
        listOf(".kunotes.tmp", "a.kunotes.tmp", ".git/.x.kunotes.tmp", ".hidden.md", "Note.md").forEach { fs.put(it) }
        val before = fs.allPaths()
        assertTrue(FsOps.recoverInterruptedWrites(fs).isEmpty())
        assertEquals(before, fs.allPaths())
    }
}
