package com.example.smsmirror.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class LockAvailability {
    /** A biometric or the device PIN/pattern/password can be used. */
    AVAILABLE,

    /** No screen lock is set, so there is nothing to authenticate against. */
    NO_LOCK_SCREEN,

    /** Hardware missing or temporarily unusable. */
    UNAVAILABLE,
}

/**
 * Wraps [BiometricPrompt] so the rest of the app never touches its API-level quirks.
 *
 * Note this gates the **user interface only**. [SecureStore]'s Keystore key is
 * deliberately not authentication-bound, because `ForwardWorker` must decrypt the SMTP
 * password and send while the phone is locked — which is the whole point of the app.
 */
object AppLock {

    fun availability(context: Context): LockAvailability {
        val code = BiometricManager.from(context).canAuthenticate(allowedAuthenticators())
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NO_LOCK_SCREEN
            else -> LockAvailability.UNAVAILABLE
        }
    }

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
                // onAuthenticationFailed (a rejected fingerprint) is intentionally not
                // overridden: the prompt stays up and lets the user try again.
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mirror")
            .setSubtitle("Your fingerprint or screen lock protects the forwarding settings.")
            // No negative button: setting one alongside DEVICE_CREDENTIAL throws.
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }

    /**
     * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is not a supported combination on API 28–29,
     * so those fall back to the weak class. The device credential is offered either way,
     * which is what makes a PIN or pattern work on a phone with no fingerprint enrolled.
     */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        }
}
