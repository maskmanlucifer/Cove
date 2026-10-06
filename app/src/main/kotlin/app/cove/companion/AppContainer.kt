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
}
