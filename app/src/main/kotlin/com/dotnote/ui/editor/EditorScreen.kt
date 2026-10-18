/**
 * The note editor screen: connects [EditorViewModel] to [EditorContent], and runs
 * what needs Android: the lifecycle (save on stop, reload on start), the Photo Picker for images,
 * the clipboard, the share sheet, and the rename sheet.
 */
package com.dotnote.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.core.NoteNames
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.DotlineSnackbarHost
import com.dotnote.ui.dotline.FormatAction
import com.dotnote.ui.dotline.showDotlineSnackbar
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.files.RenameSheet
import com.dotnote.ui.files.copyPath
import com.dotnote.ui.lock.SecureWindowEffect
import com.dotnote.ui.rememberAppContainer
import com.dotnote.vault.VaultEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * @param path the note's vault-relative path, from the route.
 * @param onBack leaves the editor (also after the note was moved to the trash).
 * @param onMove opens "Move to…" for the note (its pending edits are saved first).
 * @param onNeedsUnlock the note is locked and the vault key isn't available.
 * @param onLockNote "Lock note" in the menu (handled by the locked-notes feature).
 */
@Composable
fun EditorScreen(
    path: String,
    onBack: () -> Unit,
    onMove: (VaultPath) -> Unit,
    onNeedsUnlock: (VaultPath) -> Unit,
    onLockNote: (VaultPath) -> Unit,
    /** Open another note (a link in Preview). */
    onOpenNote: (VaultPath) -> Unit = {},
) {
    val viewModel =
        dotnoteViewModel(key = "editor:$path") { container ->
            EditorViewModel(VaultPath(path), AppEditorBackend(container), container.appScope)
        }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }

    // ON_START: reload if the file changed outside. ON_STOP (leaving, or app in background): save now.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    LaunchedEffect(ui.isLocked) {
        if (ui.isLocked) onNeedsUnlock(ui.path)
    }
    val container = rememberAppContainer()
    if (NoteNames.isLockedNote(ui.path)) {
        // A decrypted note: no screenshots, blank in Recents (ARCHITECTURE §8.2).
        SecureWindowEffect()
        // When the vault locks itself (5 min unused), leave: the plain text must not stay on screen.
        val unlocked by container.vaultLock.unlocked.collectAsStateWithLifecycle()
        val vaultState by container.vaultStore.state.collectAsStateWithLifecycle()
        LaunchedEffect(unlocked) {
            if (ui.loaded && vaultState.vault?.id !in unlocked) onNeedsUnlock(ui.path)
        }
    }
    // After a git sync changed this note on disk, reload it (if there are no unsaved edits).
    LaunchedEffect(viewModel) {
        container.syncController.changedFiles.collect { changed ->
            if (ui.path in changed) viewModel.onStart()
        }
    }
    // Errors from file operations (rename, image import, share) and from saving.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is VaultEvent.Error) snackbars.showDotlineSnackbar(event.message, actionLabel = null)
        }
    }
    LaunchedEffect(saveState) {
        (saveState as? SaveState.Failed)?.let { snackbars.showDotlineSnackbar("Not saved: ${it.message}", actionLabel = null) }
    }

    // The Android Photo Picker: no storage permission needed, returns a content Uri.
    val pickImage =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val picked = readPickedImage(context, uri)
                    if (picked == null) {
                        snackbars.showDotlineSnackbar("Couldn't read the picture", actionLabel = null)
                    } else {
                        viewModel.insertImage(picked.first, picked.second)
                    }
                }
            }
        }

    val actions =
        EditorActions(
            onBack = onBack,
            onMode = viewModel::setMode,
            onFormat = { action ->
                if (action == FormatAction.Image) {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    viewModel.format(action)
                }
            },
            onToggleTask = viewModel::toggleTask,
            onMenuAction = { action ->
                when (action) {
                    EditorMenuAction.Rename -> renaming = true
                    EditorMenuAction.Move -> viewModel.saveThen(onMove)
                    EditorMenuAction.Pin -> viewModel.togglePin()
                    EditorMenuAction.CopyPath -> copyPath(context, ui.path)
                    EditorMenuAction.Share -> viewModel.share(context)
                    EditorMenuAction.Lock -> viewModel.saveThen(onLockNote)
                    EditorMenuAction.Trash -> {
                        viewModel.trash()
                        onBack()
                    }
                }
            },
            readImage = viewModel::readImage,
            onOpenNoteLink = { link ->
                scope.launch {
                    val target = viewModel.resolveNoteLink(link)
                    if (target ==
                        null
                    ) {
                        snackbars.showDotlineSnackbar("“$link” isn't in this vault", actionLabel = null)
                    } else {
                        viewModel.saveThen { onOpenNote(target) }
                    }
                }
            },
        )

    EditorContent(
        ui = ui,
        saveState = saveState,
        pinned = pinned,
        text = viewModel.text,
        actions = actions,
        overlay = { DotlineSnackbarHost(snackbars) },
    )

    if (renaming) {
        RenameSheet(
            path = ui.path,
            isFolder = false,
            existingNames = viewModel.siblingNames(),
            onRename = { newName -> viewModel.rename(newName) { renaming = false } },
            onDismiss = { renaming = false },
        )
    }
}

/** Reads a picked picture: its file name and bytes, or `null` if it can't be read. */
private suspend fun readPickedImage(
    context: Context,
    uri: Uri,
): Pair<String, ByteArray>? =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val name =
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "image"
            // Some apps give names without an extension; take it from the file's type then.
            val extension =
                android.webkit.MimeTypeMap
                    .getSingleton()
                    .getExtensionFromMimeType(resolver.getType(uri))
            val fullName = if ('.' !in name && extension != null) "$name.$extension" else name
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            fullName to bytes
        } catch (error: IOException) {
            android.util.Log.w("EditorScreen", "Couldn't read the picked image", error)
            null
        } catch (error: SecurityException) {
            android.util.Log.w("EditorScreen", "No access to the picked image", error)
            null
        }
    }
