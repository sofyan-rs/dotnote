/**
 * The Files tab, and the empty vault when there are no notes yet.
 *
 * Shows the open-notes row and the vault tree. The ⋮ of a row (or a long press) opens the
 * note or folder actions sheet; the floating button and the header icon open "New item".
 * Deleting shows a snackbar with UNDO (2.6).
 */
package com.dotnote.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.DotArt
import com.dotnote.ui.dotline.DotArtPatterns
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineSnackbarHost
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.MainTab
import com.dotnote.ui.dotline.NewNoteFab
import com.dotnote.ui.dotline.OpenNoteCard
import com.dotnote.ui.dotline.OpenNotesRow
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.ScreenTitle
import com.dotnote.ui.dotline.ScreenTitleAction
import com.dotnote.ui.dotline.SecondaryButton
import com.dotnote.ui.dotline.SectionLabel
import com.dotnote.ui.dotline.TreeRow
import com.dotnote.ui.dotline.showDotlineSnackbar
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.navigation.LocalBottomNavSpace
import com.dotnote.ui.navigation.MainTabs
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.vaults.VaultSwitcherSheet
import com.dotnote.ui.welcome.OpenFolderFlow
import com.dotnote.vault.VaultEvent

/** Which sheet is open on the Files screen, if any. */
sealed interface FilesSheet {
    data class NewItem(
        val dir: VaultPath,
        val folder: Boolean,
    ) : FilesSheet

    data class FolderActions(
        val path: VaultPath,
    ) : FilesSheet

    data class NoteActions(
        val path: VaultPath,
    ) : FilesSheet

    data class Rename(
        val path: VaultPath,
    ) : FilesSheet

    /** Long press on an open-note card: pin, close, close others. */
    data class OpenNoteActions(
        val path: VaultPath,
    ) : FilesSheet
}

