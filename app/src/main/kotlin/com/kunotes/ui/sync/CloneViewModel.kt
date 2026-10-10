/**
 * The clone flow (SPEC 7 backlog "clone flow"; reached from Welcome 1.1, Vaults 6.x and the
 * SYNC tab): copies a git repository into KuNotes' storage (`files/vaults/<name>`) and opens it
 * as a new synced vault (ARCHITECTURE §9.1).
 */
package com.kunotes.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kunotes.core.CoreError
import com.kunotes.core.GitCredentials
import com.kunotes.core.GitSync
import com.kunotes.core.Names
import com.kunotes.settings.SettingsStore
import com.kunotes.sync.TokenStore
import com.kunotes.vault.VaultRegistry
import com.kunotes.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the clone screen shows besides the fields. */
data class CloneUi(
    val cloning: Boolean = false,
    /** Why the last try failed (shown under the form), or `null`. */
    val error: String? = null,
)

/** What the user typed. */
data class CloneForm(
    val url: String,
    val username: String,
    val token: String,
    val folderName: String,
)

class CloneViewModel(
    /** `files/vaults`: the parent of every cloned vault. */
    private val vaultsDir: File,
    private val registry: VaultRegistry,
    private val vaultStore: VaultStore,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(CloneUi())
    val ui: StateFlow<CloneUi> = _ui.asStateFlow()

    /** Clones, saves the vault and its sign-in, turns auto-sync on, opens it, then calls [onDone]. */
    fun clone(
        form: CloneForm,
        onDone: () -> Unit,
    ) {
        if (_ui.value.cloning) return
        val problem = validate(form)
        if (problem != null) {
            _ui.value = CloneUi(error = problem)
            return
        }
        val name = Names.trim(form.folderName)
        val folder = File(vaultsDir, name)
        val credentials =
            if (form.username.isBlank() && form.token.isBlank()) null else GitCredentials(form.username.trim(), form.token.trim())
        _ui.value = CloneUi(cloning = true)
        viewModelScope.launch {
            try {
                // NonCancellable: if the user leaves while git works, the clone still finishes and
                // the vault is saved, instead of leaving an unknown folder behind.
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { cloneInto(folder, form.url, credentials) }
                    val vault = registry.addAppFolder(folder, name)
                    if (credentials != null) tokenStore.save(vault.id, credentials)
                    settingsStore.update { it.copy(syncedVaults = it.syncedVaults + vault.id) }
                    vaultStore.open(vault)
                }
                ensureActive() // the screen is gone: don't navigate
                _ui.value = CloneUi()
                onDone()
            } catch (error: CoreError) {
                _ui.value = CloneUi(error = error.message ?: "Couldn't clone the repository.")
            }
        }
    }

    /** Runs the clone; a failed clone's partial folder is removed (it was never a vault). */
    private fun cloneInto(
        folder: File,
        url: String,
        credentials: GitCredentials?,
    ) {
        vaultsDir.mkdirs()
        try {
            GitSync.clone(folder, url, credentials)
        } catch (error: CoreError) {
            folder.deleteRecursively()
            throw error
        }
    }

    /** A message for the first thing wrong with [form], or `null` if it can be cloned. */
    private fun validate(form: CloneForm): String? {
        val url = form.url.trim()
        val name = Names.trim(form.folderName)
        return when {
            url.isEmpty() -> "Enter the address of a git repository."
            !url.startsWith("https://") -> "Use the https:// address of the repository."
            form.token.isNotBlank() && form.username.isBlank() -> "Enter the user name that goes with the token."
            Names.validateName(name) != null -> "Folder name: ${Names.validateName(name)?.message}"
            File(vaultsDir, name).exists() -> "A vault folder named “$name” already exists. Pick another name."
            else -> null
        }
    }
}

/**
 * The folder name suggested for a repository address: `https://github.com/me/notes.git` → `notes`.
 * Characters that can't be in a file name are dropped; "Notes" if nothing is left.
 */
internal fun defaultFolderName(url: String): String {
    val last =
        url
            .trim()
            .trimEnd('/')
            .substringAfterLast('/')
            .substringAfterLast(':') // `git@host:notes.git` style
            .removeSuffix(".git")
    val cleaned = last.filter { it.isLetterOrDigit() || it in "-_ ." }.trim('.', ' ')
    return cleaned.ifEmpty { "Notes" }
}
