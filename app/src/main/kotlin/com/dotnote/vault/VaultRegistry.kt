/**
 * The list of vaults the user added (saved in [Settings]) and access to their files.
 *
 * - [addPickedFolder] keeps the permission the folder picker gave us, so it still works after
 *   a restart, and saves the vault.
 * - [hasAccess] tells whether we can still open a vault (the user can revoke access in
 *   Android's settings, or the folder can be deleted).
 * - [openFs] gives the [VaultFs] to read and write the vault's files.
 */
package com.dotnote.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.dotnote.core.FileVaultFs
import com.dotnote.core.VaultFs
import com.dotnote.settings.SavedVault
import com.dotnote.settings.SettingsStore
import com.dotnote.settings.VaultKind
import java.io.File
import java.util.UUID

class VaultRegistry(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    /** Read + write: what we ask the picker for and keep. */
    private val accessFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Saves a folder returned by the folder picker as a vault and makes it the last opened one.
     * If the same folder was added before, that vault is returned instead of a copy.
     */
    suspend fun addPickedFolder(treeUri: Uri): SavedVault {
        // Without this, access to the folder ends when the app process ends.
        context.contentResolver.takePersistableUriPermission(treeUri, accessFlags)
        val location = treeUri.toString()
        val existing = settingsStore.current().vaults.firstOrNull { it.location == location }
        val vault = existing ?: SavedVault(UUID.randomUUID().toString(), folderName(treeUri), VaultKind.Saf, location)
        settingsStore.update { settings ->
            val vaults = if (existing == null) settings.vaults + vault else settings.vaults
            settings.copy(vaults = vaults, lastVaultId = vault.id)
        }
        return vault
    }

    /** Saves a folder in app storage (e.g. a git clone) as a vault and makes it the last opened one. */
    suspend fun addAppFolder(
        folder: File,
        name: String,
    ): SavedVault {
        val vault = SavedVault(UUID.randomUUID().toString(), name, VaultKind.File, folder.absolutePath)
        settingsStore.update { it.copy(vaults = it.vaults + vault, lastVaultId = vault.id) }
        return vault
    }

    /** True if the app can still read and write [vault]'s folder. */
    fun hasAccess(vault: SavedVault): Boolean =
        when (vault.kind) {
            VaultKind.Saf -> {
                val uri = Uri.parse(vault.location)
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission && it.isWritePermission
                }
            }
            VaultKind.File -> File(vault.location).isDirectory
        }

    /** The file system of [vault]. */
    fun openFs(vault: SavedVault): VaultFs =
        when (vault.kind) {
            VaultKind.Saf -> SafVaultFs(context.contentResolver, Uri.parse(vault.location))
            VaultKind.File -> FileVaultFs(File(vault.location))
        }

    /** The folder of a File vault (git needs it), or `null` for a picked folder. */
    fun appFolder(vault: SavedVault): File? = if (vault.kind == VaultKind.File) File(vault.location) else null

    /** Changes the name shown in the vault list (the folder itself isn't renamed). */
    suspend fun rename(
        vaultId: String,
        newName: String,
    ) {
        settingsStore.update { settings ->
            settings.copy(vaults = settings.vaults.map { if (it.id == vaultId) it.copy(name = newName) else it })
        }
    }

    /** Forgets a vault: removes it from the list and gives back the folder permission. */
    suspend fun remove(vault: SavedVault) {
        if (vault.kind == VaultKind.Saf) {
            val uri = Uri.parse(vault.location)
            try {
                context.contentResolver.releasePersistableUriPermission(uri, accessFlags)
            } catch (_: SecurityException) {
                // The permission was already gone (revoked in Android settings): nothing to release.
            }
        }
        settingsStore.update { settings ->
            val remaining = settings.vaults.filter { it.id != vault.id }
            settings.copy(
                vaults = remaining,
                lastVaultId = if (settings.lastVaultId == vault.id) remaining.firstOrNull()?.id else settings.lastVaultId,
                openNotes = settings.openNotes - vault.id,
                syncedVaults = settings.syncedVaults - vault.id,
                biometricVaults = settings.biometricVaults - vault.id,
            )
        }
    }

    /** A readable path for the vault list: "Documents/Personal" or the app folder name. */
    fun displayPath(vault: SavedVault): String =
        when (vault.kind) {
            VaultKind.Saf -> {
                // Tree ids look like "primary:Documents/Personal" on the phone's own storage.
                val treeId = DocumentsContract.getTreeDocumentId(Uri.parse(vault.location))
                treeId.substringAfter(':').ifEmpty { vault.name }
            }
            VaultKind.File -> "App storage/${File(vault.location).name}"
        }

    private fun folderName(treeUri: Uri): String {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        return treeId.substringAfterLast('/').substringAfter(':').ifEmpty { "Vault" }
    }
}
