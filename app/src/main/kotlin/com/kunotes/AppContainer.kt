/**
 * Creates and holds the objects shared by every screen ("stores"), once per app process.
 *
 * This is "manual dependency injection": instead of a DI framework, the container builds
 * everything in one readable place. Screens get what they need through their ViewModel
 * factory (see `ui/ViewModels.kt`).
 */
package com.kunotes

import android.content.Context
import com.kunotes.settings.SettingsStore
import com.kunotes.sync.SyncController
import com.kunotes.sync.TokenStore
import com.kunotes.sync.createSyncController
import com.kunotes.vault.NoteFiles
import com.kunotes.vault.VaultRegistry
import com.kunotes.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
) {
    /**
     * Lives as long as the app. Work started here (saves, scans) is not cancelled when a screen
     * closes. `SupervisorJob` keeps one failed job from cancelling the others.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The app's private storage; cloned vaults live in `vaults/` inside it (ARCHITECTURE §9.1). */
    val appFilesDir: java.io.File = context.filesDir

    val settingsStore = SettingsStore(context)
    val vaultRegistry = VaultRegistry(context, settingsStore)
    val vaultStore = VaultStore(vaultRegistry, settingsStore, appScope)
    val noteFiles = NoteFiles(vaultStore)
    val tokenStore = TokenStore(context)
    val syncController: SyncController =
        createSyncController(context, appScope, settingsStore, vaultRegistry, vaultStore, tokenStore).also { sync ->
            noteFiles.onSaved = { sync.noteSaved() }
        }
}
