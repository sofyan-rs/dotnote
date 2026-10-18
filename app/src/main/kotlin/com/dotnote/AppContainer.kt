/**
 * Creates and holds the objects shared by every screen ("stores"), once per app process.
 *
 * This is "manual dependency injection": instead of a DI framework, the container builds
 * everything in one readable place. Screens get what they need through their ViewModel
 * factory (see `ui/ViewModels.kt`).
 */
package com.dotnote

import android.content.Context
import com.dotnote.lock.BiometricKey
import com.dotnote.lock.NoteLocker
import com.dotnote.lock.VaultLock
import com.dotnote.settings.SettingsStore
import com.dotnote.sync.SyncController
import com.dotnote.sync.TokenStore
import com.dotnote.sync.createSyncController
import com.dotnote.vault.NoteFiles
import com.dotnote.vault.VaultRegistry
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    /** Locked notes: the unlocked vault key, fingerprint unlock, lock/unlock a note. */
    val vaultLock = VaultLock(appScope)
    val biometricKey = BiometricKey(context, settingsStore)
    val noteLocker = NoteLocker(vaultStore, vaultLock)

    init {
        // Reading or saving a locked note uses the open vault's key (and restarts its 5-minute timer).
        noteFiles.keyProvider = {
            vaultLock.keyFor(
                vaultStore.state.value.vault
                    ?.id,
            )
        }
        // Switching vaults locks the others: a key never outlives its vault being open.
        appScope.launch {
            vaultStore.state
                .map { it.vault?.id }
                .distinctUntilChanged()
                .collect { vaultLock.lockAllExcept(it) }
        }
    }

    val tokenStore = TokenStore(context)
    val syncController: SyncController =
        createSyncController(context, appScope, settingsStore, vaultRegistry, vaultStore, tokenStore).also { sync ->
            noteFiles.onSaved = { sync.noteSaved() }
        }
}
