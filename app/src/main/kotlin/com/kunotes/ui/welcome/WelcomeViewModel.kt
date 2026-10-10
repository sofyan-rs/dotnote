/**
 * Adds the folder picked in Android's folder picker as a vault and opens it.
 */
package com.kunotes.ui.welcome

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunotes.vault.VaultRegistry
import com.kunotes.vault.VaultStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WelcomeViewModel(
    private val registry: VaultRegistry,
    private val vaultStore: VaultStore,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)

    /** Shown under the buttons when adding the folder failed. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Saves [treeUri] as a vault, opens it, then calls [onOpened]. */
    fun addFolder(
        treeUri: Uri,
        onOpened: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val vault = registry.addPickedFolder(treeUri)
                vaultStore.open(vault)
                onOpened()
            } catch (error: SecurityException) {
                _error.value = "KuNotes couldn't get access to that folder. Try another one."
            }
        }
    }
}
