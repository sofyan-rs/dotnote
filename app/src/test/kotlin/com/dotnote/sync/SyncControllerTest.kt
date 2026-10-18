package com.dotnote.sync

import com.dotnote.core.CoreError
import com.dotnote.core.GitCredentials
import com.dotnote.core.SyncReport
import com.dotnote.core.VaultPath
import com.dotnote.settings.SavedVault
import com.dotnote.settings.VaultKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * SyncController timing and status, with a fake git and virtual time (`runTest` skips delays,
 * `advanceTimeBy` moves the clock).
 */
class SyncControllerTest {
    private val cloned = SavedVault("v1", "Notes", VaultKind.File, "/data/vaults/Notes")
    private val picked = SavedVault("v2", "Picked", VaultKind.Saf, "content://tree")

    /** A fake git: counts calls and returns [report] (or throws [error]). */
    private class FakeGit : SyncGit {
        var syncs = 0
        var quickPushes = 0
        var report = SyncReport()
        var error: CoreError? = null

        override fun remoteUrl(folder: File) = "https://github.com/me/notes.git"

        override fun sync(
            folder: File,
            credentials: GitCredentials?,
        ): SyncReport {
            syncs++
            error?.let { throw it }
            return report
        }

        override fun commitAndPushQuickly(
            folder: File,
            credentials: GitCredentials?,
        ): Boolean {
            quickPushes++
            return true
        }
    }

    private class Fixture(
        test: TestScope,
        scope: CoroutineScope,
        vault: SavedVault?,
        autoSync: Set<String>,
    ) {
        val git = FakeGit()
        val openVault = MutableStateFlow(vault)
        val autoSyncVaults = MutableStateFlow(autoSync)
        val vaultChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val backgroundPushes = mutableListOf<String>()
        var refreshes = 0
        val controller =
            SyncController(
                scope = scope,
                openVault = openVault,
                autoSyncVaults = autoSyncVaults,
                vaultChanges = vaultChanges,
                git = git,
                credentials = { GitCredentials("me", "token") },
                folderOf = { if (it.kind == VaultKind.File) File(it.location) else null },
                afterSync = { refreshes++ },
                scheduleBackgroundPush = { backgroundPushes += it },
                io = StandardTestDispatcher(test.testScheduler),
                clock = { test.testScheduler.currentTime },
            )
    }

    /** Builds the controller in the test's background scope (cancelled when the test ends). */
    private fun TestScope.fixture(
        vault: SavedVault? = cloned,
        autoSync: Set<String> = setOf(cloned.id),
    ): Fixture = Fixture(this, backgroundScope, vault, autoSync).also { runCurrent() }

    @Test
    fun `sync now reports what moved and refreshes the vault`() =
        runTest {
            val f = fixture()
            assertTrue(f.controller.status.value.connected)
            assertEquals("https://github.com/me/notes.git", f.controller.status.value.remoteUrl)
            f.git.report =
                SyncReport(changed = listOf(VaultPath("A.md")), pushed = listOf(VaultPath("B.md"), VaultPath("C.md")), branch = "main")
            val changed = mutableListOf<List<VaultPath>>()
            backgroundScope.launch { f.controller.changedFiles.collect { changed += it } }
            advanceTimeBy(5_000)

            f.controller.syncNow()
            runCurrent()

            val status = f.controller.status.value
            assertEquals(SyncPhase.UpToDate, status.phase)
            assertEquals(5_000L, status.lastSyncMillis)
            assertEquals(2, status.pushed)
            assertEquals(1, status.pulled)
            assertEquals("main", status.branch)
            assertNull(status.message)
            assertEquals(1, f.refreshes)
            assertEquals(listOf(listOf(VaultPath("A.md"))), changed)
        }

