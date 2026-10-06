package app.cove.companion.feature.connect

import app.cove.companion.data.auth.SignInOutcome
import app.cove.companion.data.config.CredentialField
import app.cove.companion.data.config.CredentialValidator
import app.cove.companion.data.config.Credentials
import app.cove.companion.data.config.TestResult
import app.cove.companion.data.drive.DriveFailure
import app.cove.companion.data.drive.DriveToken

/** The four services on the Connect screen. */
enum class ServiceId(val title: String, val blurb: String, val fields: List<CredentialField>) {
    Supabase("Supabase", "Sync between devices", listOf(CredentialField.SupabaseUrl, CredentialField.SupabaseAnonKey)),
    Google("Google sign-in", "Keeps your space yours", listOf(CredentialField.GoogleWebClientId)),
    Drive("Google Drive", "Photos and backups, optional", emptyList()),
    Gemini(
        "Gemini", "Trickier voice commands",
        listOf(CredentialField.GeminiApiKey, CredentialField.GeminiModel, CredentialField.GeminiFallbackModel),
    ),
}

/** Where a service stands; [label] is what the row shows. */
enum class ServiceStatus(val label: String) {
    NotSet("Not set"),
    Saved("Saved"),
    Connected("Connected"),
    NeedsAttention("Needs attention"),
}

/** Everything the status of a service depends on. */
data class ConnectFacts(
    val credentials: Credentials,
    val signedIn: Boolean,
    val driveConnected: Boolean,
    val tests: Map<ServiceId, TestResult>,
)

/**
 * Row status: missing values are Not set, bad formats or a failed test Need attention, and Connected needs proof
 * (a passed test, a live session, or Google's Drive grant), otherwise the values are just Saved.
 */
fun statusOf(service: ServiceId, f: ConnectFacts): ServiceStatus {
    val c = f.credentials
    val test = f.tests[service]
    val failed = test?.ok == false
    return when (service) {
        ServiceId.Drive -> when {
            failed -> ServiceStatus.NeedsAttention
            f.signedIn && f.driveConnected -> ServiceStatus.Connected
            else -> ServiceStatus.NotSet
        }
        else -> {
            val set = when (service) {
                ServiceId.Supabase -> c.hasSupabase
                ServiceId.Google -> c.hasGoogle
                else -> c.hasGemini
            }
            val invalid = service.fields.any { CredentialValidator.check(it, c[it]) != null }
            val live = when (service) {
                ServiceId.Supabase, ServiceId.Google -> f.signedIn
                else -> false
            }
            when {
                !set -> ServiceStatus.NotSet
                invalid || failed -> ServiceStatus.NeedsAttention
                test?.ok == true || live -> ServiceStatus.Connected
                else -> ServiceStatus.Saved
            }
        }
    }
}

/** Plain-language result of a Google sign-in attempt; [email] is shown on success. */
fun signInResult(outcome: SignInOutcome, email: String?): TestResult = when (outcome) {
    SignInOutcome.Success -> TestResult(true, if (email != null) "Signed in as $email." else "Signed in.")
    SignInOutcome.NotConfigured -> TestResult(false, "Add the Supabase details and the web client ID first. Sign-in goes through your Supabase project.")
    SignInOutcome.Cancelled -> TestResult(false, "Sign-in was cancelled.")
    SignInOutcome.NoAccount -> TestResult(false, "No Google account is available on this phone. Add one in Android settings, then try again.")
    SignInOutcome.Offline -> TestResult(false, "Cannot reach Google or Supabase. Check your internet connection.")
    SignInOutcome.Rejected ->
        TestResult(false, "Supabase did not accept the sign-in. In Supabase, turn on the Google provider and paste the same web client ID and its secret.")
    SignInOutcome.Failed ->
        TestResult(false, "Google sign-in did not work. Check the web client ID, and that an Android OAuth client with this package name and SHA-1 exists in the same Google Cloud project.")
}

/** Plain-language result of asking Google for Drive access. */
fun driveResult(token: DriveToken): TestResult = when (token) {
    is DriveToken.Granted -> TestResult(true, "Drive is connected. Cove saves to its own folder there.")
    DriveToken.NeedsConsent -> TestResult(true, "Approve access on the Google screen that opens, then come back.")
    DriveToken.Unavailable -> TestResult(false, "Sign in with Google first. Drive uses the same sign-in.")
    is DriveToken.Failed -> TestResult(
        false,
        when (token.reason) {
            DriveFailure.UnknownApp ->
                "Google does not recognise this build of Cove. Add an Android OAuth client with the package name and SHA-1 shown above."
            DriveFailure.Offline -> "Cannot reach Google. Check your internet connection."
            DriveFailure.Cancelled -> "Drive access was cancelled."
            DriveFailure.Other -> "Google would not allow Drive access. Check that the Drive API is on and the consent screen is In production."
        },
    )
}

/** Stable dashboard pages; a Supabase project page is only built when the project URL is known. */
object ConnectLinks {
    const val SUPABASE_DASHBOARD = "https://supabase.com/dashboard"
    const val GOOGLE_CREDENTIALS = "https://console.cloud.google.com/apis/credentials"
    const val GOOGLE_CONSENT = "https://console.cloud.google.com/apis/credentials/consent"
    const val AI_STUDIO_KEYS = "https://aistudio.google.com/apikey"
    const val DRIVE_API = "https://console.cloud.google.com/apis/library/drive.googleapis.com"

    /** The project reference of a `https://<ref>.supabase.co` URL, or null. */
    fun projectRef(url: String): String? =
        Regex("""^https://([a-z0-9]+)\.supabase\.co/?$""").find(url.trim())?.groupValues?.get(1)

    /** The project's API settings page, else the dashboard. */
    fun supabaseApi(url: String): String =
        projectRef(url)?.let { "$SUPABASE_DASHBOARD/project/$it/settings/api" } ?: SUPABASE_DASHBOARD

    /** The project's SQL editor, else the dashboard. */
    fun supabaseSql(url: String): String =
        projectRef(url)?.let { "$SUPABASE_DASHBOARD/project/$it/sql/new" } ?: SUPABASE_DASHBOARD

    /** The project's authentication providers page, else the dashboard. */
    fun supabaseProviders(url: String): String =
        projectRef(url)?.let { "$SUPABASE_DASHBOARD/project/$it/auth/providers" } ?: SUPABASE_DASHBOARD
}

/** Input hints for [field]: label, placeholder and whether it is a secret. */
data class FieldHint(val label: String, val placeholder: String, val secret: Boolean = false)

/** How [field] is labelled and masked in a sheet. */
fun fieldHint(field: CredentialField): FieldHint = when (field) {
    CredentialField.SupabaseUrl -> FieldHint("Project URL", "https://abcdwxyz.supabase.co")
    CredentialField.SupabaseAnonKey -> FieldHint("Anon public key", "eyJ...", secret = true)
    CredentialField.GoogleWebClientId -> FieldHint("Web client ID", "1234-abc.apps.googleusercontent.com")
    CredentialField.GeminiApiKey -> FieldHint("API key", "AQ. or AIza...", secret = true)
    CredentialField.GeminiModel -> FieldHint("Model", Credentials.DEFAULT_MODEL)
    CredentialField.GeminiFallbackModel -> FieldHint("Fallback model", Credentials.DEFAULT_FALLBACK_MODEL)
}
