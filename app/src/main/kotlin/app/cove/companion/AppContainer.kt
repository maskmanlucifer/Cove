package app.cove.companion

import android.content.Context
import app.cove.companion.core.Clock
import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.EncryptedSessionStore
import app.cove.companion.ai.AiPolicy
import app.cove.companion.ai.AiProviders
import app.cove.companion.ai.AiRouter
import app.cove.companion.ai.AiService
import app.cove.companion.ai.DefaultAiService
import app.cove.companion.ai.ForegroundTracker
import app.cove.companion.ai.provider.cloud.CloudGateway
import app.cove.companion.ai.provider.cloud.CloudProvider
import app.cove.companion.ai.provider.cloud.EdgeFunctionGateway
import app.cove.companion.ai.provider.cloud.GeminiConnectionCheck
import app.cove.companion.ai.provider.cloud.GeminiDirectClient
import app.cove.companion.ai.provider.cloud.NoCloudGateway
import app.cove.companion.ai.provider.ondevice.AndroidSpeechProvider
import app.cove.companion.ai.provider.ondevice.MlKitNanoClient
import app.cove.companion.ai.provider.ondevice.MlKitSpeechProvider
import app.cove.companion.ai.provider.ondevice.NanoProvider
import app.cove.companion.ai.provider.ondevice.UnbundledEmbeddingProvider
import app.cove.companion.ai.provider.rules.RuleIntentProvider
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.TypedSpeechProvider
import app.cove.companion.data.insights.JournalSearch
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
import app.cove.companion.data.categorize.LiveCategoryResolver
import app.cove.companion.data.repo.MoneyRepository
import app.cove.companion.data.repo.PlanRepository
import app.cove.companion.data.repo.SettingsRepository
import app.cove.companion.data.repo.TodoRepository
import app.cove.companion.data.repo.TrainingRepository
import app.cove.companion.data.backup.RoomBackupStore
import app.cove.companion.data.sync.ConflictResolver
import app.cove.companion.data.sync.RoomSyncStore
import app.cove.companion.data.sync.SyncTables
import app.cove.companion.data.sync.SyncManager
import app.cove.companion.core.net.ConnectivityMonitor
import app.cove.companion.data.auth.GoogleSignIn
import app.cove.companion.data.config.CloudServices
import app.cove.companion.data.config.CredentialStore
import app.cove.companion.data.config.Credentials
import app.cove.companion.data.config.ServiceProvider
import app.cove.companion.security.SecretBox
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.resilience.DatabaseGuard
import app.cove.companion.resilience.DbCheck
import app.cove.companion.resilience.RecoveryReason
import app.cove.companion.resilience.SafeMode
import app.cove.companion.resilience.StartupState
import android.app.PendingIntent
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import app.cove.companion.feature.brief.AndroidSpeechOut
import app.cove.companion.feature.today.NextCardMemory
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

    /** [StartupState.Ready] once the database checked out; [StartupState.Recovery] when the UI must show the Recovery screen instead. */
    val startup = MutableStateFlow<StartupState>(StartupState.Checking)

    /** True while an upgrade from a plaintext database is being encrypted (can take many seconds). */
    val dbMigrating: StateFlow<Boolean> get() = dbFactory.migrating

    /**
     * Creates the key (never when an encrypted database exists), runs the one-time plaintext migration and opens the
     * file off the main thread. A failure is classified, noted and turned into [startup] = Recovery; it never throws.
     * [crashLoop] (decided from the crash notes before this launch) also leads to Recovery, with the database fine.
     */
    suspend fun prepareDatabase(crashLoop: Boolean = false, beforeReady: suspend () -> Unit = {}): DbCheck {
        val check = withContext(Dispatchers.IO) {
            DatabaseGuard(::openDatabase, { CrashHandler.report("startup", it) }).check()
        }
        if (forcedRecovery) return check
        val reason = SafeMode.reason(check, crashLoop)
        if (reason == null) {
            beforeReady()
            dbReady.value = true
            startup.value = StartupState.Ready
        } else {
            startup.value = StartupState.Recovery(reason)
        }
        return check
    }

    @Volatile private var forcedRecovery = false

    private fun openDatabase() {
        dbFactory.prepare()
        database.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
    }

    /** Debug only: shows the Recovery screen for [reason] without damaging anything. */
    fun forceRecovery(reason: RecoveryReason) {
        forcedRecovery = true
        dbReady.value = false
        startup.value = StartupState.Recovery(reason)
    }
    private val changeLog = ChangeLog(database, clock)

    /** Backup/restore access to the database, used by the Recovery screen's staged restore as well as Drive. */
    fun backupStore() = RoomBackupStore(RoomSyncStore(database), changeLog, SyncTables.all)

    val settings = SettingsRepository(database, clock, changeLog)
    val plan = PlanRepository(database, clock, changeLog)
    val todos = TodoRepository(database, clock, changeLog)
    val habits = HabitRepository(database, clock, changeLog)
    val money = MoneyRepository(database, clock, changeLog)

    /** Programme, sessions, sets and body weight (see `docs/TRAINING.md`). */
    val training = TrainingRepository(database, clock, changeLog)

    /** Files spoken and typed expenses under the user's own categories (see `docs/CATEGORIZATION.md`). */
    val categoryResolver by lazy { LiveCategoryResolver(money, appScope) }
    val journal = JournalRepository(database, clock, changeLog)
    val assistant = AssistantRepository(database, clock, changeLog)

    /** Hide-until state of Today's Next card (survives restarts). */
    val nextCard = NextCardMemory(context.applicationContext)

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

    private val geminiGateway by lazy {
        ServiceProvider<CloudGateway>(
            credentials,
            key = { listOf(it.geminiApiKey, it.model, it.fallbackModel) },
            build = { c -> if (c.hasGemini) GeminiDirectClient(c.geminiApiKey, c.model, c.fallbackModel, httpClient) else NoCloudGateway },
        )
    }

    /** The legacy Edge Function is used only when no Gemini key is set (a key always wins). */
    private val edgeGateway by lazy {
        ServiceProvider<CloudGateway>(
            credentials,
            key = { listOf(it.hasGemini, it.supabaseUrl, it.supabaseAnonKey) },
            build = { c ->
                if (!c.hasGemini && c.hasSupabase) EdgeFunctionGateway(c.supabaseUrl, c.supabaseAnonKey, httpClient, tokenProvider = { auth.accessToken() })
                else NoCloudGateway
            },
        )
    }

    /**
     * The AI facade: on-device first where the rules allow, cloud as fallback, never cloud for journal content.
     * Provider order per capability is declared here; see `docs/AI.md`.
     */
    val ai: AiService by lazy {
        val nano = NanoProvider(MlKitNanoClient())
        val gemini = CloudProvider(CloudProvider.GEMINI_ID, "API key") { geminiGateway.get() }
        val edge = CloudProvider(CloudProvider.EDGE_ID, "Supabase project") { edgeGateway.get() }
        val typed = TypedSpeechProvider()
        val rules = RuleParser(clock, categoryResolver)
        val providers = AiProviders(
            intent = listOf(RuleIntentProvider(rules), nano, gemini, edge),
            brief = listOf(nano, gemini, edge),
            speech = listOf(
                MlKitSpeechProvider(),
                AndroidSpeechProvider(context.applicationContext, AndroidSpeechProvider.Mode.OnDevice),
                AndroidSpeechProvider(context.applicationContext, AndroidSpeechProvider.Mode.System),
                typed,
            ),
            caption = listOf(nano),
            summary = listOf(nano),
            embedding = listOf(UnbundledEmbeddingProvider()),
            category = listOf(nano, gemini),
        )
        val router = AiRouter(providers, AiPolicy(foreground) { connectivity.online.value }, speechLog = { if (BuildConfig.DEBUG) android.util.Log.d("CoveVoice", it) })
        DefaultAiService(router, providers, typed, rules, clock) { key, model ->
            GeminiConnectionCheck.run(GeminiDirectClient(key, model, "", httpClient))
        }
    }

    /** Voice assistant services (parser, executor, TTS, undo chip). */
    val voice by lazy { VoiceKit(context.applicationContext, this) }
    /** Outlives screens; used for work that must finish after a screen closes, such as indexing a saved entry. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CrashHandler.coroutineHandler("appScope"))
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
    val journalSearch by lazy { JournalSearch(database, ai) }
    val searchIndexer by lazy { SearchIndexer(database, journalSearch, ai, clock) }

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
            ai, clock,
        )
    }

    /** Reads the brief aloud; start it from anywhere with [BriefPlayer.playToday]. */
    val briefPlayer by lazy {
        BriefPlayer(speech = { AndroidSpeechOut(context) }, today = { briefGenerator.today() })
    }

    /** Rule-based suggestions shown on Today. */
    val decisions by lazy { DecisionEngine(this, UsageStatsSignals(context), clock) }
}
