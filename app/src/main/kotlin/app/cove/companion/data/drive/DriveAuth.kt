package app.cove.companion.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result of asking for a Drive access token. */
sealed interface DriveToken {
    data class Granted(val accessToken: String) : DriveToken

    /** The user has to approve Drive access; nothing can be uploaded until they do. */
    data object NeedsConsent : DriveToken

    /** Drive is switched off (no Google client id, or signed out). */
    data object Unavailable : DriveToken

    /** Google refused the request for a reason the user can act on. */
    data class Failed(val reason: DriveFailure) : DriveToken
}

/** Why Google would not hand out a Drive token. */
enum class DriveFailure {
    /** Google does not recognise this app's package name and signing certificate (no matching Android OAuth client). */
    UnknownApp,
    Offline,
    Cancelled,
    Other,
}

/** Source of Drive access tokens for scope `drive.file`. */
interface DriveAuth {
    /** A valid token, from cache when possible, else requested silently. */
    suspend fun token(): DriveToken

    /** Drops the cached token, used after the server answered 401. */
    fun invalidate()
}

/** Scope that limits Cove to files and folders it created itself (non-sensitive, no app verification). */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * [DriveAuth] over Play Services `AuthorizationClient`. Tokens are cached until [TOKEN_TTL_MS] has passed; when
 * consent is needed the `PendingIntent` is published on [consentRequests] for the activity to launch.
 *
 * @param available false when Drive is not configured or the user is signed out.
 */
class GoogleDriveAuth(
    private val context: Context,
    private val available: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val onGranted: () -> Unit = {},
) : DriveAuth {
    private val mutex = Mutex()
    private var cached: String? = null
    private var cachedAt = 0L
    private val _consent = MutableStateFlow<PendingIntent?>(null)

    /** Set when the user must approve Drive access; the activity launches it and calls [consumeConsent]. */
    val consentRequests: StateFlow<PendingIntent?> = _consent.asStateFlow()

    override suspend fun token(): DriveToken = mutex.withLock {
        if (!available()) return DriveToken.Unavailable
        cached?.takeIf { now() - cachedAt < TOKEN_TTL_MS }?.let { return DriveToken.Granted(it) }
        try {
            val result = authorize()
            val token = result.accessToken
            when {
                result.hasResolution() -> {
                    _consent.value = result.pendingIntent
                    DriveToken.NeedsConsent
                }
                token != null -> {
                    cached = token
                    cachedAt = now()
                    onGranted()
                    DriveToken.Granted(token)
                }
                else -> DriveToken.NeedsConsent
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            DriveToken.Failed(
                when (e.statusCode) {
                    DEVELOPER_ERROR -> DriveFailure.UnknownApp
                    NETWORK_ERROR, INTERNAL_ERROR -> DriveFailure.Offline
                    CANCELED -> DriveFailure.Cancelled
                    else -> DriveFailure.Other
                },
            )
        } catch (e: java.io.IOException) {
            DriveToken.Failed(DriveFailure.Offline)
        }
    }

    override fun invalidate() {
        cached = null
    }

    /** Called once the activity has launched the pending consent screen. */
    fun consumeConsent() {
        _consent.value = null
    }

    /** Reads the outcome of the consent screen and caches the token it granted. */
    fun onConsentResult(data: Intent?) {
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
            .getOrNull()?.accessToken?.let {
                cached = it
                cachedAt = now()
                onGranted()
            }
    }

    private suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private companion object {
        /** Google access tokens last an hour; refresh a little early. */
        const val TOKEN_TTL_MS = 50 * 60 * 1000L
        const val DEVELOPER_ERROR = 10
        const val NETWORK_ERROR = 7
        const val INTERNAL_ERROR = 8
        const val CANCELED = 16
    }
}

/** [DriveAuth] that always grants a fixed token; for tests and the debug fake-Drive flag. */
class FakeDriveAuth(var result: DriveToken = DriveToken.Granted("fake-token")) : DriveAuth {
    var invalidated = 0
        private set

    override suspend fun token(): DriveToken = result

    override fun invalidate() {
        invalidated++
    }
}
