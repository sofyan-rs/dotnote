/**
 * Reads and saves the text of one note in the open vault. The editor goes through this, never
 * through `VaultFs` directly, so the data-safety rules live in one place:
 * - Saves use `VaultFs.writeAtomic` (a crash never leaves half a file).
 * - Line endings on disk are kept: the editor works with `\n`, and a `\r\n` file is saved
 *   with `\r\n` again.
 * - A locked note (`.md.age`) is decrypted on read and encrypted on save, and is never
 *   written as plain text. Without an unlocked vault key, reading it throws [NoteLockedException].
 */
package com.dotnote.vault

import com.dotnote.core.CoreError
import com.dotnote.core.LineEnding
import com.dotnote.core.Lock
import com.dotnote.core.NoteNames
import com.dotnote.core.VaultKey
import com.dotnote.core.VaultPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A note's text as the editor sees it, plus how to write it back. */
data class NoteText(
    /** The text with `\n` line endings. */
    val text: String,
    /** The line ending the file uses on disk. */
    val lineEnding: LineEnding,
)

/** Thrown when a locked note is opened while its vault is locked. */
class NoteLockedException(
    val path: VaultPath,
) : Exception("“${path.name}” is locked")

class NoteFiles(
    private val vaultStore: VaultStore,
) {
    /**
     * Gives the unlocked key of the open vault, or `null` while it's locked. Set by the
     * locked-notes feature (`VaultLock`); until then locked notes can't be opened.
     */
    var keyProvider: () -> VaultKey? = { null }

    /** Called after every save (git auto-sync starts its 60 s wait here). */
    var onSaved: (VaultPath) -> Unit = {}

    suspend fun read(path: VaultPath): NoteText =
        withContext(Dispatchers.IO) {
            val fs = vaultStore.fs ?: throw CoreError.Io("No vault is open")
            val raw =
                if (NoteNames.isLockedNote(path)) {
                    val key = keyProvider() ?: throw NoteLockedException(path)
                    Lock.readNote(fs, key, path)
                } else {
                    fs.read(path).decodeToString()
                }
            NoteText(text = LineEnding.normalize(raw), lineEnding = LineEnding.detect(raw))
        }

    /** Saves [text] (with `\n` endings) as [lineEnding] on disk. */
    suspend fun write(
        path: VaultPath,
        text: String,
        lineEnding: LineEnding,
    ) = withContext(Dispatchers.IO) {
        val fs = vaultStore.fs ?: throw CoreError.Io("No vault is open")
        val onDisk = lineEnding.apply(text)
        if (NoteNames.isLockedNote(path)) {
            // Never write a locked note in plain text: no key, no save.
            val key = keyProvider() ?: throw NoteLockedException(path)
            Lock.writeNote(fs, key, path, onDisk)
        } else {
            fs.writeAtomic(path, onDisk.toByteArray())
        }
        onSaved(path)
    }
}
