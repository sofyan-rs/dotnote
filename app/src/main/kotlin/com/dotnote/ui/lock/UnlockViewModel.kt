/**
 * State and actions of "This note is locked": unlock the vault with the password or a
 * fingerprint, optionally turn fingerprint on for next time, then open (or lock/unlock) the note.
 */
package com.dotnote.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.AppContainer
import com.dotnote.core.CoreError
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.VaultPath
import com.dotnote.lock.BiometricAuthenticator
import com.dotnote.lock.FingerprintUnlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Unlock screen shows (besides the password field). */
data class UnlockUi(
    /** Note title for the top bar ("Server Keys"). */
    val title: String = "",
    /** File name for the caption under it ("SERVER KEYS.MD.AGE"). */
    val fileName: String = "",
    /** True for a `.md.age` note; false when the vault is unlocked to lock a plain note. */
    val noteIsLocked: Boolean = true,
    /**
     * True when this screen removes the lock ("Unlock note"): the note is saved as a plain .md
     * file again. False when it only opens the note.
     */
    val removesLock: Boolean = false,
    /** True if fingerprint unlock is on for this vault (shows "Unlock with fingerprint"). */
    val fingerprintEnabled: Boolean = false,
    /** Null if fingerprint can be turned on here, else why not (under the checkbox). */
    val fingerprintUnavailable: String? = null,
    /** True while scrypt runs (about a second). */
    val busy: Boolean = false,
    /** Error under the password field ("Wrong password."). */
    val error: String? = null,
)

class UnlockViewModel(
    private val container: AppContainer,
    path: String,
    /** True: after unlocking, lock/unlock the note (the "Lock note" action) instead of opening it. */
    private val toggleLock: Boolean,
) : ViewModel() {
    private val note = VaultPath(path)
    private val vaultId: String? =
        container.vaultStore.state.value.vault
            ?.id

    private val _ui =
        MutableStateFlow(
            UnlockUi(
                title = Paths.noteTitle(note),
                fileName = note.name,
                noteIsLocked = NoteNames.isLockedNote(note),
                removesLock = toggleLock && NoteNames.isLockedNote(note),
                fingerprintUnavailable = container.biometricKey.unavailableReason(),
            ),
        )
    val ui: StateFlow<UnlockUi> = _ui.asStateFlow()

    /** The fingerprint dialog opens by itself once per screen, not again after a cancel. */
    private var autoPrompted = false

    init {
        viewModelScope.launch {
            val enabled = vaultId != null && container.biometricKey.isEnabled(vaultId)
            _ui.update { it.copy(fingerprintEnabled = enabled && it.fingerprintUnavailable == null) }
        }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    /** True the first time it's asked while fingerprint is on: open the dialog without a tap. */
    fun shouldAutoPrompt(): Boolean {
        if (autoPrompted || !_ui.value.fingerprintEnabled) return false
        autoPrompted = true
        return true
    }

    /**
     * Unlocks with [password]. With [rememberFingerprint], also turns on fingerprint unlock
     * (needs [authenticator]). Calls [onDone] when the note can be opened.
     */
    fun unlockWithPassword(
        password: String,
        rememberFingerprint: Boolean,
        authenticator: BiometricAuthenticator?,
        onDone: () -> Unit,
    ) {
        val fs = container.vaultStore.fs ?: return
        val id = vaultId ?: return
        if (_ui.value.busy || password.isEmpty()) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                container.vaultLock.unlock(id, fs, password)
            } catch (error: CoreError) {
                _ui.update { it.copy(busy = false, error = error.message) }
                return@launch
            }
            if (rememberFingerprint && authenticator != null && !_ui.value.fingerprintEnabled) {
                val key = container.vaultLock.keyFor(id)
                if (key != null) {
                    container.biometricKey.enable(id, key, authenticator)?.let { container.vaultStore.reportError(it) }
                }
            }
            finish(onDone)
        }
    }

    /** Unlocks with a fingerprint; falls back to the password field with a message if it can't. */
    fun unlockWithFingerprint(
        authenticator: BiometricAuthenticator,
        onDone: () -> Unit,
    ) {
        val id = vaultId ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = container.biometricKey.unlock(id, authenticator)) {
                is FingerprintUnlock.Unlocked -> {
                    container.vaultLock.remember(id, result.key)
                    finish(onDone)
                }
                is FingerprintUnlock.Cancelled -> _ui.update { it.copy(busy = false) }
                is FingerprintUnlock.UsePassword -> {
                    val stillEnabled = container.biometricKey.isEnabled(id)
                    _ui.update { it.copy(busy = false, error = result.message, fingerprintEnabled = stillEnabled) }
                }
            }
        }
    }

    private suspend fun finish(onDone: () -> Unit) {
        if (toggleLock) container.noteLocker.toggle(note)
        _ui.update { it.copy(busy = false) }
        onDone()
    }
}
