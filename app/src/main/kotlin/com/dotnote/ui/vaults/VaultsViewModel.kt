/**
 * The saved vaults for the Vaults tab (SPEC 6.2), the vault switcher (6.1) and the vault
 * actions (6.3, 6.4): list, note counts, open, rename in the list, remove.
 */
package com.dotnote.ui.vaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.AppContainer
import com.dotnote.core.CoreError
import com.dotnote.core.Search
import com.dotnote.core.VaultNode
import com.dotnote.settings.SavedVault
import com.dotnote.settings.ThemeMode
import com.dotnote.settings.VaultKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One vault row. */
data class VaultUi(
    val vault: SavedVault,
    /** "Documents/Personal · 9 notes" (the count appears once it's known). */
    val meta: String,
    val isOpen: Boolean,
    val hasAccess: Boolean,
    val syncOn: Boolean,
)

class VaultsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val registry = container.vaultRegistry
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _message = MutableStateFlow<String?>(null)

    /** A one-line problem to show (e.g. lost access), or `null`. */
    val message: StateFlow<String?> = _message.asStateFlow()

    val vaults: StateFlow<List<VaultUi>> =
        combine(container.settingsStore.settings, container.vaultStore.state, counts) { settings, vaultState, counts ->
            settings.vaults.map { vault ->
                val isOpen = vaultState.vault?.id == vault.id
                val count = if (isOpen) vaultState.noteCount.takeIf { vaultState.root != null } else counts[vault.id]
                val where =
                    if (vault.kind == VaultKind.File &&
                        vault.id in settings.syncedVaults
                    ) {
                        "Synced from git"
                    } else {
                        registry.displayPath(vault)
                    }
                VaultUi(
                    vault = vault,
                    meta = listOfNotNull(where, count?.let { "$it ${if (it == 1) "note" else "notes"}" }).joinToString(" · "),
                    isOpen = isOpen,
                    hasAccess = registry.hasAccess(vault),
                    syncOn = vault.id in settings.syncedVaults,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        countNotes()
    }

    /** Counts the notes of the vaults that aren't open (a background scan of each). */
    private fun countNotes() {
        viewModelScope.launch {
            val saved = container.settingsStore.current().vaults
            for (vault in saved) {
                if (!registry.hasAccess(vault)) continue
                val count =
                    withContext(Dispatchers.IO) {
                        try {
                            Search.flattenFiles(VaultNode.scan(registry.openFs(vault))).size
                        } catch (_: CoreError) {
                            null // unreadable right now: show the vault without a count
                        }
                    }
                if (count != null) counts.update { it + (vault.id to count) }
            }
        }
    }

    /** Opens [vault] and returns true, or explains why it can't be opened and returns false. */
    fun open(vault: SavedVault): Boolean {
        if (!registry.hasAccess(vault)) {
            _message.value = "Dotnote lost access to “${vault.name}”. Use Open a folder to pick it again."
            return false
        }
        if (container.vaultStore.state.value.vault
                ?.id != vault.id
        ) {
            container.vaultStore.open(vault)
        }
        return true
    }

    fun renameInList(
        vault: SavedVault,
        newName: String,
    ) {
        viewModelScope.launch { registry.rename(vault.id, newName.trim()) }
    }

    /**
     * Forgets [vault] (the folder stays on the phone). If it was open, opens another vault, or
     * calls [onNoVaultLeft] when it was the last one.
     */
    fun remove(
        vault: SavedVault,
        onNoVaultLeft: () -> Unit,
    ) {
        viewModelScope.launch {
            val wasOpen =
                container.vaultStore.state.value.vault
                    ?.id == vault.id
            registry.remove(vault)
            if (!wasOpen) return@launch
            container.vaultStore.close()
            val next =
                container.settingsStore
                    .current()
                    .vaults
                    .firstOrNull { registry.hasAccess(it) }
            if (next == null) onNoVaultLeft() else container.vaultStore.open(next)
        }
    }

    val theme: StateFlow<ThemeMode> =
        container.settingsStore.settings
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { container.settingsStore.update { it.copy(theme = theme) } }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** True if the vault has a notes password (a key file), for the "Notes password" row. */
    suspend fun hasPassword(vault: SavedVault): Boolean =
        withContext(Dispatchers.IO) {
            try {
                com.dotnote.core.Lock
                    .hasPassword(registry.openFs(vault))
            } catch (_: CoreError) {
                false
            }
        }
}
