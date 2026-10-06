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
import app.cove.companion.data.media.ImageCompressor
import app.cove.companion.data.media.JournalFiles
import app.cove.companion.data.media.JournalMedia
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
import app.cove.companion.feature.voice.VoiceKit
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph, created once by [CoveApp]. */
class AppContainer(private val context: Context, val clock: Clock = Clock.System) {
    val database: CoveDatabase = CoveDatabase.create(context)
    private val changeLog = ChangeLog(database, clock)

    val settings = SettingsRepository(database, clock, changeLog)
    val plan = PlanRepository(database, clock, changeLog)
    val todos = TodoRepository(database, clock, changeLog)
    val habits = HabitRepository(database, clock, changeLog)
    val money = MoneyRepository(database, clock, changeLog)
    val journal = JournalRepository(database, clock, changeLog)
    val assistant = AssistantRepository(database, clock, changeLog)

    private val httpClient by lazy { HttpClient(OkHttp) }

    /** Supabase session; [AuthState.Disabled] when the build has no backend configured. */
    val auth = AuthRepository(
        BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, EncryptedSessionStore(context), httpClient,
    )

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
    val journalFiles = JournalFiles(context)
    private val appContext = context
    fun voiceRecorder() = VoiceNoteRecorder(appContext)
    fun voicePlayer() = VoiceNotePlayer()
    val journalMedia = JournalMedia(journalFiles, ImageCompressor(context), journal, clock)
    val journalSearch = JournalSearch(database, NoOpEmbedder)
    val searchIndexer = SearchIndexer(database, journalSearch, NanoInsights(), NoOpEmbedder, clock, foreground)
}
