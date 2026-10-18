/**
 * What the editor (SPEC 3.1–3.4) needs from the rest of the app: reading and saving the note,
 * the remembered view mode, vault events, and the file actions of the editor menu.
 *
 * [EditorViewModel] talks to this interface instead of the stores directly, so its save logic can
 * be tested on the JVM with a fake (see `EditorViewModelTest`). [AppEditorBackend] is the real one.
 */
package com.dotnote.ui.editor

import com.dotnote.AppContainer
import com.dotnote.core.FsOps
import com.dotnote.core.LineEnding
import com.dotnote.core.VaultFs
import com.dotnote.core.VaultPath
import com.dotnote.settings.ViewMode
import com.dotnote.vault.NoteText
import com.dotnote.vault.VaultEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface EditorBackend {
    /** One-time vault events (renames and moves, so the editor can follow its note). */
    val events: Flow<VaultEvent>

    /** The open vault's files (for images and sharing); `null` when no vault is open. */
    val fs: VaultFs?

    /** The open vault's display name, e.g. "Personal". */
    val vaultName: String

    /** Reads the note. Throws [com.dotnote.vault.NoteLockedException] for a locked note without a key. */
    suspend fun read(path: VaultPath): NoteText

    /** Saves the note safely (see `NoteFiles.write`). */
    suspend fun write(
        path: VaultPath,
        text: String,
        lineEnding: LineEnding,
    )

    suspend fun loadViewMode(): ViewMode

    suspend fun saveViewMode(mode: ViewMode)

    /** Whether [path] is pinned in the open-notes row, as a flow that updates. */
    fun isPinned(path: VaultPath): Flow<Boolean>

    fun togglePin(path: VaultPath)

    /** Names of the items in [folder] (for the rename sheet's "already exists" check). */
    fun namesIn(folder: VaultPath): List<String>

    /** Renames the note; returns the new path, or `null` after the error was shown. */
    suspend fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath?

    /** Moves the note to the vault's trash (with UNDO in the Files screen's snackbar). */
    suspend fun trash(path: VaultPath)

    /** Copies a picked image into `.img/` next to [note]; returns the link to insert, or `null` on error. */
    suspend fun importImage(
        note: VaultPath,
        name: String,
        bytes: ByteArray,
    ): String?

    /** Shows an error in the app's snackbar. */
    suspend fun reportError(message: String)
}

/** The real [EditorBackend], on top of the app's stores. */
class AppEditorBackend(
    private val container: AppContainer,
) : EditorBackend {
    private val vaultStore get() = container.vaultStore

    override val events: Flow<VaultEvent> get() = vaultStore.events
    override val fs: VaultFs? get() = vaultStore.fs
    override val vaultName: String get() =
        vaultStore.state.value.vault
            ?.name
            .orEmpty()

    override suspend fun read(path: VaultPath): NoteText = container.noteFiles.read(path)

    override suspend fun write(
        path: VaultPath,
        text: String,
        lineEnding: LineEnding,
    ) = container.noteFiles.write(path, text, lineEnding)

    override suspend fun loadViewMode(): ViewMode = container.settingsStore.current().viewMode

    override suspend fun saveViewMode(mode: ViewMode) = container.settingsStore.update { it.copy(viewMode = mode) }

    override fun isPinned(path: VaultPath): Flow<Boolean> =
        vaultStore.state.map { state -> state.openNotes.entries.any { it.path == path && it.pinned } }

    override fun togglePin(path: VaultPath) {
        // Pinning only makes sense for a note in the open-notes row; opening it again is harmless.
        if (vaultStore.state.value.openNotes.entries
                .none { it.path == path }
        ) {
            vaultStore.openNote(path)
        }
        vaultStore.togglePin(path)
    }

    override fun namesIn(folder: VaultPath): List<String> {
        val root = vaultStore.state.value.root ?: return emptyList()
        var node = root
        for (part in folder.segments) {
            node = node.children.firstOrNull { it.name == part } ?: return emptyList()
        }
        return node.children.map { it.name }
    }

    override suspend fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath? = vaultStore.rename(path, newName)

    override suspend fun trash(path: VaultPath) {
        vaultStore.trash(path)
    }

    override suspend fun importImage(
        note: VaultPath,
        name: String,
        bytes: ByteArray,
    ): String? {
        val fs = vaultStore.fs ?: return null
        return try {
            withContext(Dispatchers.IO) { FsOps.importImage(fs, note, name, bytes) }
        } catch (error: com.dotnote.core.CoreError) {
            reportError(error.message ?: "Couldn't add the image")
            null
        }
    }

    override suspend fun reportError(message: String) = vaultStore.reportError(message)
}
