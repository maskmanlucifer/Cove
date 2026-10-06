package app.cove.companion.feature.security

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import androidx.compose.foundation.layout.height
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.feature.onboarding.BigButton
import app.cove.companion.feature.onboarding.HelperText
import app.cove.companion.feature.onboarding.TextAction

/**
 * Full-screen cover shown instead of the app while it is locked. Asks for authentication when it appears and
 * whenever the app comes back to the foreground; [onUnlock] runs after a successful authentication and
 * [onTurnOff] lets someone whose device has no screen lock any more switch the app lock off.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, onTurnOff: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val availability = remember { Authenticator.availability(context) }
    var showing by remember { mutableStateOf(false) }

    fun ask(credentialOnly: Boolean) {
        val host = activity ?: return
        if (showing || availability == AuthAvailability.None) return
        showing = true
        Authenticator.prompt(host, "Unlock Cove", null, credentialOnly) { ok ->
            showing = false
            if (ok) onUnlock()
        }
    }
    LaunchedEffect(Unit) { ask(credentialOnly = availability == AuthAvailability.CredentialOnly) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ask(credentialOnly = availability == AuthAvailability.CredentialOnly) }

    CoveScreen {
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Illustration(Scene.Secure, Modifier.height(140.dp))
                CoveText("Cove", Modifier.padding(top = 28.dp), style = CoveType.Title.copy(fontSize = 36.sp, lineHeight = 42.sp))
                CoveText("Locked", Modifier.padding(top = 4.dp), style = CoveType.Body, color = Cove.colors.muted)
            }
            when (availability) {
                AuthAvailability.None -> {
                    HelperText("Set a screen lock on this phone to keep using the app lock.")
                    Box(Modifier.padding(top = 16.dp)) {
                        BigButton("Open security settings", { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) })
                    }
                    TextAction("Turn off app lock", onTurnOff)
                }
                AuthAvailability.Biometric -> {
                    BigButton("Unlock with fingerprint", { ask(false) })
                    TextAction("Use device PIN") { ask(true) }
                }
                AuthAvailability.CredentialOnly -> BigButton("Use device PIN", { ask(true) })
            }
        }
    }
}
