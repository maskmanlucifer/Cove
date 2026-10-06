package app.cove.companion

import android.content.Context
import app.cove.companion.core.Clock
import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.EncryptedSessionStore
import app.cove.companion.data.insights.ForegroundTracker
import app.cove.companion.data.insights.JournalSearch
import app.cove.companion.data.insights.NanoInsights
import app.cove.companion.data.insights.NoOpEmbedder
import app.cove.companion.data.insights.SearchIndexer
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.feature.security.AppLock
import app.cove.companion.feature.security.LockAfter
import app.cove.companion.security.DeferredFactory
import app.cove.companion.security.EncryptedDatabase
import app.cove.companion.data.drive.DriveKit
import app.cove.companion.data.media.ImageCompressor
import app.cove.companion.data.media.JournalFiles
import app.cove.companion.data.media.JournalMedia
import app.cove.companion.data.media.PhotoQuality
import app.cove.companion.data.media.SupabaseThumbStore
import app.cove.companion.data.media.VoiceNotePlayer
import app.cove.companion.data.media.VoiceNoteRecorder
import app.cove.companion.data.repo.AssistantRepository
import app.cove.companion.data.repo.ChangeLog
import app.cove.companion.data.repo.HabitRepository
import app.cove.companion.data.repo.JournalRepository
import app.cove.companion.data.repo.MoneyRepository
import app.cove.companion.data.repo.PlanRepository
import app.cove.companion.data.repo.SettingsRepository
import app.cove.companion.data.repo.TodoRepository
import app.cove.companion.data.sync.ConflictResolver
import app.cove.companion.data.sync.SyncManager
import app.cove.companion.core.net.ConnectivityMonitor
import app.cove.companion.data.ai.AiGateway
import app.cove.companion.data.ai.GeminiDirectClient
import app.cove.companion.data.ai.KtorAiGateway
import app.cove.companion.data.ai.NoAiGateway
import app.cove.companion.data.ai.SwitchingAiGateway
import app.cove.companion.data.auth.GoogleSignIn
import app.cove.companion.data.config.CloudServices
import app.cove.companion.data.config.CredentialStore
import app.cove.companion.data.config.Credentials
import app.cove.companion.data.config.ServiceProvider
import app.cove.companion.security.SecretBox
import android.app.PendingIntent
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import app.cove.companion.feature.brief.AndroidSpeechOut
import app.cove.companion.feature.brief.BriefGenerator
import app.cove.companion.feature.brief.BriefPlayer
import app.cove.companion.feature.brief.BriefPrefs
import app.cove.companion.feature.brief.CalendarSource
import app.cove.companion.feature.brief.Geocoder
import app.cove.companion.feature.brief.WeatherClient
import app.cove.companion.feature.suggest.DecisionEngine
import app.cove.companion.feature.suggest.UsageStatsSignals
import app.cove.companion.feature.voice.VoiceKit
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Manual dependency graph, created once by [CoveApp]. */
class AppContainer(private val context: Context, val clock: Clock = Clock.System) {
    private val dbFactory = DeferredFactory(context)
    val database: CoveDatabase = EncryptedDatabase.open(context, dbFactory)

    /** True once the encrypted database is usable; the UI shows nothing but a splash until then. */
    val dbReady = MutableStateFlow(false)

    /** True while an upgrade from a plaintext database is being encrypted (can take many seconds). */
    val dbMigrating: StateFlow<Boolean> get() = dbFactory.migrating

    /** Creates the key and runs the one-time plaintext migration off the main thread, then flips [dbReady]. */
    suspend fun prepareDatabase() {
        withContext(Dispatchers.IO) { dbFactory.prepare() }
        dbReady.value = true
    }
    private val changeLog = ChangeLog(database, clock)

    val settings = SettingsRepository(database, clock, changeLog)
    val plan = PlanRepository(database, clock, changeLog)
    val todos = TodoRepository(database, clock, changeLog)
    val habits = HabitRepository(database, clock, changeLog)
    val money = MoneyRepository(database, clock, changeLog)
    val journal = JournalRepository(database, clock, changeLog)
    val assistant = AssistantRepository(database, clock, changeLog)

    private val httpClient by lazy { HttpClient(OkHttp) }

    /** The user's own credentials, stored encrypted on this phone; Gradle properties only fill blanks in debug builds. */
    val credentialStore by lazy {
        CredentialStore(
            File(context.noBackupFilesDir, "cove-credentials.bin"), SecretBox("cove_credentials"),
            Credentials(BuildConfig.SUPABASE_URL.trim(), BuildConfig.SUPABASE_ANON_KEY.trim(), BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()),
        )
    }

    /** Effective credentials; services rebuild when the part they use changes. */
    val credentials: StateFlow<Credentials> get() = credentialStore.credentials

    private val cloudProvider by lazy {
        ServiceProvider(
            credentials,
            key = { Triple(it.supabaseUrl, it.supabaseAnonKey, it.googleWebClientId) },
            build = ::buildCloud,
            retire = CloudServices::close,
        )
    }

    /** Supabase, sync, Google sign-in and Drive for the current credentials; replaced when those change. */
    val cloud: CloudServices get() = cloudProvider.get()

    /** Emits the current [cloud] and each replacement, for observers that must follow it. */
    fun cloudChanges(): Flow<CloudServices> = cloudProvider.changes()

