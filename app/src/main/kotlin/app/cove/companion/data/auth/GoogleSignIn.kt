package app.cove.companion.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.cove.companion.feature.onboarding.SignInLauncher
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** How a sign-in attempt ended. */
enum class SignInOutcome { Success, NotConfigured, Cancelled, NoAccount, Offline, Rejected, Failed }

/** Google sign-in through Credential Manager, then a Supabase session through [auth]. */
class GoogleSignIn(
    private val auth: AuthRepository,
    private val webClientId: String,
    private val scope: CoroutineScope,
) : SignInLauncher {
    override fun signIn(context: Context, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.Main) { onResult(attempt(context) == SignInOutcome.Success) }
    }

    /** Runs the whole sign-in and says how it ended, so the UI can explain a failure. */
    suspend fun attempt(context: Context): SignInOutcome {
        if (!auth.enabled || webClientId.isBlank()) return SignInOutcome.NotConfigured
        return try {
            val nonce = UUID.randomUUID().toString()
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setNonce(sha256Hex(nonce))
                .build()
            val result = CredentialManager.create(context)
                .getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val google = GoogleIdTokenCredential.createFrom(result.credential.data)
            if (auth.signInWithGoogle(google.idToken, nonce)) SignInOutcome.Success else SignInOutcome.Rejected
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            SignInOutcome.NoAccount
        } catch (e: GetCredentialException) {
            SignInOutcome.Failed
        } catch (e: com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException) {
            SignInOutcome.Failed
        } catch (e: java.io.IOException) {
            SignInOutcome.Offline
        }
    }

    private fun sha256Hex(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
