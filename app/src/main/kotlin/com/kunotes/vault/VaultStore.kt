/**
 * The open vault: its file tree, which folders are expanded, the open notes, and every file
 * operation (create, rename, move, trash, undo). The single source of truth for the vault
 * (ARCHITECTURE §4.1); screens read [state] and call the functions here.
 *
 * It lives as long as the app (in [com.kunotes.AppContainer]), not as long as one screen,
 * so leaving a screen never cancels a file operation halfway.
 *
 * Threading: every disk access runs on `Dispatchers.IO`. File operations go one at a time
 * (a [Mutex]), so two of them never touch the same files at once.
 */
package com.kunotes.vault

import com.kunotes.core.CoreError
import com.kunotes.core.FsOps
import com.kunotes.core.NoteNames
import com.kunotes.core.OpenNote
import com.kunotes.core.OpenNotes
import com.kunotes.core.Paths
import com.kunotes.core.Search
import com.kunotes.core.Trash
import com.kunotes.core.TrashedItem
import com.kunotes.core.VaultFs
import com.kunotes.core.VaultNode
import com.kunotes.core.VaultPath
import com.kunotes.settings.SavedOpenNote
import com.kunotes.settings.SavedVault
import com.kunotes.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the screens show about the open vault. */
data class VaultState(
    val vault: SavedVault? = null,
    /** The file tree; `null` until the first scan is done. */
    val root: VaultNode? = null,
    val expanded: Set<VaultPath> = emptySet(),
    val selected: VaultPath? = null,
    val openNotes: OpenNotes = OpenNotes(),
    /** Character counts of notes, filled in after each scan (locked notes have none). */
    val charCounts: Map<VaultPath, Int> = emptyMap(),
) {
    val noteCount: Int get() = root?.let { Search.flattenFiles(it).size } ?: 0
}

/** One-time things that happened, for snackbars and for screens that show a moved note. */
sealed class VaultEvent {
    data class Error(
        val message: String,
    ) : VaultEvent()

    data class Trashed(
        val item: TrashedItem,
    ) : VaultEvent()

    /** A file or folder now lives at [new] (renamed, moved, or restored). */
    data class Moved(
        val old: VaultPath,
        val new: VaultPath,
    ) : VaultEvent()
}

