/**
 * Decides what the app shows at launch: the last vault's files, or the welcome screen
 * (also when access to the last vault's folder was lost). Also provides the theme setting.
 */
package com.kunotes.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunotes.AppContainer
import com.kunotes.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
