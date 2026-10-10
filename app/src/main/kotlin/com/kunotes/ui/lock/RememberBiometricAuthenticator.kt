/**
 * Gives a screen a [BiometricAuthenticator] for its activity, to pass to a ViewModel action
 * that turns on or uses fingerprint unlock (SPEC 5.1, 5.2).
 */
package com.kunotes.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.kunotes.lock.BiometricAuthenticator

/** The authenticator, or `null` in previews (no FragmentActivity there). */
@Composable
fun rememberBiometricAuthenticator(): BiometricAuthenticator? {
    val activity = LocalActivity.current as? FragmentActivity
    return remember(activity) { activity?.let(::BiometricAuthenticator) }
}
