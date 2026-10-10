/**
 * Git sync of the open vault (SPEC 7.1, ARCHITECTURE §9.3): the status the SYNC tab shows, the
 * **Sync now** action, and auto-sync timing.
 *
 * Auto-sync (only for vaults in `Settings.syncedVaults`):
 * - when the app comes to the front ([onAppStart]);
 * - 60 s after the last save while the app is open ([noteSaved], also called for file operations);
 * - when the app goes to the background ([onAppStop]), a background job commits and pushes.
 *
 * It lives as long as the app (built in `AppContainer`). It has no Android code: everything it
 * needs comes in through the constructor, so tests can pass fakes and a test dispatcher.
 */
package com.kunotes.sync

import com.kunotes.core.CoreError
import com.kunotes.core.GitCredentials
import com.kunotes.core.SyncReport
import com.kunotes.core.VaultPath
import com.kunotes.settings.SavedVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where a sync stands. */
enum class SyncPhase { Idle, Syncing, UpToDate, Error }

/** What the SYNC tab shows about the open vault. */
data class SyncStatus(
    val vaultId: String? = null,
    /** False until we know whether the vault has a git remote. */
    val loaded: Boolean = false,
    /** True when the vault is a git folder in app storage with a remote: it can sync. */
    val connected: Boolean = false,
    val remoteUrl: String? = null,
    val autoSync: Boolean = false,
    val phase: SyncPhase = SyncPhase.Idle,
    /** The error of the last sync (in [SyncPhase.Error]), or the conflict copies it made. */
    val message: String? = null,
    /** When the last sync finished (millis since 1970), `null` if never (since the app started). */
    val lastSyncMillis: Long? = null,
    /** The remote branch, "main" usually. */
    val branch: String? = null,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: List<VaultPath> = emptyList(),
)

/** The git calls the controller makes. An interface so tests can fake them; the real one is [JGitSyncGit]. */
interface SyncGit {
    fun remoteUrl(folder: File): String?

    fun sync(
        folder: File,
        credentials: GitCredentials?,
    ): SyncReport

    fun commitAndPushQuickly(
        folder: File,
        credentials: GitCredentials?,
    ): Boolean
}

/** [SyncGit] with JGit, through `:core`'s `GitSync`. */
object JGitSyncGit : SyncGit {
    /** The background job gives up on the network after this long. */
    private const val QUICK_PUSH_TIMEOUT_SECONDS = 20

    override fun remoteUrl(folder: File) =
        com.kunotes.core.GitSync
            .remoteUrl(folder)

    override fun sync(
        folder: File,
        credentials: GitCredentials?,
    ) = com.kunotes.core.GitSync
        .sync(folder, credentials)

    override fun commitAndPushQuickly(
        folder: File,
        credentials: GitCredentials?,
    ) = com.kunotes.core.GitSync
        .commitAndPushQuickly(folder, credentials, QUICK_PUSH_TIMEOUT_SECONDS)
}

/**
 * @param openVault the open vault (from `VaultStore.state`).
 * @param autoSyncVaults ids of vaults with auto-sync on (from `Settings.syncedVaults`).
 * @param vaultChanges emits when the app changed files (trash, rename, move): counts as an edit.
 * @param credentials the saved sign-in of a vault, or `null`.
 * @param folderOf the folder git works in, or `null` for a picked (SAF) folder.
 * @param afterSync called after each sync that worked (rescans the file tree).
 * @param scheduleBackgroundPush starts the background job for a vault (WorkManager).
 * @param io where git runs; tests pass their test dispatcher.
 */
