/**
 * Builds the app's [SyncController] from the shared stores and connects it to the app's
 * lifecycle (ARCHITECTURE §4.5): `ON_START` → auto-sync, `ON_STOP` → background push.
 * Called once, from `AppContainer`.
 */
package com.dotnote.sync

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.dotnote.settings.SettingsStore
import com.dotnote.vault.VaultEvent
import com.dotnote.vault.VaultRegistry
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Creates the [SyncController] and starts watching the app's lifecycle. Call on the main thread. */
fun createSyncController(
    context: Context,
    appScope: CoroutineScope,
    settingsStore: SettingsStore,
    vaultRegistry: VaultRegistry,
    vaultStore: VaultStore,
    tokenStore: TokenStore,
): SyncController {
    val appContext = context.applicationContext
    val controller =
        SyncController(
            scope = appScope,
            openVault = vaultStore.state.map { it.vault }.distinctUntilChanged(),
            autoSyncVaults = settingsStore.settings.map { it.syncedVaults }.distinctUntilChanged(),
            // Trash, rename and move change files like an edit does.
            vaultChanges = vaultStore.events.filter { it !is VaultEvent.Error }.map { },
            git = JGitSyncGit,
            credentials = tokenStore::load,
            folderOf = vaultRegistry::appFolder,
            afterSync = vaultStore::refresh,
            scheduleBackgroundPush = { vaultId -> SyncWorker.enqueue(appContext, vaultId) },
        )

    // ProcessLifecycleOwner: the lifecycle of the whole app (all screens), not of one Activity.
    ProcessLifecycleOwner.get().lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.onAppStart()
                Lifecycle.Event.ON_STOP -> controller.onAppStop()
                else -> Unit
            }
        },
    )

    // Forget the sign-in of vaults that were removed from the list.
    appScope.launch {
        settingsStore.settings
            .map { settings -> settings.vaults.map { it.id }.toSet() }
            .distinctUntilChanged()
            .collect { tokenStore.keepOnly(it) }
    }
    return controller
}
