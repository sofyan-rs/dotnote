package com.dotnote.ui.sync

import com.dotnote.sync.SyncPhase
import com.dotnote.sync.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The SYNC tab's texts and the clone screen's folder-name suggestion. */
class SyncUiTest {
    private val minute = 60_000L

    @Test
    fun `the hero counts minutes, then hours, then days`() {
        assertEquals("--" to "NEVER", lastSyncHero(null, 0))
        assertEquals("0" to "MIN AGO", lastSyncHero(1_000, 30_000))
        assertEquals("2" to "MIN AGO", lastSyncHero(0, 2 * minute + 5_000))
        assertEquals("3" to "H AGO", lastSyncHero(0, 190 * minute))
        assertEquals("2" to "D AGO", lastSyncHero(0, 2 * 24 * 60 * minute))
    }

    @Test
    fun `the summary and remote match the mockup`() {
        val status =
            SyncStatus(
                vaultId = "v",
                loaded = true,
                connected = true,
                remoteUrl = "https://github.com/sofyan-rs/notes.git",
                phase = SyncPhase.UpToDate,
                lastSyncMillis = 0,
                branch = "main",
                pushed = 3,
                pulled = 0,
            )
        val ui = toUi(status, "Notes", hasSignIn = true, now = 2 * minute)
        assertEquals("main · 3 files pushed · 0 pulled", ui.summary)
        assertEquals("github.com/sofyan-rs/notes.git", ui.remote)
        assertFalse(ui.isError)

        val never = toUi(status.copy(lastSyncMillis = null, phase = SyncPhase.Error, message = "No"), "Notes", false, 0)
        assertNull(never.summary)
        assertTrue(never.isError)
    }

    @Test
    fun `the folder name comes from the address`() {
        assertEquals("notes", defaultFolderName("https://github.com/me/notes.git"))
        assertEquals("my-notes", defaultFolderName("https://gitlab.com/group/my-notes/ "))
        assertEquals("notes", defaultFolderName("git@github.com:notes.git"))
        assertEquals("Notes", defaultFolderName("https://"))
    }
}