    /** Drive consent screens to show, following the current [cloud]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun driveConsents(): Flow<PendingIntent?> = cloudChanges().flatMapLatest { it.drive.consentRequests }.flowOn(Dispatchers.Default)

    /** Starts sync and Drive for the current and every later [cloud]. Call once the database is ready. */
    fun startCloud() {
        appScope.launch { cloudChanges().collect { it.start() } }
    }

    private fun buildCloud(c: Credentials): CloudServices {
        val scope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Default)
        val sessions = EncryptedSessionStore(context)
        forgetSessionOfOtherProject(c.supabaseUrl, sessions)
        val auth = AuthRepository(c.supabaseUrl, c.supabaseAnonKey, sessions, httpClient)
        val thumbs = if (auth.enabled) SupabaseThumbStore(c.supabaseUrl, c.supabaseAnonKey, auth, httpClient) else null
        return CloudServices(
            auth,
            SyncManager(context.applicationContext, database, auth, c.supabaseUrl, c.supabaseAnonKey, httpClient, clock, scope),
            DriveKit(
                context.applicationContext, database, journal, settings, changeLog, auth, c.googleWebClientId, httpClient,
                thumbs, journalFiles, clock, scope,
            ),
            GoogleSignIn(auth, c.googleWebClientId, scope),
            scope,
        )
    }

    /** A saved session belongs to one Supabase project; pointing the app at another signs out. */
    private fun forgetSessionOfOtherProject(url: String, sessions: EncryptedSessionStore) {
        if (url.isBlank()) return
        val prefs = context.getSharedPreferences("cove_cloud", Context.MODE_PRIVATE)
        val previous = prefs.getString("project", null)
        if (previous != null && previous != url) sessions.save(null)
        prefs.edit().putString("project", url).apply()
    }

    /** Supabase session; [AuthState.Disabled] when no backend is set up. */
    val auth: AuthRepository get() = cloud.auth

    /** Cloud sync; inert until configured and signed in. */
    val sync: SyncManager get() = cloud.sync
    val conflictResolver by lazy { ConflictResolver(database, plan, todos) { sync.requestSync() } }

    private val aiProvider by lazy {
        ServiceProvider(
            credentials,
            key = { listOf(it.geminiApiKey, it.model, it.fallbackModel, it.supabaseUrl, it.supabaseAnonKey) },
            build = { c ->
                when {
                    c.hasGemini -> GeminiDirectClient(c.geminiApiKey, c.model, c.fallbackModel, httpClient)
                    c.hasSupabase -> KtorAiGateway(c.supabaseUrl, c.supabaseAnonKey, httpClient, tokenProvider = { auth.accessToken() })
                    else -> NoAiGateway
                }
            },
        )
    }

    /** Cloud language layer: Gemini with the user's key, else the legacy Edge Function, else off. Follows credential changes. */
    val aiGateway: AiGateway by lazy { SwitchingAiGateway { aiProvider.get() } }

    /** Voice assistant services (parser, executor, TTS, undo chip). */
    val voice by lazy { VoiceKit(context.applicationContext, this) }
    /** Outlives screens; used for work that must finish after a screen closes, such as indexing a saved entry. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val foreground = ForegroundTracker()

    /** Lock state of the UI; alarms and workers never consult it. */
    val appLock = AppLock(android.os.SystemClock::elapsedRealtime).also { lock ->
        appScope.launch { settings.settings.collect { lock.configure(it.biometricLock, LockAfter.fromKey(it.lockAfter)) } }
    }
    val journalFiles = JournalFiles(context)
    private val appContext = context
    fun voiceRecorder() = VoiceNoteRecorder(appContext)
    fun voicePlayer() = VoiceNotePlayer()
    val journalMedia = JournalMedia(
        journalFiles, ImageCompressor(context), journal, clock,
        quality = { PhotoQuality.webp(settings.settings.first().photoQuality) },
        onSaved = { driveKit.requestUpload() },
    )

    /** Google Drive storage for media and backups; inert until configured, signed in and consented. */
    val driveKit: DriveKit get() = cloud.drive
    val journalSearch = JournalSearch(database, NoOpEmbedder)
    val searchIndexer = SearchIndexer(database, journalSearch, NanoInsights(), NoOpEmbedder, clock, foreground)

    /** Network state for the offline notice and "will sync" markers. */
    val connectivity by lazy { ConnectivityMonitor(context.applicationContext) }

    /** Calendar access for the brief (needs READ_CALENDAR, tolerated when denied). */
    val calendar by lazy { CalendarSource(context.applicationContext) }

    /** Looks up the brief's default city. */
    val geocoder by lazy { Geocoder(httpClient) }

    /** Builds the morning brief from facts and templates. */
    val briefGenerator by lazy {
        val prefs = BriefPrefs(context)
        BriefGenerator(
            this, context.applicationContext, WeatherClient(prefs, clock::now), prefs, calendar,
            aiGateway, clock,
        )
    }

    /** Reads the brief aloud; start it from anywhere with [BriefPlayer.playToday]. */
    val briefPlayer by lazy {
        BriefPlayer(speech = { AndroidSpeechOut(context) }, today = { briefGenerator.today() })
    }

    /** Rule-based suggestions shown on Today. */
    val decisions by lazy { DecisionEngine(this, UsageStatsSignals(context), clock) }
}
