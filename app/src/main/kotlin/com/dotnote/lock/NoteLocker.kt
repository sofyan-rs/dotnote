/**
 * Locks and unlocks single notes ("Lock note" in the note actions sheet and the
 * editor menu).
 *
 * - Locking `Name.md`: `Lock.lockNote` writes `Name.md.age` and checks it decrypts to the same
 *   text; only then is the readable `Name.md` moved to the vault trash.
 * - Unlocking `Name.md.age`: `Lock.unlockNote` writes `Name.md`, then `Name.md.age` goes to the trash.
 *
 * Both need the vault's password set and the vault unlocked; [toggle] says which screen to show
 * first when that isn't the case.
 */
package com.dotnote.lock

import com.dotnote.core.CoreError
import com.dotnote.core.Lock
import com.dotnote.core.NoteNames
import com.dotnote.core.Trash
import com.dotnote.core.VaultPath
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The outcome of [NoteLocker.toggle]. */
sealed class LockToggle {
    /** Done: the note now lives at [path] (`Name.md.age` after locking, `Name.md` after unlocking). */
    data class Done(
        val path: VaultPath,
    ) : LockToggle()

    /** The vault has no password yet: show Set password (5.1) first. */
    data object NeedsPassword : LockToggle()

    /** The vault is locked: show Unlock (5.2) first. */
    data object NeedsUnlock : LockToggle()

    /** It failed; the error was already shown as a snackbar. Nothing changed on disk. */
    data object Failed : LockToggle()
}

class NoteLocker(
    private val vaultStore: VaultStore,
    private val vaultLock: VaultLock,
) {
    /** One lock/unlock at a time, like VaultStore's own file operations. */
    private val mutex = Mutex()

    /** True if the open vault has a password (`.dotnote-lock.age`). Reads the disk. */
    suspend fun hasPassword(): Boolean {
        val fs = vaultStore.fs ?: return false
        return try {
            withContext(Dispatchers.IO) { Lock.hasPassword(fs) }
        } catch (error: CoreError) {
            vaultStore.reportError(error.message ?: "Couldn't read the vault")
            false
        }
    }

    /** Locks [path] if it's a plain note, or unlocks it if it's a locked one. */
    suspend fun toggle(path: VaultPath): LockToggle {
        val fs = vaultStore.fs ?: return LockToggle.Failed
        val vaultId =
            vaultStore.state.value.vault
                ?.id ?: return LockToggle.Failed
        if (!hasPassword()) return LockToggle.NeedsPassword
        val key = vaultLock.keyFor(vaultId) ?: return LockToggle.NeedsUnlock
        val result =
            try {
                // NonCancellable: once the new file is written, always finish by trashing the old one.
                mutex.withLock {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (NoteNames.isLockedNote(path)) {
                            val note = Lock.unlockNote(fs, key, path)
                            Trash.trash(fs, path)
                            note
                        } else {
                            val locked = Lock.lockNote(fs, key, path)
                            Trash.trash(fs, path)
                            locked
                        }
                    }
                }
            } catch (error: CoreError) {
                vaultStore.reportError(error.message ?: "Couldn't lock the note")
                return LockToggle.Failed
            }
        followRename(path, result)
        vaultStore.refresh()
        return LockToggle.Done(result)
    }

    /** The note changed name (`.md` ⇄ `.md.age`): keep it open and selected under its new name. */
    private fun followRename(
        old: VaultPath,
        new: VaultPath,
    ) {
        val state = vaultStore.state.value
        if (old in state.openNotes) {
            vaultStore.closeNote(old)
            vaultStore.openNote(new)
        } else if (state.selected == old) {
            vaultStore.select(new)
        }
    }
}
