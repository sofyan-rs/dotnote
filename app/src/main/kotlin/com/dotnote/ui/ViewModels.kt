/**
 * Helpers to create ViewModels that need the app's shared stores.
 *
 * A ViewModel keeps screen state across rotation. Android creates it through a "factory";
 * [dotnoteViewModel] builds that factory for you:
 *
 * ```
 * val viewModel: FilesViewModel = dotnoteViewModel { container -> FilesViewModel(container.vaultStore) }
 * ```
 */
package com.dotnote.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dotnote.AppContainer
import com.dotnote.DotnoteApp

/** Gets (or creates) a ViewModel of type [VM] for the current screen, built by [create]. */
@Composable
inline fun <reified VM : ViewModel> dotnoteViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM =
    viewModel(
        key = key,
        factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as DotnoteApp
                    create(app.container)
                }
            },
    )

/**
 * The app's shared stores, for composables that only need to read a flow (no ViewModel).
 * Prefer a ViewModel for anything that changes state.
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) { (context.applicationContext as DotnoteApp).container }
}
