/**
 * `FLAG_SECURE` while a decrypted note or a password field is on screen (ARCHITECTURE §8.2):
 * no screenshots or screen recording, and the app shows blank in Recents.
 *
 * Call [SecureWindowEffect] from any screen that shows decrypted text (the editor, for a
 * `.md.age` note) or asks for the vault password. The flag is cleared when the
 * last such screen leaves.
 */
package com.dotnote.ui.lock

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

/** Sets `FLAG_SECURE` on the activity window while this composable is shown. */
@Composable
fun SecureWindowEffect() {
    val window = LocalActivity.current?.window ?: return
    // DisposableEffect runs when this enters the screen and `onDispose` when it leaves.
    DisposableEffect(window) {
        SecureWindowCounter.acquire(window)
        onDispose { SecureWindowCounter.release(window) }
    }
}

/**
 * Counts the screens that asked for `FLAG_SECURE`. During a navigation (Unlock → Editor) the new
 * screen arrives before the old one leaves; without a count the old one would clear the flag
 * while the decrypted note is already showing.
 */
private object SecureWindowCounter {
    // Weak keys: a destroyed activity's window doesn't stay in memory because of us.
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = counts[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        counts[window] = count + 1
    }

    fun release(window: Window) {
        val count = (counts[window] ?: 1) - 1
        if (count <= 0) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            counts[window] = count
        }
    }
}
