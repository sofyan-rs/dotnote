/**
 * State and actions of the note editor (SPEC 3.1–3.4, ARCHITECTURE §4.2).
 *
 * Holds the **one text buffer** ([text], a Compose `TextFieldState` with `\n` line endings) that
 * Live, Source and Preview all show. Its selection is the cursor. Every change of the buffer is
 * saved 500 ms later by the [Autosaver] (in the app scope); [flush] saves at once.
 *
 * Switching modes only changes how the buffer is drawn, never the buffer, so it never changes the
 * file. The line ending found on load (`\n` or `\r\n`) is used again on every save.
 */
package com.dotnote.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.core.CoreError
import com.dotnote.core.Edit
import com.dotnote.core.Format
import com.dotnote.core.LineEnding
import com.dotnote.core.LiveLists
import com.dotnote.core.Paths
import com.dotnote.core.TextRange
import com.dotnote.core.VaultPath
import com.dotnote.settings.ViewMode
import com.dotnote.ui.dotline.FormatAction
import com.dotnote.vault.NoteLockedException
import com.dotnote.vault.VaultEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextRange as ComposeRange

/** Everything the editor screen draws, except the buffer itself and the save state. */
data class EditorUi(
    val path: VaultPath,
    /** "PERSONAL / EXAMPLE.MD". */
    val location: String,
    val mode: ViewMode = ViewMode.Live,
    /** False until the note was read (the buffer is empty until then). */
    val loaded: Boolean = false,
    /** The note is encrypted and the vault is locked: the screen sends the user to unlock it. */
    val isLocked: Boolean = false,
    /** Reading the note failed (missing, unreadable). */
    val loadError: String? = null,
    val lineEnding: LineEnding = LineEnding.LF,
) {
    val title: String get() = Paths.noteTitle(path)
}

