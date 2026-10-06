package app.cove.companion.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Whether cloud features are available and who is signed in. */
sealed interface AuthState {
    /** Supabase or Google config is blank: sync is switched off. */
    data object Disabled : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/** Tokens issued by Supabase GoTrue; [expiresAtMs] is epoch millis. */
@Serializable
data class Session(val accessToken: String, val refreshToken: String, val expiresAtMs: Long, val userId: String, val email: String? = null)

/** Where the [Session] is kept between launches (encrypted on device). */
interface SessionStore {
    fun load(): Session?
    fun save(session: Session?)
}

/** Thrown when the refresh token is no longer accepted and the user must sign in again. */
class SessionExpiredException : Exception("Session expired")

/**
 * Supabase auth: exchanges a Google ID token for a session, keeps it fresh and exposes [state].
 * A blank [baseUrl] or [anonKey] leaves the repository permanently [AuthState.Disabled].
 */
class AuthRepository(
    private val baseUrl: String,
    private val anonKey: String,
    private val store: SessionStore,
    private val client: HttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val refreshSkewMs: Long = 60_000,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AuthState>(initialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** True when a backend is configured. */
    val enabled: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private fun initialState(): AuthState {
        if (baseUrl.isBlank() || anonKey.isBlank()) return AuthState.Disabled
        return store.load()?.let { AuthState.SignedIn(it.userId, it.email) } ?: AuthState.SignedOut
    }

    /** Exchanges a Google [idToken] (and the raw [nonce] it was minted with) for a Supabase session. */
    suspend fun signInWithGoogle(idToken: String, nonce: String?): Boolean {
        if (!enabled) return false
        val body = buildJsonObject {
            put("provider", "google")
            put("id_token", idToken)
            if (nonce != null) put("nonce", nonce)
        }
        val session = tokenCall("id_token", body) ?: return false
        persist(session)
        return true
    }

    /**
     * Returns a usable access token, refreshing it first when it expires within the skew window.
     * Null when signed out; signs out when the refresh token is rejected; keeps the session on network errors
     * and returns the old token so a later retry can succeed.
     */
    suspend fun accessToken(): String? = mutex.withLock {
        val session = store.load() ?: return null
        if (session.expiresAtMs - now() > refreshSkewMs) return session.accessToken
        try {
            val fresh = tokenCall("refresh_token", buildJsonObject { put("refresh_token", session.refreshToken) })
            if (fresh == null) {
                clear()
                null
            } else {
                persist(fresh)
                fresh.accessToken
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.accessToken.takeIf { session.expiresAtMs > now() }
        }
    }

    /** Forces the next [accessToken] call to refresh, used after the server answered 401. */
    suspend fun invalidateAccessToken() = mutex.withLock {
        store.load()?.let { store.save(it.copy(expiresAtMs = 0)) }
    }

    /** Signs out locally and tells the server on a best-effort basis. */
    suspend fun signOut() {
        val token = store.load()?.accessToken
        clear()
        if (token != null && enabled) runCatching {
            client.post("${baseUrl.trimEnd('/')}/auth/v1/logout") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
        }
    }

    /** Returns null for a 400/401 (token rejected) and throws for transient failures. */
    private suspend fun tokenCall(grant: String, body: JsonObject): Session? {
        val response = client.post("${baseUrl.trimEnd('/')}/auth/v1/token") {
            parameter("grant_type", grant)
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (response.status.value == 400 || response.status.value == 401) return null
        if (!response.status.isSuccess()) throw java.io.IOException("auth ${response.status.value}")
        return parseSession(text, now())
    }

    private fun persist(session: Session) {
        store.save(session)
        _state.value = AuthState.SignedIn(session.userId, session.email)
    }

    private fun clear() {
        store.save(null)
        _state.value = if (enabled) AuthState.SignedOut else AuthState.Disabled
    }

    internal companion object {
        /** Parses a GoTrue token response; expiry comes from `expires_in` relative to [nowMs]. */
        fun parseSession(text: String, nowMs: Long): Session {
            val o = Json.parseToJsonElement(text).jsonObject
            val user = o["user"]?.jsonObject
            val expiresIn = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
            return Session(
                accessToken = o.getValue("access_token").jsonPrimitive.content,
                refreshToken = o.getValue("refresh_token").jsonPrimitive.content,
                expiresAtMs = nowMs + expiresIn * 1000,
                userId = user?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty(),
                email = user?.get("email")?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}
