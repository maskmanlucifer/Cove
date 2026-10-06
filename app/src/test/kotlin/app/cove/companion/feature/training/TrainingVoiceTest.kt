package app.cove.companion.feature.training

import app.cove.companion.ai.model.SpokenSet
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.schema.IntentSchema
import app.cove.companion.core.Clock
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity
import app.cove.companion.feature.training.voice.BodyWeightUndo
import app.cove.companion.feature.training.voice.SetsPreview
import app.cove.companion.feature.training.voice.TrainingOutcome
import app.cove.companion.feature.training.voice.TrainingUndo
import app.cove.companion.feature.training.voice.TrainingVoice
import app.cove.companion.feature.voice.exec.IntentExecutor
import app.cove.companion.feature.voice.exec.VoiceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingVoiceTest {
    private class Store : VoiceStore {
        val commands = mutableListOf<VoiceCommandEntity>()
        override suspend fun todoCategories() = emptyList<TodoCategoryEntity>()
        override suspend fun expenseCategories() = emptyList<ExpenseCategoryEntity>()
        override suspend fun habits() = emptyList<HabitEntity>()
        override suspend fun alarms() = emptyList<AlarmEntity>()
        override suspend fun nextEvent(from: Long): EventEntity? = null
        override suspend fun addTodo(title: String, categoryId: String?, dueAt: Long?, remind: Boolean) = TodoEntity("t", categoryId, title)
        override suspend fun deleteTodo(id: String) = Unit
        override suspend fun saveAlarm(alarm: AlarmEntity) = alarm
        override suspend fun getAlarm(id: String): AlarmEntity? = null
        override suspend fun deleteAlarm(id: String) = Unit
        override suspend fun saveExpense(expense: ExpenseEntity) = Unit
        override suspend fun deleteExpense(id: String) = Unit
        override suspend fun saveHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabit(id: String) = Unit
        override suspend fun isHabitTicked(habitId: String, day: LocalDate) = false
        override suspend fun toggleHabit(habitId: String, day: LocalDate) = Unit
        override suspend fun saveJournal(entry: JournalEntryEntity) = Unit
        override suspend fun deleteJournal(id: String) = Unit
        override suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?) =
            VoiceCommandEntity("c${commands.size}", transcript, intent, undoPayload, createdAt = 0).also { commands += it }
        override suspend fun lastCommand() = commands.lastOrNull { !it.undone }
        override suspend fun commandById(id: String) = commands.firstOrNull { it.id == id }
        override suspend fun markUndone(id: String) { commands.replaceAll { if (it.id == id) it.copy(undone = true) else it } }
    }

    private class Fake : TrainingVoice {
        val logged = mutableListOf<String>()
        var undone: TrainingUndo? = null
        override suspend fun exerciseNames() = listOf("Bench press")
        override suspend fun preview(intent: VoiceIntent.LogSets): SetsPreview? = null
        override suspend fun logSets(intent: VoiceIntent.LogSets): TrainingOutcome {
            logged += intent.exercise
            return TrainingOutcome("Logged ${intent.sets.size} sets of Bench press", undo = TrainingUndo(sets = listOf("a", "b", "c")), label = "removed 3 sets")
        }
        override suspend fun startWorkout(intent: VoiceIntent.StartWorkout) = TrainingOutcome("Started Push day", undo = TrainingUndo(sessions = listOf("s1")), route = "training/session")
        override suspend fun logBodyWeight(intent: VoiceIntent.LogBodyWeight) = TrainingOutcome("Logged 68.4 kg", undo = TrainingUndo(bodyWeights = listOf(BodyWeightUndo(1, null))))
        override suspend fun nextWorkout() = "Next is Legs on Thursday at 7 pm"
        override suspend fun undo(undo: TrainingUndo) { undone = undo }
    }

    private val store = Store()
    private val training = Fake()
    private val executor = IntentExecutor(store, Clock { 0L }, training)

    @Test fun savingSetsRecordsAnUndoPayloadAndUndoReachesTheTrainingSide() = runBlocking {
        val r = executor.execute("bench", listOf(VoiceIntent.LogSets("Bench press", listOf(SpokenSet(62.5, 8), SpokenSet(62.5, 8), SpokenSet(62.5, 6)))))
        assertEquals("Logged 3 sets of Bench press", r.summary)
        assertNotNull(r.commandId)
        val undo = executor.undo(r.commandId)
        assertTrue(undo.summary, undo.summary.startsWith("Undone"))
        assertEquals(listOf("a", "b", "c"), training.undone?.sets)
    }

    @Test fun startWorkoutOpensTheSessionAfterSaving() = runBlocking {
        val r = executor.execute("start workout", listOf(VoiceIntent.StartWorkout()))
        assertEquals("training/session", r.route)
        assertEquals("Started Push day", r.summary)
    }

    @Test fun weighInAndNextWorkoutQuery() = runBlocking {
        assertEquals("Logged 68.4 kg", executor.execute("w", listOf(VoiceIntent.LogBodyWeight(68.4))).summary)
        assertEquals("Next is Legs on Thursday at 7 pm", executor.execute("n", listOf(VoiceIntent.QueryNextWorkout)).summary)
        assertNull(executor.execute("n", listOf(VoiceIntent.QueryNextWorkout)).commandId)
    }

    @Test fun withoutTrainingTheExecutorExplains() = runBlocking {
        val r = IntentExecutor(store, Clock { 0L }).execute("x", listOf(VoiceIntent.StartWorkout()))
        assertEquals("Training isn't available", r.summary)
    }

    @Test fun schemaAcceptsAndRejectsTrainingIntents() {
        val ok = IntentSchema.parse("""{"intents":[{"type":"log_sets","exercise":"Bench press","unit":"kg","sets":[{"weight":62.5,"reps":8},{"weight":null,"reps":6}]}]}""")!!
        val log = ok.single() as VoiceIntent.LogSets
        assertEquals(listOf(SpokenSet(62.5, 8), SpokenSet(null, 6)), log.sets)
        assertEquals("kg", log.unit)
        assertEquals(VoiceIntent.StartWorkout("Push"), IntentSchema.parse("""{"intents":[{"type":"start_workout","day":"Push"}]}""")!!.single())
        assertEquals(VoiceIntent.LogBodyWeight(68.4, null), IntentSchema.parse("""{"intents":[{"type":"log_body_weight","weight":68.4}]}""")!!.single())
        assertEquals(VoiceIntent.QueryNextWorkout, IntentSchema.parse("""{"intents":[{"type":"next_workout"}]}""")!!.single())
        listOf(
            """{"intents":[{"type":"log_sets","exercise":"Bench","sets":[]}]}""",
            """{"intents":[{"type":"log_sets","exercise":"Bench","sets":[{"weight":62.5,"reps":0}]}]}""",
            """{"intents":[{"type":"log_sets","exercise":"Bench","sets":[{"weight":-3,"reps":5}]}]}""",
            """{"intents":[{"type":"log_sets","sets":[{"reps":5}]}]}""",
            """{"intents":[{"type":"log_body_weight","weight":5}]}""",
            """{"intents":[{"type":"log_body_weight","weight":68,"unit":"stone"}]}""",
        ).forEach { assertNull(it, IntentSchema.parse(it)) }
        assertNotNull(IntentSchema.validateCloud("""{"intents":[{"type":"log_sets","exercise":"Bench","sets":[{"reps":5}]}],"confidence":0.9}"""))
    }
}
