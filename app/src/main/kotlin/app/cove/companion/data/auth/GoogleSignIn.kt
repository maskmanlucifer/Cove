package app.cove.companion.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.cove.companion.feature.onboarding.SignInLauncher
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Google sign-in through Credential Manager, then a Supabase session through [auth]. */
class GoogleSignIn(
    private val auth: AuthRepository,
    private val webClientId: String,
    private val scope: CoroutineScope,
) : SignInLauncher {
    override fun signIn(context: Context, onResult: (Boolean) -> Unit) {
        if (!auth.enabled || webClientId.isBlank()) {
            onResult(false)
            return
        }
        scope.launch(Dispatchers.Main) {
            val ok = try {
                val nonce = UUID.randomUUID().toString()
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setNonce(sha256Hex(nonce))
                    .build()
                val result = CredentialManager.create(context)
                    .getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val google = GoogleIdTokenCredential.createFrom(result.credential.data)
                auth.signInWithGoogle(google.idToken, nonce)
            } catch (e: GetCredentialException) {
                false
            } catch (e: com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException) {
                false
            } catch (e: java.io.IOException) {
                false
            }
            onResult(ok)
        }
    }

    private fun sha256Hex(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
