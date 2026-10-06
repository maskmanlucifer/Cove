package app.cove.companion.feature.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.config.ConnectionTester
import app.cove.companion.data.config.CredentialField
import app.cove.companion.data.config.CredentialValidator
import app.cove.companion.data.config.Credentials
import app.cove.companion.data.config.SetupCode
import app.cove.companion.data.config.SetupCodeResult
import app.cove.companion.data.config.TestResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Connect screen draws. */
data class ConnectUi(
    val credentials: Credentials = Credentials(),
    val signedIn: Boolean = false,
    val email: String? = null,
    val driveConnected: Boolean = false,
    val tests: Map<ServiceId, TestResult> = emptyMap(),
    val busy: ServiceId? = null,
) {
    /** Status of [service]. */
    fun status(service: ServiceId) = statusOf(service, ConnectFacts(credentials, signedIn, driveConnected, tests))
}

/** Reads and writes the credentials and runs the "Test connection" checks. */
class ConnectViewModel(private val c: AppContainer) : ViewModel() {
    private val tests = MutableStateFlow<Map<ServiceId, TestResult>>(emptyMap())
    private val busy = MutableStateFlow<ServiceId?>(null)
    private val tester by lazy { ConnectionTester(HttpClient(OkHttp), c.ai) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val live = c.cloudChanges().flatMapLatest { cloud ->
        combine(cloud.auth.state, cloud.drive.connected) { auth, drive -> auth to drive }
    }

    /** Current state for the screen. */
    val ui: StateFlow<ConnectUi> = combine(c.credentials, live, tests, busy) { creds, (auth, drive), results, working ->
        ConnectUi(creds, auth is AuthState.SignedIn, (auth as? AuthState.SignedIn)?.email, drive, results, working)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectUi(c.credentials.value))

    /**
     * Saves [patch] when every value passes its format check; otherwise saves nothing and returns the friendly
     * problem for each bad field.
     */
    fun save(patch: Map<CredentialField, String>): Map<CredentialField, String> {
        val problems = patch.mapNotNull { (f, v) -> CredentialValidator.check(f, v)?.let { f to it } }.toMap()
        if (problems.isEmpty()) {
            val changed = patch.filter { (f, v) -> c.credentialStore.saved[f] != CredentialValidator.normalize(f, v) }
            c.credentialStore.update(patch)
            if (changed.isNotEmpty()) tests.update { it - ServiceId.entries.filter { s -> s.fields.any(changed::containsKey) }.toSet() }
        }
        return problems
    }

    /** Applies a pasted setup code. */
    fun applySetupCode(text: String): SetupCodeResult {
        val result = SetupCode.parse(text)
        if (result is SetupCodeResult.Parsed) save(result.values)
        return result
    }

    /** Names of the services a setup code just filled, for the confirmation line. */
    fun describe(values: Map<CredentialField, String>): String =
        ServiceId.entries.filter { s -> s.fields.any(values::containsKey) }.joinToString(", ") { it.title }

    /** Saves [patch] (if valid) then runs the check for [service]; returns the field problems when it did not start. */
    fun test(service: ServiceId, patch: Map<CredentialField, String>, context: Context): Map<CredentialField, String> {
        val problems = save(patch)
        if (problems.isNotEmpty() || busy.value != null) return problems
        busy.value = service
        tests.update { it - service }
        viewModelScope.launch {
            val result = try {
                run(service, c.credentials.value, context)
            } finally {
                busy.value = null
            }
            tests.update { it + (service to result) }
        }
        return emptyMap()
    }

    private suspend fun run(service: ServiceId, creds: Credentials, context: Context): TestResult = when (service) {
        ServiceId.Supabase ->
            if (!creds.hasSupabase) TestResult(false, "Add the project URL and the anon key first.") else tester.supabase(creds.supabaseUrl, creds.supabaseAnonKey)
        ServiceId.Gemini ->
            if (!creds.hasGemini) TestResult(false, "Add your API key first.") else tester.gemini(creds.geminiApiKey, creds.model)
        ServiceId.Google -> when {
            !creds.hasGoogle -> TestResult(false, "Add the web client ID first.")
            else -> {
                val outcome = c.cloud.signIn.attempt(context)
                signInResult(outcome, (c.cloud.auth.state.value as? AuthState.SignedIn)?.email)
            }
        }
        ServiceId.Drive -> driveResult(c.cloud.drive.connect())
    }

    /** Signs out of Supabase on this phone. */
    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }

    /** Queues a sync now. */
    fun syncNow() = c.sync.requestSync()

    /** The bundled combined SQL, or null when the asset is missing. */
    fun setupSql(context: Context): String? =
        runCatching { context.assets.open("setup.sql").bufferedReader().use { it.readText() } }.getOrNull()
}
