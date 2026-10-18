/**
 * State and actions of the Files tab (SPEC 2.1): turns the [VaultStore]'s tree into the rows
 * and cards the screen draws, and forwards taps to the store.
 */
package com.dotnote.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.Search
import com.dotnote.core.TrashedItem
import com.dotnote.core.VaultNode
import com.dotnote.core.VaultPath
import com.dotnote.ui.RelativeTime
import com.dotnote.vault.VaultEvent
import com.dotnote.vault.VaultState
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Files screen draws. */
data class FilesUi(
    val vaultName: String = "",
    /** False until the first scan is done. */
    val loaded: Boolean = false,
    val noteCount: Int = 0,
    val openNotes: List<OpenNoteUi> = emptyList(),
    val rows: List<TreeRowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = loaded && rows.isEmpty()
}

data class OpenNoteUi(
    val path: VaultPath,
    val title: String,
    /** EDITING, LOCKED, or a time like "1 H AGO". */
    val caption: String,
    val isLocked: Boolean,
    val isCurrent: Boolean,
    val isPinned: Boolean,
)

data class TreeRowUi(
    val path: VaultPath,
    val name: String,
    val depth: Int,
    val isFolder: Boolean,
    val expanded: Boolean,
    /** Items directly inside a folder. */
    val count: Int?,
    val subtitle: String?,
    val isLocked: Boolean,
    val selected: Boolean,
)

class FilesViewModel(
    private val vaultStore: VaultStore,
) : ViewModel() {
    /** `stateIn` turns the mapped flow into a StateFlow the screen can read at any time. */
    val ui: StateFlow<FilesUi> =
        vaultStore.state
            .map(::toUi)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUi(vaultStore.state.value))

    val events: SharedFlow<VaultEvent> = vaultStore.events

    fun onStart() {
        vaultStore.refresh()
        vaultStore.startPolling()
    }

    fun onStop() = vaultStore.stopPolling()

    fun refresh() = vaultStore.refresh()

    fun toggleFolder(path: VaultPath) = vaultStore.toggle(path)

    fun openNote(path: VaultPath) = vaultStore.openNote(path)

    fun togglePin(path: VaultPath) = vaultStore.togglePin(path)

    fun closeOpenNote(path: VaultPath) = vaultStore.closeNote(path)

    fun closeOtherOpenNotes(keep: VaultPath) = vaultStore.closeOtherNotes(keep)

    fun closeUnpinnedOpenNotes() = vaultStore.closeUnpinnedNotes()

    fun createNote(
        dir: VaultPath,
        name: String?,
        onCreated: (VaultPath) -> Unit,
    ) {
        viewModelScope.launch { vaultStore.createNote(dir, name)?.let(onCreated) }
    }

    fun createFolder(
        dir: VaultPath,
        name: String?,
        onCreated: (VaultPath) -> Unit,
    ) {
        viewModelScope.launch { vaultStore.createFolder(dir, name)?.let(onCreated) }
    }

    fun rename(
        path: VaultPath,
        newName: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch { if (vaultStore.rename(path, newName) != null) onDone() }
    }

    fun trash(path: VaultPath) {
        viewModelScope.launch { vaultStore.trash(path) }
    }

    fun undoTrash(item: TrashedItem) {
        viewModelScope.launch { vaultStore.undoTrash(item) }
    }

    fun isPinned(path: VaultPath): Boolean =
        vaultStore.state.value.openNotes.entries
            .any { it.path == path && it.pinned }

    /** Opens Android's share sheet for the note (the file itself, or its text). */
    fun share(
        context: android.content.Context,
        path: VaultPath,
    ) {
        val fs = vaultStore.fs ?: return
        viewModelScope.launch {
            try {
                FileSharing.shareNote(context, fs, path)
            } catch (error: com.dotnote.core.CoreError) {
                vaultStore.reportError(error.message ?: "Couldn't share the note")
            }
        }
    }

    /** The node at [path] in the current tree, for sheet headers. */
    fun node(path: VaultPath): VaultNode? =
        vaultStore.state.value.root
            ?.let { findNode(it, path) }

    fun charCount(path: VaultPath): Int? = vaultStore.state.value.charCounts[path]

    /** Every folder in the vault, for the location picker of the New item sheet. */
    fun allFolders(): List<VaultPath> = vaultStore.allFolders()

    private fun toUi(state: VaultState): FilesUi {
        val root = state.root ?: return FilesUi(vaultName = state.vault?.name.orEmpty())
        val nodes = mutableMapOf<VaultPath, VaultNode>()
        indexNodes(root, nodes)
        val rows =
            Search.visibleRows(root, state.expanded).map { row ->
                val node = nodes[row.path]
                TreeRowUi(
                    path = row.path,
                    name = if (row.isDir) row.name else Paths.noteTitle(row.path),
                    depth = row.depth,
                    isFolder = row.isDir,
                    expanded = row.isExpanded,
                    count = if (row.isDir) node?.children?.size else null,
                    subtitle = if (row.isDir) null else noteSubtitle(row.path, node, state),
                    isLocked = NoteNames.isLockedNote(row.path),
                    selected = row.path == state.selected,
                )
            }
        // The note opened last. Pinned notes sit in front, so the first card isn't always it.
        val current =
            state.selected?.takeIf { selected -> state.openNotes.entries.any { it.path == selected } }
                ?: state.openNotes.entries
                    .firstOrNull()
                    ?.path
        val cards =
            state.openNotes.entries.map { entry ->
                val locked = NoteNames.isLockedNote(entry.path)
                OpenNoteUi(
                    path = entry.path,
                    title = Paths.noteTitle(entry.path),
                    caption =
                        when {
                            locked -> "LOCKED"
                            // The red dot marks the note opened last; the caption says when it changed.
                            else -> RelativeTime.format(nodes[entry.path]?.lastModified ?: 0)?.uppercase() ?: ""
                        },
                    isLocked = locked,
                    isCurrent = entry.path == current,
                    isPinned = entry.pinned,
                )
            }
        return FilesUi(
            vaultName = state.vault?.name.orEmpty(),
            loaded = true,
            noteCount = state.noteCount,
            openNotes = cards,
            rows = rows,
        )
    }

    /** "2 min ago · 645 chars", or "Locked · encrypted". */
    private fun noteSubtitle(
        path: VaultPath,
        node: VaultNode?,
        state: VaultState,
    ): String? {
        if (NoteNames.isLockedNote(path)) return "Locked · encrypted"
        val time = node?.let { RelativeTime.format(it.lastModified) }
        val chars = state.charCounts[path]?.let { "$it chars" }
        return listOfNotNull(time, chars).joinToString(" · ").ifEmpty { null }
    }

    private fun indexNodes(
        node: VaultNode,
        into: MutableMap<VaultPath, VaultNode>,
    ) {
        into[node.path] = node
        node.children.forEach { indexNodes(it, into) }
    }

    private fun findNode(
        node: VaultNode,
        path: VaultPath,
    ): VaultNode? {
        if (node.path == path) return node
        return node.children.firstNotNullOfOrNull { child -> if (path.isInside(child.path)) findNode(child, path) else null }
    }
}
