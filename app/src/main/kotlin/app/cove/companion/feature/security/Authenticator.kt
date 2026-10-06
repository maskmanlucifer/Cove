package app.cove.companion.feature.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** What the device can authenticate with right now. */
enum class AuthAvailability {
    /** A strong biometric (fingerprint, face) is enrolled. */
    Biometric,

    /** No biometric, but a PIN, pattern or password is set. */
    CredentialOnly,

    /** Nothing is enrolled: the lock cannot work until the user sets a screen lock. */
    None,
}

/** Thin wrapper over [BiometricPrompt] allowing strong biometrics or the device PIN. */
object Authenticator {
    /** Reports what [context]'s device can authenticate with. */
    fun availability(context: Context): AuthAvailability {
        val manager = BiometricManager.from(context)
        return when {
            manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.Biometric
            manager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.CredentialOnly
            else -> AuthAvailability.None
        }
    }

    /**
     * Shows the system prompt. With [credentialOnly] only the device PIN is offered. [onResult] gets true after a
     * successful authentication and false when it was cancelled or failed for good.
     */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String?, credentialOnly: Boolean = false, onResult: (Boolean) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(if (credentialOnly) DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

/** The [FragmentActivity] behind this context, or null (needed to show a [BiometricPrompt]). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
