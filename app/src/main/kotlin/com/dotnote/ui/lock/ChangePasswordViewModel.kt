/**
 * State and actions of "Change notes password" (SPEC 5.3). Only `.dotnote-lock.age` is rewritten
 * (same vault key, new password), so locked notes don't change, like desktop.
 */
package com.dotnote.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.AppContainer
import com.dotnote.core.CoreError
import com.dotnote.core.Lock
import com.dotnote.core.NoteNames
import com.dotnote.core.Search
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Change password screen shows (besides the three fields). */
data class ChangePasswordUi(
    val vaultName: String = "",
    /** Number of `.md.age` notes in the vault, for "Your 3 locked notes stay locked." */
    val lockedNotes: Int = 0,
    /** True while scrypt runs (unlock with the old password, then save with the new one). */
    val busy: Boolean = false,
    /** Error on the current-password field ("That's not the current password. Try again."). */
    val currentError: String? = null,
    /** Error on the new-password field (too short). */
    val newError: String? = null,
    /** Error on the confirm field (doesn't match). */
    val confirmError: String? = null,
)

class ChangePasswordViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(initialUi())
    val ui: StateFlow<ChangePasswordUi> = _ui.asStateFlow()

    fun clearErrors() = _ui.update { it.copy(currentError = null, newError = null, confirmError = null) }

    /** Changes the password; calls [onDone] when it's saved. */
    fun submit(
        current: String,
        new: String,
        confirm: String,
        onDone: () -> Unit,
    ) {
        if (_ui.value.busy) return
        val newError = if (new.passwordLength() < Lock.MIN_PASSWORD_LEN) "Use at least ${Lock.MIN_PASSWORD_LEN} characters." else null
        val confirmError = if (newError == null && new != confirm) "The passwords don't match." else null
        if (newError != null || confirmError != null) {
            _ui.update { it.copy(newError = newError, confirmError = confirmError) }
            return
        }
        val fs = container.vaultStore.fs ?: return
        val vaultId =
            container.vaultStore.state.value.vault
                ?.id ?: return
        _ui.update { it.copy(busy = true, currentError = null, newError = null, confirmError = null) }
        viewModelScope.launch {
            try {
                container.vaultLock.changePassword(vaultId, fs, current, new)
            } catch (error: CoreError) {
                val message =
                    when {
                        error.message == "The current password is wrong." -> "That's not the current password. Try again."
                        error is CoreError.NotFound -> "This vault has no notes password yet."
                        else -> error.message
                    }
                _ui.update { it.copy(busy = false, currentError = message) }
                return@launch
            }
            _ui.update { it.copy(busy = false) }
            onDone()
        }
    }

    private fun initialUi(): ChangePasswordUi {
        val state = container.vaultStore.state.value
        val locked = state.root?.let { root -> Search.flattenFiles(root).count { NoteNames.isLockedNote(it.path) } } ?: 0
        return ChangePasswordUi(vaultName = state.vault?.name.orEmpty(), lockedNotes = locked)
    }
}
