/**
 * Helpers to create ViewModels that need the app's shared stores.
 *
 * A ViewModel keeps screen state across rotation. Android creates it through a "factory";
 * [kuNotesViewModel] builds that factory for you:
 *
 * ```
 * val viewModel: FilesViewModel = kuNotesViewModel { container -> FilesViewModel(container.vaultStore) }
 * ```
 */
package com.kunotes.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kunotes.AppContainer
import com.kunotes.KuNotesApp

/** Gets (or creates) a ViewModel of type [VM] for the current screen, built by [create]. */
@Composable
inline fun <reified VM : ViewModel> kuNotesViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM =
    viewModel(
        key = key,
        factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as KuNotesApp
                    create(app.container)
                }
            },
    )
