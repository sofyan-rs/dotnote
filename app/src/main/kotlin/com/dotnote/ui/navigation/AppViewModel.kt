/**
 * Decides what the app shows at launch: the last vault's files, or the welcome screen
 * (also when access to the last vault's folder was lost). Also provides the theme setting.
 */
package com.dotnote.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dotnote.AppContainer
import com.dotnote.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app starts. */
sealed class StartScreen {
    /** Settings are still loading: show an empty background for a moment. */
    data object Loading : StartScreen()

    /** No usable vault. [lostAccessTo] names a vault whose folder permission is gone. */
    data class Welcome(
        val lostAccessTo: String? = null,
    ) : StartScreen()

    data object Files : StartScreen()
}

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _start = MutableStateFlow<StartScreen>(StartScreen.Loading)
    val start: StateFlow<StartScreen> = _start.asStateFlow()

    private val _noteRequest = MutableStateFlow<String?>(null)

    /** A note path to open, asked for by a home-screen widget tap; `null` when there is none. */
    val noteRequest: StateFlow<String?> = _noteRequest.asStateFlow()

    val theme: StateFlow<ThemeMode> =
        container.settingsStore.settings
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    init {
        viewModelScope.launch {
            val settings = container.settingsStore.current()
            val last = settings.vaults.firstOrNull { it.id == settings.lastVaultId }
            _start.value =
                when {
                    last == null -> StartScreen.Welcome()
                    !container.vaultRegistry.hasAccess(last) -> StartScreen.Welcome(lostAccessTo = last.name)
                    else -> {
                        // Already open (e.g. the activity was recreated): don't rescan.
                        if (container.vaultStore.state.value.vault
                                ?.id != last.id
                        ) {
                            container.vaultStore.open(last)
                        }
                        StartScreen.Files
                    }
                }
        }
    }

    /**
     * Opens [path] of vault [vaultId] (a widget tap): switches to that vault first if another
     * one is open, then asks the navigation to show the note. Ignored if the vault is gone or
     * its folder can't be read; the app then just opens as usual.
     */
    fun openNote(
        vaultId: String,
        path: String,
    ) {
        viewModelScope.launch {
            // Wait for the launch decision in init, so the two don't open vaults at the same time.
            start.first { it != StartScreen.Loading }
            val vault =
                container.settingsStore
                    .current()
                    .vaults
                    .firstOrNull { it.id == vaultId } ?: return@launch
            if (!container.vaultRegistry.hasAccess(vault)) return@launch
            if (container.vaultStore.state.value.vault
                    ?.id != vault.id
            ) {
                container.vaultStore.open(vault)
            }
            _noteRequest.value = path
        }
    }

    /** The navigation opened the requested note. */
    fun noteRequestHandled() {
        _noteRequest.value = null
    }
}