@Composable
fun FilesScreen(
    onOpenNote: (VaultPath) -> Unit,
    onMoveItem: (VaultPath) -> Unit,
    onManageVaults: () -> Unit,
    onCloneFromGit: () -> Unit,
    onSearch: () -> Unit,
    onLockNote: (VaultPath) -> Unit,
) {
    val viewModel = dotnoteViewModel { FilesViewModel(it.vaultStore) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<FilesSheet?>(null) }
    var vaultSwitcherOpen by remember { mutableStateOf(false) }
    var openFolderOpen by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Rescan when the screen appears, and poll for outside changes while it's visible.
    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }

    // One-time events from the store: errors and "moved to trash · UNDO".
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultEvent.Error -> snackbars.showDotlineSnackbar(event.message, actionLabel = null)
                is VaultEvent.Trashed -> {
                    val name =
                        com.dotnote.core.Paths
                            .noteTitle(event.item.originalPath)
                    val result = snackbars.showDotlineSnackbar("“$name” moved to trash", actionLabel = "UNDO")
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash(event.item)
                }
                is VaultEvent.Moved -> Unit
            }
        }
    }

    val openNote = { path: VaultPath ->
        viewModel.openNote(path)
        onOpenNote(path)
    }

    MainTabs(
        overlay = {
            Column(Modifier.fillMaxWidth().padding(Dotline.space.s4), horizontalAlignment = Alignment.End) {
                DotlineSnackbarHost(snackbars, icon = DotlineIcons.Trash)
                if (!ui.isEmpty && ui.loaded) {
                    Spacer(Modifier.height(Dotline.space.s3))
                    NewNoteFab(onClick = { sheet = FilesSheet.NewItem(VaultPath.ROOT, folder = false) })
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(
                title = ui.vaultName.uppercase(),
                onTitleClick = { vaultSwitcherOpen = true },
                subline = if (ui.loaded) "${ui.noteCount} ${if (ui.noteCount == 1) "note" else "notes"}" else "Reading…",
                actions = {
                    ScreenTitleAction(DotlineIcons.Search, "Search notes", onSearch)
                    ScreenTitleAction(DotlineIcons.NewFolder, "New folder", { sheet = FilesSheet.NewItem(VaultPath.ROOT, folder = true) })
                },
            )
            if (ui.isEmpty) {
                EmptyVault(
                    onNewNote = { sheet = FilesSheet.NewItem(VaultPath.ROOT, folder = false) },
                    onNewFolder = { sheet = FilesSheet.NewItem(VaultPath.ROOT, folder = true) },
                )
            } else {
                FileList(
                    ui = ui,
                    onOpenNote = openNote,
                    onCardActions = { card -> sheet = FilesSheet.OpenNoteActions(card.path) },
                    onToggleFolder = viewModel::toggleFolder,
                    onRowActions = { row ->
                        sheet = if (row.isFolder) FilesSheet.FolderActions(row.path) else FilesSheet.NoteActions(row.path)
                    },
                )
            }
        }
    }

    if (vaultSwitcherOpen) {
        VaultSwitcherSheet(
            onManage = onManageVaults,
            onOpenFolder = {
                vaultSwitcherOpen = false
                openFolderOpen = true
            },
            onCloneFromGit = {
                vaultSwitcherOpen = false
                onCloneFromGit()
            },
            onDismiss = { vaultSwitcherOpen = false },
        )
    }
    OpenFolderFlow(open = openFolderOpen, lostAccessTo = null, onDismiss = { openFolderOpen = false }, onVaultOpened = {})

    when (val current = sheet) {
        null -> Unit
        is FilesSheet.NewItem ->
            NewItemSheet(
                initialDir = current.dir,
                initialFolder = current.folder,
                folders = viewModel.allFolders(),
                existingNames = { dir ->
                    viewModel
                        .node(dir)
                        ?.children
                        ?.map { it.name }
                        .orEmpty()
                },
                onCreate = { dir, name, folder ->
                    if (folder) {
                        viewModel.createFolder(dir, name) { sheet = null }
                    } else {
                        viewModel.createNote(dir, name) { created ->
                            sheet = null
                            openNote(created)
                        }
                    }
                },
                onDismiss = { sheet = null },
            )
        is FilesSheet.FolderActions ->
            FolderActionsSheet(
                node = viewModel.node(current.path),
                onNewNote = { sheet = FilesSheet.NewItem(current.path, folder = false) },
                onNewFolder = { sheet = FilesSheet.NewItem(current.path, folder = true) },
                onCopyPath = { copyPath(context, current.path) },
                onRename = { sheet = FilesSheet.Rename(current.path) },
                onMove = {
                    sheet = null
                    onMoveItem(current.path)
                },
                onTrash = {
                    sheet = null
                    viewModel.trash(current.path)
                },
                onDismiss = { sheet = null },
            )
        is FilesSheet.NoteActions ->
            NoteActionsSheet(
                path = current.path,
                charCount = viewModel.charCount(current.path),
                pinned = ui.openNotes.isNotEmpty() && viewModel.isPinned(current.path),
                onPin = {
                    viewModel.togglePin(current.path)
                    sheet = null
                },
                onCopyPath = { copyPath(context, current.path) },
                onShare = {
                    sheet = null
                    viewModel.share(context, current.path)
                },
                onRename = { sheet = FilesSheet.Rename(current.path) },
                onMove = {
                    sheet = null
                    onMoveItem(current.path)
                },
                onLock = {
                    sheet = null
                    onLockNote(current.path)
                },
                onTrash = {
                    sheet = null
                    viewModel.trash(current.path)
                },
                onDismiss = { sheet = null },
            )
        is FilesSheet.OpenNoteActions -> {
            val card = ui.openNotes.firstOrNull { it.path == current.path }
            OpenNoteActionsSheet(
                title = card?.title ?: current.path.name,
                pinned = card?.isPinned == true,
                onPin = { viewModel.togglePin(current.path) },
                onClose = { viewModel.closeOpenNote(current.path) },
                onCloseOthers = { viewModel.closeOtherOpenNotes(current.path) },
                onCloseAll = { viewModel.closeUnpinnedOpenNotes() },
                onDismiss = { sheet = null },
            )
        }
        is FilesSheet.Rename ->
            RenameSheet(
                path = current.path,
                isFolder = viewModel.node(current.path)?.isDir == true,
                existingNames =
                    viewModel
                        .node(current.path.parent ?: VaultPath.ROOT)
                        ?.children
                        ?.map { it.name }
                        .orEmpty(),
                onRename = { newName -> viewModel.rename(current.path, newName) { sheet = null } },
                onDismiss = { sheet = null },
            )
    }
}

@Composable
private fun FileList(
    ui: FilesUi,
    onOpenNote: (VaultPath) -> Unit,
    onCardActions: (OpenNoteUi) -> Unit,
    onToggleFolder: (VaultPath) -> Unit,
    onRowActions: (TreeRowUi) -> Unit,
) {
    // Bottom padding keeps the last row clear of the floating nav and the "New note" button above it.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomNavSpace.current + 96.dp)) {
        if (ui.openNotes.isNotEmpty()) {
            item(key = "open-notes") {
                Column(Modifier.padding(top = Dotline.space.s5), verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                    SectionLabel("OPEN NOTES", Modifier.padding(horizontal = Dotline.space.s4))
                    OpenNotesRow(ui.openNotes, key = { it.path.value }) { card ->
                        OpenNoteCard(
                            name = card.title,
                            caption = card.caption,
                            onClick = { onOpenNote(card.path) },
                            icon = if (card.isLocked) DotlineIcons.Lock else DotlineIcons.Note,
                            isCurrent = card.isCurrent,
                            isPinned = card.isPinned,
                            onLongClick = { onCardActions(card) },
                            onLongClickLabel = "Pin or close",
                        )
                    }
                }
            }
        }
        item(key = "vault-label") {
            SectionLabel(
                "VAULT",
                Modifier.padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s5, bottom = Dotline.space.s2),
            )
        }
        item(key = "tree") {
            // One rounded group around all rows. Fine for typical vaults; a very large vault
            // would be better as separate lazy items.
            ListGroup(Modifier.padding(horizontal = Dotline.space.s4)) {
                ui.rows.forEachIndexed { index, row ->
                    if (index > 0) ListDivider()
                    TreeRow(
                        name = row.name,
                        depth = row.depth,
                        isFolder = row.isFolder,
                        onClick = { if (row.isFolder) onToggleFolder(row.path) else onOpenNote(row.path) },
                        expanded = row.expanded,
                        count = row.count,
                        subtitle = row.subtitle,
                        isLocked = row.isLocked,
                        selected = row.selected,
                        onLongClick = { onRowActions(row) },
                        onMoreClick = { onRowActions(row) },
                    )
                }
            }
        }
    }
}

/** No notes yet. */
@Composable
private fun EmptyVault(
    onNewNote: () -> Unit,
    onNewFolder: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dotline.space.s6, end = Dotline.space.s6, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s5, Alignment.CenterVertically),
    ) {
        DotArt(DotArtPatterns.EmptyFolder, contentDescription = null)
        Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            Text("No notes yet", style = Dotline.type.title, color = Dotline.colors.ink)
            Text(
                "Write your first note, or add .md files to this folder from another app. They show up here on their own.",
                style = Dotline.type.body,
                color = Dotline.colors.inkMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            PrimaryButton("New note", onClick = onNewNote, icon = DotlineIcons.Plus, compact = true)
            SecondaryButton("New folder", onClick = onNewFolder, compact = true)
        }
    }
}
