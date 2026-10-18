/**
 * State and actions of "Set a notes password": checks the two fields, creates the
 * vault key, optionally turns on fingerprint unlock, then locks the note that asked for it.
 */
package com.dotnote.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.AppContainer
import com.dotnote.core.CoreError
import com.dotnote.core.Lock
import com.dotnote.core.Paths
import com.dotnote.core.VaultPath
import com.dotnote.lock.BiometricAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Set password screen shows (besides the two password fields). */
data class SetPasswordUi(
    /** Open vault name, for the `VAULT · PERSONAL` caption. */
    val vaultName: String = "",
    /** Title of the note that will be locked afterwards, or null. */
    val noteTitle: String? = null,
    /** Null if the fingerprint checkbox can be used, else why not (shown under it). */
    val fingerprintUnavailable: String? = null,
    /** True while the key is being made (scrypt, about a second). */
    val busy: Boolean = false,
    /** Error under the password field. */
    val error: String? = null,
)

class SetPasswordViewModel(
    private val container: AppContainer,
    /** The note to lock afterwards (vault-relative), or null. */
    private val notePath: String?,
) : ViewModel() {
    private val _ui =
        MutableStateFlow(
            SetPasswordUi(
                vaultName =
                    container.vaultStore.state.value.vault
                        ?.name
                        .orEmpty(),
                noteTitle = notePath?.let { Paths.noteTitle(VaultPath(it)) },
                fingerprintUnavailable = container.biometricKey.unavailableReason(),
            ),
        )
    val ui: StateFlow<SetPasswordUi> = _ui.asStateFlow()

    /** Clears the error once the user edits a field. */
    fun clearError() = _ui.update { it.copy(error = null) }

    /**
     * Sets the password. [authenticator] is needed only when [fingerprint] is ticked.
     * Calls [onDone] when the password is set (and the note locked).
     */
    fun submit(
        password: String,
        confirm: String,
        fingerprint: Boolean,
        authenticator: BiometricAuthenticator?,
        onDone: () -> Unit,
    ) {
        if (_ui.value.busy) return
        val problem =
            when {
                password.passwordLength() < Lock.MIN_PASSWORD_LEN -> "Use at least ${Lock.MIN_PASSWORD_LEN} characters."
                password != confirm -> "The passwords don't match."
                else -> null
            }
        if (problem != null) {
            _ui.update { it.copy(error = problem) }
            return
        }
        val fs = container.vaultStore.fs ?: return
        val vaultId =
            container.vaultStore.state.value.vault
                ?.id ?: return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val key =
                try {
                    container.vaultLock.createPassword(vaultId, fs, password)
                } catch (error: CoreError) {
                    _ui.update { it.copy(busy = false, error = error.message) }
                    return@launch
                }
            if (fingerprint && authenticator != null && _ui.value.fingerprintUnavailable == null) {
                // A failure here isn't fatal: the password works; tell the user on the Files screen.
                container.biometricKey.enable(vaultId, key, authenticator)?.let { container.vaultStore.reportError(it) }
            }
            if (notePath != null) container.noteLocker.toggle(VaultPath(notePath))
            _ui.update { it.copy(busy = false) }
            onDone()
        }
    }
}
