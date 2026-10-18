package com.dotnote.lock

import com.dotnote.core.CoreError
import com.dotnote.core.Lock
import com.dotnote.core.MemoryVaultFs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The unlocked key in memory and the 5-minute auto-lock (ARCHITECTURE §8.2). Time is virtual:
 * `advanceTimeBy` moves the test clock, so the tests take milliseconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultLockTest {
    private val password = "correct horse"

    /** A vault with a password, made with a tiny scrypt factor so tests are fast. */
    private fun vaultWithPassword(): MemoryVaultFs = MemoryVaultFs().also { Lock.createPassword(it, password, workFactor = 10) }

    /** Timers run on the test clock; scrypt runs on the test dispatcher too (no real threads). */
    private fun TestScope.newLock() =
        VaultLock(
            scope = backgroundScope,
            cpu = StandardTestDispatcher(testScheduler),
            workFactor = 10,
        )

    @Test
    fun `unlock keeps the key until the idle time passes`() =
        runTest {
            val fs = vaultWithPassword()
            val lock = newLock()
            assertNull(lock.keyFor("v"))
            assertFalse(lock.isUnlocked("v"))

            lock.unlock("v", fs, password)
            assertTrue(lock.isUnlocked("v"))
            assertEquals(setOf("v"), lock.unlocked.value)

            advanceTimeBy(VaultLock.IDLE_LOCK_MILLIS - 1)
            runCurrent()
            assertTrue(lock.isUnlocked("v"), "still unlocked just before 5 minutes")

            advanceTimeBy(2)
            runCurrent()
            assertFalse(lock.isUnlocked("v"), "locked after 5 minutes without use")
            assertNull(lock.keyFor("v"))
            assertEquals(emptySet<String>(), lock.unlocked.value)
        }

    @Test
    fun `using the key restarts the timer`() =
        runTest {
            val fs = vaultWithPassword()
            val lock = newLock()
            lock.unlock("v", fs, password)

            advanceTimeBy(4 * 60_000L)
            assertNotNull(lock.keyFor("v")) // reading a locked note at 4 min
            advanceTimeBy(4 * 60_000L)
            runCurrent()
            assertTrue(lock.isUnlocked("v"), "8 min after unlock, but only 4 since the last use")

            advanceTimeBy(60_000L + 1)
            runCurrent()
            assertFalse(lock.isUnlocked("v"))
        }

    @Test
    fun `isUnlocked doesn't count as use`() =
        runTest {
            val lock = newLock()
            lock.unlock("v", vaultWithPassword(), password)
            advanceTimeBy(4 * 60_000L)
            assertTrue(lock.isUnlocked("v"))
            advanceTimeBy(60_000L + 1)
            runCurrent()
            assertFalse(lock.isUnlocked("v"))
        }

    @Test
    fun `wrong password throws and stays locked`() =
        runTest {
            val lock = newLock()
            val error = assertThrows<CoreError.Lock> { lock.unlock("v", vaultWithPassword(), "wrong password") }
            assertEquals("Wrong password.", error.message)
            assertFalse(lock.isUnlocked("v"))
        }

    @Test
    fun `lock drops the key now, and lockAllExcept keeps one vault`() =
        runTest {
            val lock = newLock()
            lock.unlock("a", vaultWithPassword(), password)
            lock.unlock("b", vaultWithPassword(), password)
            lock.unlock("c", vaultWithPassword(), password)

            lock.lock("a")
            assertNull(lock.keyFor("a"))
            lock.lockAllExcept("c")
            assertEquals(setOf("c"), lock.unlocked.value)
            lock.lockAllExcept(null)
            assertEquals(emptySet<String>(), lock.unlocked.value)
        }

    @Test
    fun `create and change password keep the vault unlocked with the same key`() =
        runTest {
            val fs = MemoryVaultFs()
            val lock = newLock()
            val key = lock.createPassword("v", fs, password)
            assertTrue(lock.isUnlocked("v"))
            val secret = key.secretText()

            lock.lock("v")
            assertThrows<CoreError.Lock> { lock.changePassword("v", fs, "not the password", "new password 1") }
            lock.changePassword("v", fs, password, "new password 1")
            assertEquals(secret, lock.keyFor("v")?.secretText(), "the vault key doesn't change")

            lock.lock("v")
            lock.unlock("v", fs, "new password 1")
            assertEquals(secret, lock.keyFor("v")?.secretText())
        }
}
