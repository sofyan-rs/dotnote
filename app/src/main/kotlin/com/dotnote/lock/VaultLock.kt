/**
 * Holds the unlocked vault key in memory, per vault, and locks it again after 5 minutes without
 * use (ARCHITECTURE §8.2). App-scoped: one instance in [com.dotnote.AppContainer].
 *
 * "Use" means reading or saving a locked note: [keyFor] restarts the timer. The timer keeps
 * running while the app is in the background; if Android kills the process, the key is gone
 * anyway (it only ever lives in memory).
 */
package com.dotnote.lock

import com.dotnote.core.Lock
import com.dotnote.core.VaultFs
import com.dotnote.core.VaultKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @param scope where the auto-lock timers run (the app scope).
 * @param cpu where scrypt runs; `Dispatchers.Default` because it is CPU work, not waiting on I/O.
 * @param idleMillis how long a key stays unlocked without use (desktop `IDLE_LOCK`: 5 minutes).
 * @param workFactor scrypt work factor for keys made on this phone, see [NEW_KEY_WORK_FACTOR].
 */
class VaultLock(
    private val scope: CoroutineScope,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
    private val idleMillis: Long = IDLE_LOCK_MILLIS,
    private val workFactor: Int = NEW_KEY_WORK_FACTOR,
) {
    // Keys and timers are touched from the IO thread (NoteFiles) and the main thread (screens),
    // so every change goes through `synchronized(this)`.
    private val keys = mutableMapOf<String, VaultKey>()
    private val timers = mutableMapOf<String, Job>()

    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the vaults that are unlocked right now. Screens with a decrypted note watch this. */
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()

    /** True if [vaultId]'s key is in memory. Doesn't count as use (the timer isn't restarted). */
    fun isUnlocked(vaultId: String?): Boolean = vaultId != null && vaultId in _unlocked.value

    /**
     * The unlocked key of [vaultId], or `null` while it's locked. Counts as use: restarts the
     * 5-minute timer. `NoteFiles` calls this on every read and save of a locked note.
     */
    fun keyFor(vaultId: String?): VaultKey? {
        if (vaultId == null) return null
        synchronized(this) {
            val key = keys[vaultId] ?: return null
            restartTimer(vaultId)
            return key
        }
    }

    /**
     * Unlocks [vaultId] with [password]. Takes about a second (scrypt, on purpose).
     * Throws `CoreError.Lock` ("Wrong password.") if the password is wrong.
     */
    suspend fun unlock(
        vaultId: String,
        fs: VaultFs,
        password: String,
    ) {
        val key = withContext(cpu) { Lock.unlock(fs, password) }
        remember(vaultId, key)
    }

    /** Sets the first password of [vaultId] and keeps the new key unlocked. */
    suspend fun createPassword(
        vaultId: String,
        fs: VaultFs,
        password: String,
    ): VaultKey {
        val key = withContext(cpu) { Lock.createPassword(fs, password, workFactor) }
        remember(vaultId, key)
        return key
    }

    /**
     * Changes the password of [vaultId]. Throws `CoreError.Lock` ("The current password
     * is wrong.") if [current] is wrong. The key stays the same and is kept unlocked.
     */
    suspend fun changePassword(
        vaultId: String,
        fs: VaultFs,
        current: String,
        new: String,
    ) {
        val key = withContext(cpu) { Lock.changePassword(fs, current, new, workFactor) }
        remember(vaultId, key)
    }

    /** Keeps [key] as the unlocked key of [vaultId] (after a password or fingerprint unlock). */
    fun remember(
        vaultId: String,
        key: VaultKey,
    ) {
        synchronized(this) {
            keys[vaultId] = key
            restartTimer(vaultId)
            _unlocked.update { it + vaultId }
        }
    }

    /** Locks [vaultId] now: drops its key from memory. */
    fun lock(vaultId: String) {
        synchronized(this) {
            keys.remove(vaultId)
            timers.remove(vaultId)?.cancel()
            _unlocked.update { it - vaultId }
        }
    }

    /** Locks every vault except [keep] (called when the open vault changes). */
    fun lockAllExcept(keep: String?) {
        _unlocked.value.filter { it != keep }.forEach(::lock)
    }

    /** Call while holding the lock: cancels the old timer and starts a new one. */
    private fun restartTimer(vaultId: String) {
        timers.remove(vaultId)?.cancel()
        timers[vaultId] =
            scope.launch {
                delay(idleMillis)
                expire(vaultId, coroutineContext[Job])
            }
    }

    /** Timer done: locks [vaultId], unless the key was used meanwhile (a newer timer runs). */
    private fun expire(
        vaultId: String,
        timer: Job?,
    ) {
        synchronized(this) {
            if (timers[vaultId] !== timer) return
            keys.remove(vaultId)
            timers.remove(vaultId)
            _unlocked.update { it - vaultId }
        }
    }

    companion object {
        /** Desktop `IDLE_LOCK`: a vault locks again after 5 minutes without use. */
        const val IDLE_LOCK_MILLIS = 5 * 60 * 1000L

        /**
         * scrypt work factor for keys made (or re-saved) on the phone: 2^17 = 128 MiB of memory,
         * about 0.6 s on the test emulator. Desktop's default 2^18 needs 256 MiB, which only fits
         * with `largeHeap` and took 1.5 s; a low-end phone has less memory and a slower CPU.
         * 2^17 is still far beyond what makes guessing practical, and desktop opens it fine
         * (ARCHITECTURE §8.1 and §12 Q7).
         */
        const val NEW_KEY_WORK_FACTOR = 17
    }
}
