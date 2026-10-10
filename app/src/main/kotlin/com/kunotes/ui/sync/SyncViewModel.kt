/**
 * State and actions of the SYNC tab (SPEC 7.1): turns the [SyncController]'s status into what
 * the screen draws, and saves the auto-sync switch and the git sign-in.
 */
package com.kunotes.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunotes.core.GitCredentials
import com.kunotes.settings.SettingsStore
import com.kunotes.sync.SyncController
import com.kunotes.sync.SyncPhase
import com.kunotes.sync.SyncStatus
import com.kunotes.sync.TokenStore
import com.kunotes.vault.VaultStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the SYNC tab draws. */
data class SyncUi(
    /** False until we know whether the vault can sync (avoids a flash of the wrong layout). */
    val loaded: Boolean = false,
    /** Name of the open vault, `null` if none is open. */
    val vaultName: String? = null,
    /** True when the open vault is a git clone that can sync. */
    val connected: Boolean = false,
    val phase: SyncPhase = SyncPhase.Idle,
    /** The hero number ("2") and its unit ("MIN AGO"). */
    val heroNumber: String = "--",
    val heroUnit: String = "NEVER",
    /** "main · 3 files pushed · 0 pulled", or `null` before the first sync. */
    val summary: String? = null,
    /** The last sync's error ([isError]) or the conflict copies it made. */
    val message: String? = null,
    val isError: Boolean = false,
    /** The remote without `https://`, for the REPOSITORY group. */
    val remote: String = "",
    val autoSync: Boolean = false,
    /** True once a user name and token are saved. */
    val hasSignIn: Boolean = false,
)

class SyncViewModel(
    private val controller: SyncController,
    private val vaultStore: VaultStore,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
) : ViewModel() {
    /** Ticks every 30 s so "2 MIN AGO" counts up while the screen is open. */
    private val clock =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(TICK_MILLIS)
            }
        }

    private val signIn = MutableStateFlow<GitCredentials?>(null)

    val ui: StateFlow<SyncUi> =
        combine(controller.status, vaultStore.state, signIn, clock) { status, vault, credentials, now ->
            toUi(status, vault.vault?.name, credentials != null, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    /** The saved user name, to fill in the sign-in sheet (the token itself is never shown). */
    val savedUsername: String get() = signIn.value?.username.orEmpty()

    init {
        // Load the sign-in of the open vault, again whenever another vault is opened.
        viewModelScope.launch {
            controller.status
                .map { it.vaultId }
                .distinctUntilChanged()
                .collect { vaultId -> signIn.value = vaultId?.let { tokenStore.load(it) } }
        }
    }

    fun syncNow() = controller.syncNow()

    fun setAutoSync(on: Boolean) {
        val vaultId = controller.status.value.vaultId ?: return
        viewModelScope.launch {
            settingsStore.update { settings ->
                val synced = if (on) settings.syncedVaults + vaultId else settings.syncedVaults - vaultId
                settings.copy(syncedVaults = synced)
            }
        }
    }

    /**
     * Saves the sign-in and syncs with it. An empty [token] keeps the saved one, so the user can
     * fix a user name without pasting the token again.
     */
    fun saveSignIn(
        username: String,
        token: String,
    ) {
        val vaultId = controller.status.value.vaultId ?: return
        val newToken = token.trim().ifEmpty { signIn.value?.token.orEmpty() }
        val credentials = GitCredentials(username.trim(), newToken)
        viewModelScope.launch {
            tokenStore.save(vaultId, credentials)
            signIn.value = credentials
            controller.syncNow()
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}

/** Builds the screen state; `internal` so it can be unit tested. */
internal fun toUi(
    status: SyncStatus,
    vaultName: String?,
    hasSignIn: Boolean,
    now: Long,
): SyncUi {
    val (number, unit) = lastSyncHero(status.lastSyncMillis, now)
    val summary =
        status.lastSyncMillis?.let {
            val files = if (status.pushed == 1) "file" else "files"
            "${status.branch ?: "main"} · ${status.pushed} $files pushed · ${status.pulled} pulled"
        }
    return SyncUi(
        loaded = status.loaded || vaultName == null,
        vaultName = vaultName,
        connected = status.connected,
        phase = status.phase,
        heroNumber = number,
        heroUnit = unit,
        summary = summary,
        message = status.message,
        isError = status.phase == SyncPhase.Error,
        remote =
            status.remoteUrl
                .orEmpty()
                .removePrefix("https://")
                .removePrefix("http://"),
        autoSync = status.autoSync,
        hasSignIn = hasSignIn,
    )
}

/** The hero of the LAST SYNC widget: "2" + "MIN AGO", "3" + "H AGO", "1" + "D AGO", or "--" + "NEVER". */
internal fun lastSyncHero(
    lastSyncMillis: Long?,
    now: Long,
): Pair<String, String> {
    if (lastSyncMillis == null) return "--" to "NEVER"
    val minutes = ((now - lastSyncMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes" to "MIN AGO"
        minutes < 24 * 60 -> "${minutes / 60}" to "H AGO"
        else -> "${minutes / (24 * 60)}" to "D AGO"
    }
}