class VaultStore(
    private val registry: VaultRegistry,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<VaultEvent> = _events.asSharedFlow()

    /** The open vault's files; `null` when no vault is open. */
    var fs: VaultFs? = null
        private set

    private val fileLock = Mutex()
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshAgain = false
    private var lastScanMillis = 0L

    /** Remembers counted notes so a rescan only reads files that changed. */
    private val countCache = java.util.concurrent.ConcurrentHashMap<VaultPath, Pair<Long, Int>>()

    // ---- Opening ----

    /** Opens [vault]: fixes leftovers of interrupted saves, empties old trash, and scans it. */
    fun open(vault: SavedVault) {
        val vaultFs = registry.openFs(vault)
        fs = vaultFs
        countCache.clear()
        _state.value = VaultState(vault = vault)
        scope.launch {
            val saved = settingsStore.current().openNotes[vault.id].orEmpty()
            val openNotes = OpenNotes(saved.map { OpenNote(VaultPath(it.path), it.pinned) })
            _state.update { it.copy(openNotes = openNotes) }
            settingsStore.update { it.copy(lastVaultId = vault.id) }
            fileOp {
                FsOps.recoverInterruptedWrites(vaultFs)
                Trash.purge(vaultFs)
            }
            refresh()
        }
    }

    /** Closes the vault (e.g. when it's removed from the list). */
    fun close() {
        stopPolling()
        fs = null
        _state.value = VaultState()
    }

    /**
     * Scans the vault again and updates the tree. Safe to call often: while a scan runs, more
     * calls only ask for one more scan after it (scans never overlap).
     */
    fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshAgain = true
            return
        }
        val vaultFs = fs ?: return
        val vaultId = _state.value.vault?.id
        refreshJob =
            scope.launch {
                val started = System.currentTimeMillis()
                val root = withContext(Dispatchers.IO) { VaultNode.scan(vaultFs) }
                lastScanMillis = System.currentTimeMillis() - started
                android.util.Log.d("VaultStore", "Scanned ${Search.flattenFiles(root).size} notes in $lastScanMillis ms")
                if (_state.value.vault?.id != vaultId) return@launch // another vault was opened meanwhile
                _state.update { state ->
                    state.copy(root = root, openNotes = keepExisting(state.openNotes, root))
                }
                countCharacters(vaultFs)
            }
        refreshJob?.invokeOnCompletion {
            if (refreshAgain) {
                refreshAgain = false
                scope.launch { refresh() }
            }
        }
    }

    /**
     * Rescans while a screen showing the tree is visible (ARCHITECTURE §7.4): every 10 s, or
     * less often on big vaults (at least 3× the last scan time), so polling never keeps the
     * phone busy.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            scope.launch {
                while (isActive) {
                    delay(maxOf(POLL_MILLIS, lastScanMillis * 3))
                    refresh()
                }
            }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ---- Tree state ----

    fun toggle(folder: VaultPath) {
        _state.update { state ->
            val expanded = if (folder in state.expanded) state.expanded - folder else state.expanded + folder
            state.copy(expanded = expanded)
        }
        // Notes that just became visible need their character counts.
        fs?.let { vaultFs -> scope.launch { countCharacters(vaultFs) } }
    }

    fun select(path: VaultPath?) {
        _state.update { it.copy(selected = path) }
    }

    // ---- Open notes ----

    /** Marks [note] as open (moves it to the front of the open-notes row). */
    fun openNote(note: VaultPath) {
        updateOpenNotes { it.open(note) }
        select(note)
    }

    fun togglePin(note: VaultPath) = updateOpenNotes { it.togglePin(note) }

    fun closeNote(note: VaultPath) = updateOpenNotes { it.close(note) }

    // ---- File operations (each returns the new path, or null after showing an error) ----

    suspend fun createNote(
        dir: VaultPath,
        name: String?,
    ): VaultPath? =
        fileOp { fs ->
            if (name.isNullOrBlank()) FsOps.createFile(fs, dir) else FsOps.createFileNamed(fs, dir, name)
        }?.also { created ->
            expandToShow(created)
            refresh()
        }

    suspend fun createFolder(
        dir: VaultPath,
        name: String?,
    ): VaultPath? =
        fileOp { fs ->
            if (name.isNullOrBlank()) FsOps.createFolder(fs, dir) else FsOps.createFolderNamed(fs, dir, name)
        }?.also { created ->
            expandToShow(created)
            refresh()
        }

    suspend fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath? = fileOp { fs -> FsOps.rename(fs, path, newName) }?.also { moved(path, it) }

    suspend fun move(
        path: VaultPath,
        folder: VaultPath,
    ): VaultPath? = fileOp { fs -> FsOps.moveInto(fs, path, folder) }?.also { moved(path, it) }

    /** Moves [path] to the vault's trash and offers UNDO through [VaultEvent.Trashed]. */
    suspend fun trash(path: VaultPath): TrashedItem? =
        fileOp { fs -> Trash.trash(fs, path) }?.also { item ->
            updateOpenNotes { it.closeInside(path) }
            _state.update { if (it.selected?.isInside(path) == true) it.copy(selected = null) else it }
            _events.emit(VaultEvent.Trashed(item))
            refresh()
        }

    suspend fun undoTrash(item: TrashedItem): VaultPath? =
        fileOp { fs -> Trash.restore(fs, item) }?.also { restored ->
            _events.emit(VaultEvent.Moved(item.trashPath, restored))
            refresh()
        }

    /** Every folder of the vault (for "Move to…" and location pickers), root first. */
    fun allFolders(): List<VaultPath> {
        val root = _state.value.root ?: return listOf(VaultPath.ROOT)
        val folders = mutableListOf(VaultPath.ROOT)

        fun walk(node: VaultNode) {
            node.children.filter { it.isDir }.forEach {
                folders += it.path
                walk(it)
            }
        }
        walk(root)
        return folders
    }

    /** Shows an error from elsewhere (editor, lock, sync) in the same snackbar. */
    suspend fun reportError(message: String) {
        _events.emit(VaultEvent.Error(message))
    }

    // ---- Helpers ----

    /**
     * Runs one file operation on the IO thread, one at a time. A [CoreError] becomes a
     * [VaultEvent.Error] (shown as a snackbar) and the result is `null`.
     */
    private suspend fun <T> fileOp(block: (VaultFs) -> T): T? {
        val vaultFs = fs ?: return null
        return try {
            // NonCancellable: once started, a file operation always finishes, even if the screen
            // that asked for it closes. A half-done rename or move is worse than a late one.
            fileLock.withLock { withContext(NonCancellable + Dispatchers.IO) { block(vaultFs) } }
        } catch (error: CoreError) {
            _events.emit(VaultEvent.Error(error.message ?: "Something went wrong"))
            null
        }
    }

    /** After a rename or move: open notes, selection and expanded folders follow the item. */
    private suspend fun moved(
        old: VaultPath,
        new: VaultPath,
    ) {
        if (old == new) return
        _state.update { state ->
            state.copy(
                openNotes = state.openNotes.remap(old, new),
                selected = state.selected?.let { Paths.remapPath(it, old, new) ?: it },
                expanded = state.expanded.map { Paths.remapPath(it, old, new) ?: it }.toSet(),
            )
        }
        saveOpenNotes()
        expandToShow(new)
        _events.emit(VaultEvent.Moved(old, new))
        refresh()
    }

    /** Expands the folders above [path] so it's visible in the tree. */
    private fun expandToShow(path: VaultPath) {
        val parents = generateSequence(path.parent) { it.parent }.filter { !it.isRoot }.toSet()
        _state.update { it.copy(expanded = it.expanded + parents) }
    }

    private fun updateOpenNotes(change: (OpenNotes) -> OpenNotes) {
        _state.update { it.copy(openNotes = change(it.openNotes)) }
        scope.launch { saveOpenNotes() }
    }

    private suspend fun saveOpenNotes() {
        val vaultId = _state.value.vault?.id ?: return
        val saved =
            _state.value.openNotes.entries
                .map { SavedOpenNote(it.path.value, it.pinned) }
        settingsStore.update { it.copy(openNotes = it.openNotes + (vaultId to saved)) }
    }

    /** Drops open notes whose file is gone (deleted outside the app). */
    private fun keepExisting(
        openNotes: OpenNotes,
        root: VaultNode,
    ): OpenNotes {
        val existing = Search.flattenFiles(root).map { it.path }.toSet()
        return OpenNotes(openNotes.entries.filter { it.path in existing })
    }

    /**
     * Counts characters of the notes visible in the tree (for "645 chars"). Only visible notes,
     * because reading every note of a big vault on each scan costs too much; counts are cached
     * by modification time, so unchanged notes aren't read again.
     */
    private suspend fun countCharacters(vaultFs: VaultFs) {
        val state = _state.value
        val root = state.root ?: return
        val visible =
            Search
                .visibleRows(root, state.expanded)
                .filter { !it.isDir }
                .map { it.path }
                .toSet()
        val notes = Search.flattenFiles(root).filter { it.path in visible }
        val counts =
            withContext(Dispatchers.IO) {
                val result = mutableMapOf<VaultPath, Int>()
                for (note in notes) {
                    if (NoteNames.isLockedNote(note.path) || note.size > MAX_COUNTED_BYTES) continue
                    val cached = countCache[note.path]
                    if (cached != null && cached.first == note.lastModified) {
                        result[note.path] = cached.second
                        continue
                    }
                    val count =
                        try {
                            com.kunotes.core.Cursor
                                .charCount(vaultFs.read(note.path).decodeToString())
                        } catch (_: CoreError) {
                            continue // deleted or unreadable since the scan: no count
                        }
                    countCache[note.path] = note.lastModified to count
                    result[note.path] = count
                }
                result
            }
        _state.update { it.copy(charCounts = it.charCounts + counts) }
    }

    private companion object {
        const val POLL_MILLIS = 10_000L

        /** Bigger notes skip the count (reading them on every scan would cost too much). */
        const val MAX_COUNTED_BYTES = 512 * 1024
    }
}