class EditorViewModel(
    path: VaultPath,
    private val backend: EditorBackend,
    private val appScope: CoroutineScope,
) : ViewModel() {
    /** The note's markdown, shared by all three modes. Its selection is the cursor. */
    val text = TextFieldState()

    private val _ui = MutableStateFlow(EditorUi(path = path, location = location(path)))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    private val autosaver = Autosaver(appScope, backend::write, ::errorMessage)
    val saveState: StateFlow<SaveState> = autosaver.state

    @OptIn(ExperimentalCoroutinesApi::class)
    val pinned: StateFlow<Boolean> =
        _ui
            .map { it.path }
            .flatMapLatest { backend.isPinned(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The text we last handed to the autosaver (or read from disk): no need to save it again. */
    private var savedText: String? = null

    /** Set after the note was trashed: nothing is saved any more. */
    private var closed = false

    val path: VaultPath get() = _ui.value.path

    /** Vault events, for the screen's snackbar (errors of rename, share, image import). */
    val events: Flow<VaultEvent> get() = backend.events

    init {
        viewModelScope.launch {
            val mode = backend.loadViewMode()
            _ui.update { it.copy(mode = mode) }
        }
        viewModelScope.launch { load() }
        // Follow the note when it is renamed or moved (also when a folder above it moves).
        viewModelScope.launch {
            backend.events.collect { event -> if (event is VaultEvent.Moved) onMoved(event.old, event.new) }
        }
        // snapshotFlow turns reads of Compose state into a Flow: it emits whenever the text changes.
        viewModelScope.launch {
            snapshotFlow { text.text.toString() }.collect(::onTextChanged)
        }
    }

    // ---- Loading ----

    /** Reads the note into the buffer. Called at start, and again after an unlock. */
    private suspend fun load() {
        try {
            val note = backend.read(path)
            replaceBuffer(note.text, cursorAtStart = true)
            _ui.update { it.copy(loaded = true, isLocked = false, loadError = null, lineEnding = note.lineEnding) }
        } catch (error: NoteLockedException) {
            _ui.update { it.copy(isLocked = true) }
        } catch (error: CoreError) {
            _ui.update { it.copy(loadError = error.message ?: "Couldn't open the note") }
        }
    }

    /**
     * The screen became visible (app came back, or returned from another screen). Reloads the
     * note if it changed on disk and has no unsaved edits (ARCHITECTURE §4.5); opens it if it
     * couldn't be opened before (e.g. it was locked and is unlocked now).
     */
    fun onStart() {
        if (closed) return
        viewModelScope.launch {
            val ui = _ui.value
            if (!ui.loaded) {
                load()
                return@launch
            }
            if (hasUnsavedEdits()) return@launch
            val note =
                try {
                    backend.read(ui.path)
                } catch (error: NoteLockedException) {
                    return@launch // the vault locked meanwhile; the buffer stays as it is
                } catch (error: CoreError) {
                    return@launch // gone or unreadable right now; keep showing what we have
                }
            // Check again: the user may have typed while we were reading.
            if (!hasUnsavedEdits() && note.text != text.text.toString()) {
                replaceBuffer(note.text, cursorAtStart = false)
                _ui.update { it.copy(lineEnding = note.lineEnding) }
            }
        }
    }

    /** The screen stopped (left, or the app went to the background): save now. */
    fun onStop() {
        flush()
    }

    override fun onCleared() {
        flush()
    }

    // ---- Saving ----

    /** Saves waiting edits now. Join the job to wait until they are on disk. */
    fun flush() = autosaver.flush()

    private fun hasUnsavedEdits(): Boolean = autosaver.hasUnsavedText || text.text.toString() != savedText

    private fun onTextChanged(current: String) {
        val ui = _ui.value
        if (!ui.loaded || closed || current == savedText) return
        savedText = current
        autosaver.edited(ui.path, current, ui.lineEnding)
    }

    /** Puts [newText] in the buffer without saving it (it came from disk) and without an undo step. */
    @OptIn(ExperimentalFoundationApi::class) // undoState is still marked experimental
    private fun replaceBuffer(
        newText: String,
        cursorAtStart: Boolean,
    ) {
        savedText = newText
        val cursor = if (cursorAtStart) 0 else text.selection.start.coerceAtMost(newText.length)
        text.edit {
            replace(0, length, newText)
            selection = ComposeRange(cursor)
        }
        text.undoState.clearHistory()
    }

    // ---- Modes ----

    /** Switches Live / Source / Preview and remembers the choice for the next note. */
    fun setMode(mode: ViewMode) {
        if (mode == _ui.value.mode) return
        _ui.update { it.copy(mode = mode) }
        viewModelScope.launch { backend.saveViewMode(mode) }
    }

    // ---- Editing ----

    /** A formatter-bar button: applies its `:core` [Format] edit as one undo step. */
    fun format(action: FormatAction) {
        val current = text.text.toString()
        val edit = EditorFormatting.editFor(action, current, selection()) ?: return
        apply(edit)
    }

    /** Ticks or unticks the task box on the line holding [offset] (a position in the buffer). */
    fun toggleTask(offset: Int) {
        val toggle = LiveLists.taskToggle(text.text.toString(), offset) ?: return
        text.edit {
            // Same length, so the cursor stays where it was.
            replace(toggle.range.start, toggle.range.end, toggle.replacement)
        }
    }

    /** Copies a picked image into `.img/` and inserts `![](.img/name.png)` at the cursor. */
    fun insertImage(
        name: String,
        bytes: ByteArray,
    ) {
        viewModelScope.launch {
            val link = backend.importImage(path, name, bytes) ?: return@launch
            apply(Format.images(text.text.toString(), selection(), listOf(Format.Image(alt = "", path = link))))
        }
    }

    private fun apply(edit: Edit) {
        text.edit {
            replace(edit.range.start, edit.range.end, edit.replacement)
            selection = ComposeRange(edit.newSelection.start, edit.newSelection.end)
        }
    }

    /** The cursor/selection as a `:core` range (start ≤ end, even when selected backwards). */
    private fun selection(): TextRange {
        val selection = text.selection
        return TextRange(selection.min, selection.max)
    }

    // ---- Menu actions (SPEC 3.4) ----

    fun togglePin() = backend.togglePin(path)

    /**
     * The note a Preview link points to, relative to this note's folder (`%20` decoded,
     * `#heading` dropped), or `null` if there's no such note in the vault.
     */
    suspend fun resolveNoteLink(link: String): VaultPath? {
        val decoded = java.net.URLDecoder.decode(link.substringBefore('#').replace("+", "%2B"), "UTF-8")
        val target =
            com.dotnote.core.Paths
                .imageFile(_ui.value.path.parent ?: VaultPath.ROOT, decoded) ?: return null
        val entry = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { backend.fs?.stat(target) }
        return if (entry != null && !entry.isDir) target else null
    }

    /** Names next to the note, for the rename sheet. */
    fun siblingNames(): List<String> = backend.namesIn(path.parent ?: VaultPath.ROOT)

    /** Saves, then renames the note on disk. [onDone] runs after a successful rename. */
    fun rename(
        newName: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            flush().join()
            val renamed = backend.rename(path, newName) ?: return@launch
            onMoved(path, renamed)
            onDone()
        }
    }

    /** Saves pending edits, then runs [action] with the note's path (before Move to… and Lock note). */
    fun saveThen(action: (VaultPath) -> Unit) {
        viewModelScope.launch {
            flush().join()
            action(path)
        }
    }

    /**
     * Saves, then moves the note to the trash. Runs in the app scope, because the screen closes
     * right away (the Files screen shows "moved to trash · UNDO").
     */
    fun trash() {
        val note = path
        val save = flush()
        closed = true
        appScope.launch {
            save.join()
            backend.trash(note)
        }
    }

    /** Opens the share sheet for the note, after saving it so the shared file is current. */
    fun share(context: android.content.Context) {
        val fs = backend.fs ?: return
        viewModelScope.launch {
            flush().join()
            try {
                com.dotnote.ui.files.FileSharing
                    .shareNote(context, fs, path)
            } catch (error: CoreError) {
                backend.reportError(error.message ?: "Couldn't share the note")
            }
        }
    }

    /** Loads an image linked from the note (`.img/cat.png`, relative to the note's folder). */
    suspend fun readImage(link: String): ByteArray? {
        val fs = backend.fs ?: return null
        val file = Paths.imageFile(path.parent ?: VaultPath.ROOT, link) ?: return null
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fs.read(file) }
        } catch (error: CoreError) {
            null // a missing image shows its alt text instead
        }
    }

    // ---- Helpers ----

    private fun onMoved(
        old: VaultPath,
        new: VaultPath,
    ) {
        val moved = Paths.remapPath(path, old, new) ?: return
        if (moved == path) return
        autosaver.moved(old, new)
        _ui.update { it.copy(path = moved, location = location(moved)) }
    }

    private fun location(path: VaultPath): String =
        // "NOTES / JOURNAL / EXAMPLE.MD": vault, folders, file (SPEC 3.1).
        (listOf(backend.vaultName) + path.segments)
            .filter {
                it.isNotEmpty()
            }.joinToString(" / ")
            .uppercase()

    private fun errorMessage(error: Exception): String? =
        when (error) {
            is NoteLockedException -> "Locked: unlock the vault to save"
            else -> error.message
        }
}