    @Test
    fun `auto-sync runs 60 s after the last save`() =
        runTest {
            val f = fixture()
            f.controller.onAppStart()
            runCurrent()
            assertEquals(1, f.git.syncs, "opening the app syncs")

            f.controller.noteSaved()
            advanceTimeBy(40_000)
            f.controller.noteSaved() // a second save restarts the wait
            advanceTimeBy(59_000)
            runCurrent()
            assertEquals(1, f.git.syncs)
            advanceTimeBy(1_001)
            assertEquals(2, f.git.syncs)

            f.vaultChanges.emit(Unit) // a rename counts as an edit
            advanceTimeBy(60_001)
            assertEquals(3, f.git.syncs)
        }

    @Test
    fun `without auto-sync only sync now syncs`() =
        runTest {
            val f = fixture(autoSync = emptySet())
            f.controller.onAppStart()
            f.controller.noteSaved()
            advanceTimeBy(120_000)
            f.controller.onAppStop()
            runCurrent()
            assertEquals(0, f.git.syncs)
            assertEquals(emptyList<String>(), f.backgroundPushes)
            assertFalse(f.controller.status.value.autoSync)

            f.controller.syncNow()
            runCurrent()
            assertEquals(1, f.git.syncs)
        }

    @Test
    fun `app start before the vault is known syncs once it is`() =
        runTest {
            val f = fixture(vault = null)
            f.controller.onAppStart()
            runCurrent()
            f.openVault.value = cloned
            runCurrent()
            assertEquals(1, f.git.syncs)
        }

    @Test
    fun `leaving the app with unsynced edits starts the background job`() =
        runTest {
            val f = fixture()
            f.controller.onAppStart()
            runCurrent()
            f.controller.onAppStop()
            runCurrent()
            assertEquals(emptyList<String>(), f.backgroundPushes, "nothing changed since the sync")

            f.controller.onAppStart()
            runCurrent()
            f.controller.noteSaved()
            runCurrent()
            val syncsBefore = f.git.syncs
            f.controller.onAppStop()
            advanceTimeBy(120_000)
            assertEquals(listOf(cloned.id), f.backgroundPushes)
            assertEquals(syncsBefore, f.git.syncs, "the 60 s wait was cancelled")

            // A save flushed after ON_STOP goes straight to the background job.
            f.controller.noteSaved()
            runCurrent()
            assertEquals(listOf(cloned.id, cloned.id), f.backgroundPushes)

            assertTrue(f.controller.pushQuickly(File("/x"), null))
            assertEquals(1, f.git.quickPushes)
        }

    @Test
    fun `errors and conflicts are shown`() =
        runTest {
            val f = fixture()
            f.git.error = CoreError.Git("Git couldn't sign in to the remote.")
            f.controller.syncNow()
            runCurrent()
            assertEquals(SyncPhase.Error, f.controller.status.value.phase)
            assertEquals("Git couldn't sign in to the remote.", f.controller.status.value.message)
            assertNull(f.controller.status.value.lastSyncMillis)
            assertEquals(0, f.refreshes)

            f.git.error = null
            f.git.report = SyncReport(conflicts = listOf(VaultPath("Plan (conflict 2026-10-10).md")))
            f.controller.syncNow()
            runCurrent()
            val status = f.controller.status.value
            assertEquals(SyncPhase.UpToDate, status.phase)
            assertEquals(
                "A note was changed on two devices. Both versions are kept: “Plan (conflict 2026-10-10).md”.",
                status.message,
            )
        }

    @Test
    fun `a second sync now while one runs is ignored`() =
        runTest {
            val f = fixture()
            f.controller.syncNow()
            f.controller.syncNow()
            runCurrent()
            assertEquals(1, f.git.syncs)
        }

    @Test
    fun `a picked folder can't sync`() =
        runTest {
            val f = fixture(vault = picked, autoSync = setOf(picked.id))
            val status = f.controller.status.value
            assertTrue(status.loaded)
            assertFalse(status.connected)
            f.controller.onAppStart()
            f.controller.syncNow()
            f.controller.noteSaved()
            advanceTimeBy(120_000)
            assertEquals(0, f.git.syncs)
        }
}
