package app.cove.companion

import android.content.Context
import app.cove.companion.core.Clock
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.repo.AssistantRepository
import app.cove.companion.data.repo.ChangeLog
import app.cove.companion.data.repo.HabitRepository
import app.cove.companion.data.repo.JournalRepository
import app.cove.companion.data.repo.MoneyRepository
import app.cove.companion.data.repo.PlanRepository
import app.cove.companion.data.repo.SettingsRepository
import app.cove.companion.data.repo.TodoRepository
import app.cove.companion.core.net.ConnectivityMonitor
import app.cove.companion.data.ai.KtorAiGateway
import app.cove.companion.feature.brief.AndroidSpeechOut
import app.cove.companion.feature.brief.BriefGenerator
import app.cove.companion.feature.brief.BriefPlayer
import app.cove.companion.feature.brief.BriefPrefs
import app.cove.companion.feature.brief.CalendarSource
import app.cove.companion.feature.brief.WeatherClient
import app.cove.companion.feature.suggest.DecisionEngine
import app.cove.companion.feature.suggest.UsageStatsSignals
import app.cove.companion.feature.voice.VoiceKit

/** Manual dependency graph, created once by [CoveApp]. */
class AppContainer(private val context: Context, val clock: Clock = Clock.System) {
    val database: CoveDatabase = CoveDatabase.create(context)
    private val changeLog = ChangeLog(database, clock)

    val settings = SettingsRepository(database, clock)
    val plan = PlanRepository(database, clock, changeLog)
    val todos = TodoRepository(database, clock, changeLog)
    val habits = HabitRepository(database, clock, changeLog)
    val money = MoneyRepository(database, clock, changeLog)
    val journal = JournalRepository(database, clock, changeLog)
    val assistant = AssistantRepository(database, clock)

    /** Voice assistant services (parser, executor, TTS, undo chip). */
    val voice by lazy { VoiceKit(context.applicationContext, this) }

    /** Network state for the offline notice and "will sync" markers. */
    val connectivity by lazy { ConnectivityMonitor(context.applicationContext) }

    /** Calendar access for the brief (needs READ_CALENDAR, tolerated when denied). */
    val calendar by lazy { CalendarSource(context.applicationContext) }

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