class SyncController(
    private val scope: CoroutineScope,
    openVault: Flow<SavedVault?>,
    autoSyncVaults: Flow<Set<String>>,
    vaultChanges: Flow<Unit>,
    private val git: SyncGit,
    private val credentials: suspend (vaultId: String) -> GitCredentials?,
    private val folderOf: (SavedVault) -> File?,
    private val afterSync: () -> Unit,
    private val scheduleBackgroundPush: (vaultId: String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _changedFiles = MutableSharedFlow<List<VaultPath>>(extraBufferCapacity = 4)

    /** Files a sync changed on this device, so an open note can reload them. */
    val changedFiles: SharedFlow<List<VaultPath>> = _changedFiles.asSharedFlow()

    /** One git command at a time, also across the foreground sync and the background job. */
    private val gitLock = Mutex()

    private var vault: SavedVault? = null
    private var autoSyncIds: Set<String> = emptySet()
    private val lastSync = mutableMapOf<String, Long>()

    private var loadJob: Job? = null
    private var syncJob: Job? = null
    private var afterEditJob: Job? = null

    /** Something was saved since the last sync started. */
    private var dirty = false
    private var inForeground = false

    /** The app came to the front before the vault's status was known: sync once it is. */
    private var syncWhenLoaded = false

    private val autoSyncOn: Boolean get() = vault?.id?.let { it in autoSyncIds } == true && _status.value.connected

    init {
        scope.launch {
            // `combine` emits whenever either flow changes, with the latest value of both.
            combine(openVault, autoSyncVaults) { open, auto -> open to auto }.collect { (open, auto) ->
                autoSyncIds = auto
                if (open?.id != vault?.id) onVaultChanged(open)
                _status.update { it.copy(autoSync = open != null && open.id in auto) }
            }
        }
        scope.launch { vaultChanges.collect { noteSaved() } }
    }

    /** Syncs the open vault now (the **Sync now** button). Does nothing while a sync runs. */
    fun syncNow() {
        scope.launch { startSync() }
    }

    /** Call after the editor saved a note: auto-sync waits 60 s after the last save. */
    fun noteSaved() {
        scope.launch {
            dirty = true
            val id = vault?.id
            if (!autoSyncOn || id == null) return@launch
            if (!inForeground) {
                // A save that lands after the app went away (pending saves are flushed on ON_STOP).
                scheduleBackgroundPush(id)
                return@launch
            }
            afterEditJob?.cancel()
            afterEditJob =
                scope.launch {
                    delay(AFTER_EDIT_MILLIS)
                    startSync()
                }
        }
    }

    /** The app came to the front (`ON_START`). */
    fun onAppStart() {
        scope.launch {
            inForeground = true
            if (!_status.value.loaded) {
                syncWhenLoaded = true
            } else if (autoSyncOn) {
                startSync()
            }
        }
    }

    /** The app went to the background (`ON_STOP`): unsynced edits go out in a background job. */
    fun onAppStop() {
        scope.launch {
            inForeground = false
            syncWhenLoaded = false
            afterEditJob?.cancel()
            val id = vault?.id
            if (dirty && autoSyncOn && id != null) scheduleBackgroundPush(id)
        }
    }

    /** Reads the open vault's remote again (e.g. after the sign-in changed). */
    fun reload() {
        scope.launch { vault?.let { load(it) } }
    }

    /**
     * For the background job: commits and pushes [folder] with a short network timeout.
     * Returns `true` if the push went through.
     */
    suspend fun pushQuickly(
        folder: File,
        credentials: GitCredentials?,
    ): Boolean =
        try {
            gitLock.withLock { withContext(io) { git.commitAndPushQuickly(folder, credentials) } }
        } catch (error: CoreError) {
            false // The commit (if any) is safe locally; the next sync sends it.
        }

    // ---- Helpers ----

    private fun onVaultChanged(open: SavedVault?) {
        vault = open
        loadJob?.cancel()
        afterEditJob?.cancel()
        dirty = false
        _status.value = SyncStatus(vaultId = open?.id, lastSyncMillis = open?.let { lastSync[it.id] })
        if (open != null) loadJob = scope.launch { load(open) }
    }

    /** Finds out whether [open] has a remote, then runs a sync the app start asked for. */
    private suspend fun load(open: SavedVault) {
        val folder = folderOf(open)
        val remote =
            folder?.let {
                try {
                    withContext(io) { git.remoteUrl(it) }
                } catch (error: CoreError) {
                    null // Not a readable git folder: shown as "not synced".
                }
            }
        if (vault?.id != open.id) return
        _status.update { it.copy(loaded = true, connected = remote != null, remoteUrl = remote) }
        if (syncWhenLoaded) {
            syncWhenLoaded = false
            if (autoSyncOn) startSync()
        }
    }

    private fun startSync() {
        val open = vault ?: return
        val folder = folderOf(open) ?: return
        if (!_status.value.connected || syncJob?.isActive == true) return
        afterEditJob?.cancel()
        syncJob = scope.launch { runSync(open, folder) }
    }

    private suspend fun runSync(
        open: SavedVault,
        folder: File,
    ) {
        dirty = false // the sync commits everything saved until now
        _status.update { it.copy(phase = SyncPhase.Syncing, message = null) }
        val report =
            try {
                val signIn = credentials(open.id)
                gitLock.withLock { withContext(io) { git.sync(folder, signIn) } }
            } catch (error: CancellationException) {
                throw error // never swallow cancellation
            } catch (error: CoreError) {
                failed(open, error.message ?: "Sync failed.")
                return
            } catch (error: RuntimeException) {
                // JGit can throw unchecked errors too; show them instead of crashing the app.
                failed(open, "Sync failed (${error.javaClass.simpleName}).")
                return
            }
        val now = clock()
        lastSync[open.id] = now
        if (vault?.id != open.id) return // another vault was opened meanwhile
        _status.update {
            it.copy(
                phase = SyncPhase.UpToDate,
                message = conflictMessage(report.conflicts),
                lastSyncMillis = now,
                branch = report.branch ?: it.branch,
                pushed = report.pushed.size,
                pulled = report.changed.size,
                conflicts = report.conflicts,
            )
        }
        afterSync()
        if (report.changed.isNotEmpty()) _changedFiles.emit(report.changed)
    }

    private fun failed(
        open: SavedVault,
        message: String,
    ) {
        dirty = true // what was saved still has to go out
        if (vault?.id == open.id) _status.update { it.copy(phase = SyncPhase.Error, message = message) }
    }

    private fun conflictMessage(conflicts: List<VaultPath>): String? {
        if (conflicts.isEmpty()) return null
        val names = conflicts.joinToString(", ") { "“${it.name}”" }
        val what = if (conflicts.size == 1) "A note was" else "${conflicts.size} notes were"
        return "$what changed on two devices. Both versions are kept: $names."
    }

    companion object {
        /** Auto-sync runs this long after the last save (desktop `AFTER_EDIT`). */
        const val AFTER_EDIT_MILLIS = 60_000L
    }
}
