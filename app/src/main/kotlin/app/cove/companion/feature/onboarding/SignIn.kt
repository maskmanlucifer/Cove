package app.cove.companion.feature.onboarding

import android.content.Context

/** Starts Google sign-in for sync; the sync feature replaces [SignIn.launcher] with the real flow. */
fun interface SignInLauncher {
    /** Runs sign-in and reports whether it succeeded through [onResult]. */
    fun signIn(context: Context, onResult: (Boolean) -> Unit)
}

/** Holder for the active [SignInLauncher]; until sync lands it just continues. */
object SignIn {
    @Volatile
    var launcher: SignInLauncher = SignInLauncher { _, onResult -> onResult(true) }
}
