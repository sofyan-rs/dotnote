/**
 * Shows Android's fingerprint dialog (`BiometricPrompt`) and waits for the result, as one
 * suspend call. Used by [BiometricKey] for "Unlock with fingerprint" (SPEC 5.1, 5.2;
 * ARCHITECTURE §8.3).
 *
 * `BiometricPrompt` needs the activity (it adds a hidden fragment to show its dialog), so a
 * screen creates this with `rememberBiometricAuthenticator()` and passes it to its ViewModel's
 * action. Don't keep it longer than the call: it holds the activity.
 */
package com.kunotes.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** What happened in the fingerprint dialog. */
sealed class BiometricResult {
    /** The finger matched; [cipher] may now encrypt or decrypt once. */
    data class Success(
        val cipher: Cipher,
    ) : BiometricResult()

    /** The user closed the dialog or chose "Use password". Not an error. */
    data object Cancelled : BiometricResult()

    /** The system refused (too many tries, sensor error, …). [message] comes from Android. */
    data class Failed(
        val message: String,
    ) : BiometricResult()
}

class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {
    /**
     * Shows the fingerprint dialog with [title] and unlocks [cipher] (a Keystore cipher that
     * needs a fingerprint for every use). Suspends until the user is done.
     */
    suspend fun authenticate(
        title: String,
        subtitle: String?,
        cipher: Cipher,
    ): BiometricResult =
        // BiometricPrompt must be created and shown on the main thread.
        withContext(Dispatchers.Main) {
            // suspendCancellableCoroutine turns the callback API into a suspend function: we
            // resume the coroutine from the callback, exactly once.
            suspendCancellableCoroutine { continuation ->
                val callback =
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val unlocked = result.cryptoObject?.cipher
                            val outcome =
                                if (unlocked != null) {
                                    BiometricResult.Success(unlocked)
                                } else {
                                    BiometricResult.Failed("Fingerprint unlock didn't return a key.")
                                }
                            if (continuation.isActive) continuation.resume(outcome)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            val outcome =
                                if (errorCode in CANCEL_CODES) {
                                    BiometricResult.Cancelled
                                } else {
                                    BiometricResult.Failed(errString.toString())
                                }
                            if (continuation.isActive) continuation.resume(outcome)
                        }

                        // onAuthenticationFailed (one finger that didn't match) is not overridden:
                        // the dialog stays open and lets the user try again.
                    }
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                val info =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(title)
                        .apply { if (subtitle != null) setSubtitle(subtitle) }
                        .setNegativeButtonText("Use password")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG)
                        .build()
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                // If the screen goes away while the dialog is up, close the dialog too.
                continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            }
        }

    private companion object {
        /** Error codes that just mean "the user didn't want to" (not worth an error message). */
        val CANCEL_CODES =
            setOf(
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                BiometricPrompt.ERROR_CANCELED,
            )
    }
}
