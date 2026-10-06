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
import app.cove.companion.data.ai.KtorAiGateway
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

    /** Hide-until state of Today's Next card (survives restarts). */
    val nextCard = NextCardMemory(context.applicationContext)

    private val httpClient by lazy { HttpClient(OkHttp) }

    /** Supabase session; [AuthState.Disabled] when the build has no backend configured. */
    val auth by lazy {
        AuthRepository(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, EncryptedSessionStore(context), httpClient)
    }

    /** Cloud sync; inert until configured and signed in. */
    val sync by lazy {
        SyncManager(context.applicationContext, database, auth, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, httpClient, clock, appScope)
    }
    val conflictResolver by lazy { ConflictResolver(database, plan, todos, sync::requestSync) }

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
    val driveKit by lazy {
        DriveKit(
            context.applicationContext, database, journal, settings, changeLog, auth, BuildConfig.GOOGLE_WEB_CLIENT_ID, httpClient,
            if (auth.enabled) SupabaseThumbStore(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, auth, httpClient) else null,
            journalFiles, clock, appScope,
        )
    }
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
            KtorAiGateway(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY), clock,
        )
    }

    /** Reads the brief aloud; start it from anywhere with [BriefPlayer.playToday]. */
    val briefPlayer by lazy {
        BriefPlayer(speech = { AndroidSpeechOut(context) }, today = { briefGenerator.today() })
    }

    /** Rule-based suggestions shown on Today. */
    val decisions by lazy { DecisionEngine(this, UsageStatsSignals(context), clock) }
}
