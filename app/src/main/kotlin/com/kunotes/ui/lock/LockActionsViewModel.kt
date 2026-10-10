/**
 * The lock-related decisions the navigation needs (SPEC §4 flow):
 * - opening a `.md.age` note goes to Unlock (5.2) while the vault is locked, else to the editor;
 * - "Lock note" locks/unlocks right away, or first shows Set password (5.1) or Unlock (5.2).
 */
package com.kunotes.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunotes.AppContainer
import com.kunotes.core.NoteNames
import com.kunotes.core.VaultPath
import com.kunotes.lock.LockToggle
import kotlinx.coroutines.launch

class LockActionsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    /** True if [path] is a locked note and its vault is locked: show Unlock before the editor. */
    fun needsUnlock(path: VaultPath): Boolean =
        NoteNames.isLockedNote(path) &&
            !container.vaultLock.isUnlocked(
                container.vaultStore.state.value.vault
                    ?.id,
            )

    /**
     * Locks [path] (or unlocks a `.md.age` note). If the vault has no password yet, calls
     * [onNeedsPassword]; if it is locked, calls [onNeedsUnlock]. Errors show as a snackbar.
     */
    fun toggleLock(
        path: VaultPath,
        onNeedsPassword: () -> Unit,
        onNeedsUnlock: () -> Unit,
    ) {
        viewModelScope.launch {
            when (container.noteLocker.toggle(path)) {
                LockToggle.NeedsPassword -> onNeedsPassword()
                LockToggle.NeedsUnlock -> onNeedsUnlock()
                is LockToggle.Done, LockToggle.Failed -> Unit
            }
        }
    }
}
