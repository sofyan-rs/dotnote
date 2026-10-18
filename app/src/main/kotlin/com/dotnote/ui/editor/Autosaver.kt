/**
 * Autosave for the editor (ARCHITECTURE §7.6): saves the note 500 ms after the last edit, and
 * right away when asked to [flush] (leaving the note, app going to the background).
 *
 * Saves run in the **app scope** and can't be cancelled once started, so closing the editor in
 * the middle of a save never loses it. Saves run one at a time, in order.
 */
package com.dotnote.ui.editor

import com.dotnote.core.LineEnding
import com.dotnote.core.VaultPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the top bar shows: ✓ SAVED, ● SAVING, or the error of the last failed save. */
sealed interface SaveState {
    data object Saved : SaveState

    data object Saving : SaveState

    data class Failed(
        val message: String,
    ) : SaveState
}

/**
 * @param scope the app scope (`AppContainer.appScope`), so saves outlive the screen.
 * @param write saves the text; it may throw (the error is shown and the text kept for a retry).
 * @param errorMessage turns a failed save into a short message for the top bar.
 */
class Autosaver(
    private val scope: CoroutineScope,
    private val write: suspend (VaultPath, String, LineEnding) -> Unit,
    private val errorMessage: (Exception) -> String?,
    private val delayMillis: Long = SAVE_DELAY_MILLIS,
) {
    /** One save waiting to happen: the latest text wins. */
    private data class Pending(
        val path: VaultPath,
        val text: String,
        val lineEnding: LineEnding,
    )

    private val _state = MutableStateFlow<SaveState>(SaveState.Saved)
    val state: StateFlow<SaveState> = _state.asStateFlow()

    private var pending: Pending? = null
    private var timer: Job? = null
    private val writeLock = Mutex()

    /** True while there is text that isn't on disk yet (waiting, or being written). */
    val hasUnsavedText: Boolean get() = pending != null || writeLock.isLocked

    /** The buffer changed: save [text] after the delay (an earlier waiting save is replaced). */
    fun edited(
        path: VaultPath,
        text: String,
        lineEnding: LineEnding,
    ) {
        pending = Pending(path, text, lineEnding)
        _state.value = SaveState.Saving
        timer?.cancel()
        timer =
            scope.launch {
                delay(delayMillis)
                // Once the delay is over, the save must finish even if the timer is cancelled.
                withContext(NonCancellable) { saveNow() }
            }
    }

    /**
     * Saves the waiting text now. The returned job finishes when it's on disk (or failed);
     * `join()` it before renaming, moving or trashing the note.
     */
    fun flush(): Job {
        timer?.cancel()
        timer = null
        return scope.launch(NonCancellable) { saveNow() }
    }

    /** The note was renamed or moved: a waiting save goes to its new place. */
    fun moved(
        old: VaultPath,
        new: VaultPath,
    ) {
        val save = pending ?: return
        val remapped =
            com.dotnote.core.Paths
                .remapPath(save.path, old, new) ?: return
        pending = save.copy(path = remapped)
    }

    /** Drops a waiting save without writing (only for a note that is gone, e.g. trashed). */
    fun discard() {
        timer?.cancel()
        timer = null
        pending = null
        _state.value = SaveState.Saved
    }

    private suspend fun saveNow() {
        writeLock.withLock {
            val save = pending ?: return
            pending = null
            _state.value = SaveState.Saving
            try {
                write(save.path, save.text, save.lineEnding)
                // A newer edit may have come in while writing; it keeps "SAVING" until its own save.
                if (pending == null) _state.value = SaveState.Saved
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                // Keep the text so the next edit or flush tries again; never drop the user's words.
                if (pending == null) pending = save
                _state.value = SaveState.Failed(errorMessage(error) ?: "Couldn't save")
            }
        }
    }

    companion object {
        /** Desktop `SAVE_DELAY`. */
        const val SAVE_DELAY_MILLIS = 500L
    }
}
