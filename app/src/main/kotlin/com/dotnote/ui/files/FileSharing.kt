/**
 * Copy path and Share (SPEC 2.3), using Android's clipboard and share sheet.
 */
package com.dotnote.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.dotnote.core.Paths
import com.dotnote.core.VaultFs
import com.dotnote.core.VaultPath
import com.dotnote.vault.SafVaultFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies the vault-relative path ("Projects/Plan.md") to the clipboard. */
fun copyPath(
    context: Context,
    path: VaultPath,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Path", path.value.ifEmpty { "/" }))
    // Android 13+ shows its own "Copied" confirmation, so we don't add one.
}

object FileSharing {
    /**
     * Shares a note: the .md file itself when it's in a picked folder (other apps get read
     * access to just that file), otherwise its text.
     */
    suspend fun shareNote(
        context: Context,
        fs: VaultFs,
        path: VaultPath,
    ) {
        val title = Paths.noteTitle(path)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TITLE, title)
            }
        val uri = (fs as? SafVaultFs)?.let { withContext(Dispatchers.IO) { it.shareUri(path) } }
        if (uri != null) {
            intent.type = "text/markdown"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, withContext(Dispatchers.IO) { fs.read(path).decodeToString() })
        }
        context.startActivity(Intent.createChooser(intent, "Share “$title”"))
    }
}
