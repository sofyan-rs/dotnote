/**
 * Everything the app remembers between launches (ARCHITECTURE §4.4), stored as JSON by
 * [SettingsStore]. Secrets (git token, wrapped vault keys) are NOT kept here.
 *
 * `@Serializable` lets kotlinx.serialization turn these classes into JSON and back.
 * Every field has a default, so older settings files still load after we add fields.
 */
package com.dotnote.settings

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    /** Vaults the user added, in the order they were added. */
    val vaults: List<SavedVault> = emptyList(),
    /** The vault to open at launch. */
    val lastVaultId: String? = null,
    /** Open notes per vault id: paths (vault-relative) and pinned flags, most recent first. */
    val openNotes: Map<String, List<SavedOpenNote>> = emptyMap(),
    /** The editor mode used for the next note you open (remembered per app). */
    val viewMode: ViewMode = ViewMode.Live,
    /** Vault ids with git auto-sync turned on. */
    val syncedVaults: Set<String> = emptySet(),
    /** Vault ids where fingerprint unlock is turned on. */
    val biometricVaults: Set<String> = emptySet(),
    /** Light, dark, or follow the phone. */
    val theme: ThemeMode = ThemeMode.System,
)

/** A vault the user added. */
@Serializable
data class SavedVault(
    /** Stable id (random), used as the key everywhere else. */
    val id: String,
    /** Display name (the folder name at first; can be renamed in the list only). */
    val name: String,
    val kind: VaultKind,
    /** A SAF tree URI (`content://...`) for [VaultKind.Saf], an absolute path for [VaultKind.File]. */
    val location: String,
)

@Serializable
enum class VaultKind {
    /** A folder picked with Android's folder picker (Storage Access Framework). */
    Saf,

    /** A folder in the app's own storage (e.g. a vault cloned from git). */
    File,
}

@Serializable
data class SavedOpenNote(
    val path: String,
    val pinned: Boolean = false,
)

@Serializable
enum class ViewMode { Live, Source, Preview }

@Serializable
enum class ThemeMode { System, Light, Dark }
