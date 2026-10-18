/**
 * The quick switcher's logic: note names that contain the query (letter case
 * ignored), in natural order, with where the match is so the screen can highlight it.
 */
package com.dotnote.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.core.NaturalOrder
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.Search
import com.dotnote.core.VaultPath
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.launch

/** One search result. */
data class SearchMatch(
    val path: VaultPath,
    val title: String,
    /** The matched part of [title] (start until end), for highlighting. Empty for no query. */
    val matchStart: Int,
    val matchEnd: Int,
    /** The folder, like "/Projects/Archive" ("/" for the vault root). */
    val folder: String,
    val isLocked: Boolean,
)

class SearchViewModel(
    private val vaultStore: VaultStore,
) : ViewModel() {
    val state = vaultStore.state

    /** Notes whose name contains [query]. */
    fun matches(query: String): List<SearchMatch> {
        val root = vaultStore.state.value.root ?: return emptyList()
        val needle = query.trim()
        return Search
            .filterFiles(Search.flattenFiles(root), needle)
            .map { node ->
                val title = Paths.noteTitle(node.path)
                val start = if (needle.isEmpty()) -1 else title.indexOf(needle, ignoreCase = true)
                SearchMatch(
                    path = node.path,
                    title = title,
                    matchStart = maxOf(start, 0),
                    matchEnd = if (start < 0) 0 else start + needle.length,
                    folder = "/" + (node.path.parent?.value ?: ""),
                    isLocked = NoteNames.isLockedNote(node.path),
                )
            }.sortedWith(compareBy(NaturalOrder) { it.title })
    }

    /** Recently opened notes, for the chips shown before typing. */
    fun recent(): List<VaultPath> = vaultStore.state.value.openNotes.paths

    fun openNote(path: VaultPath) = vaultStore.openNote(path)

    fun createNote(
        name: String,
        onCreated: (VaultPath) -> Unit,
    ) {
        viewModelScope.launch {
            vaultStore.createNote(VaultPath.ROOT, name)?.let {
                vaultStore.openNote(it)
                onCreated(it)
            }
        }
    }
}
