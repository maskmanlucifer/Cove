package app.cove.companion.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.BuildConfig
import app.cove.companion.core.rupees
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.model.VoiceIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the voice frames is showing. */
enum class Stage { Listening, Result, Partial, Typing, Answer, Trouble }

/** Everything the Voice screen draws. */
data class VoiceState(
    val stage: Stage = Stage.Listening,
    val transcript: String = "",
    val seconds: Int = 0,
    val level: Float = 0f,
    val onDevice: Boolean = true,
    /** An engine is capturing audio: the person can speak now. */
    val ready: Boolean = false,
    /** Voice cannot be used (permission denied or no recognizer); the typing UI explains it. */
    val micOff: Boolean = false,
    /** Why listening failed, for [Stage.Trouble]; [troubleCode] is the engine's error code (0 if none). */
    val trouble: SpeechFailure? = null,
    val troubleCode: Int = 0,
    val typed: String = "",
    val drafts: List<VoiceIntent> = emptyList(),
    val guesses: List<VoiceIntent> = emptyList(),
    val categories: List<String> = emptyList(),
    val expenseCategories: List<String> = emptyList(),
    val moneyHint: String? = null,
    val answer: String = "",
    val heardByVoice: Boolean = true,
    val busy: Boolean = false,
    val done: Boolean = false,
)

/** Drives listening, understanding, confirming and saving a voice command. */
class VoiceViewModel(private val c: AppContainer) : ViewModel() {
    private val kit get() = c.voice
    private val typed = c.ai.openTyped()
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var job: Job? = null
    private var session: SpeechSession? = null
    private var started = false
    private var frozen = false
    private val gate = ListenGate()

    /** Starts listening once; debug builds may instead jump to a frame via [VoiceDebug]. */
    fun begin() {
        if (started) return
        started = true
        val debug = if (BuildConfig.DEBUG) VoiceDebug.consume() else null
        if (debug != null) { frozen = true; applyDebug(debug) } else listen()
    }

    /** The permission was just granted: start (first time) or retry after the permission explanation. */
    fun micGranted() {
        if (!started) begin() else if (_state.value.trouble == SpeechFailure.PermissionDenied) listen()
    }

    /** Re-checks after the app returns to the foreground. */
    fun resume(micGranted: Boolean) {
        val s = _state.value
        if (frozen) return
        if (s.trouble == SpeechFailure.PermissionDenied && micGranted) listen()
        else if (s.stage == Stage.Listening && job?.isActive != true && started && !s.busy) listen()
    }

    /** Stops the microphone when the app leaves the foreground. */
    fun pause() {
        if (_state.value.stage == Stage.Listening) { job?.cancel(); gate.abort() }
    }

    /** The microphone permission was refused (or is off): explain it and offer the right next step. */
    fun micDenied() = trouble(SpeechFailure.PermissionDenied, 0)

    private fun trouble(reason: SpeechFailure, code: Int) {
        _state.update { it.copy(stage = Stage.Trouble, trouble = reason, troubleCode = code, ready = false, level = 0f, busy = false, heardByVoice = false) }
    }

    /**
     * Starts one listening run. Ignored while a run is starting or listening (double taps, recompositions, a second
     * QuickListen); a previous run is fully closed before the next recognizer is created.
     */
    fun listen() {
        val token = gate.tryStart() ?: return
        val previous = job
        kit.speaker.stop()
        _state.update { VoiceState(categories = it.categories) }
        job = viewModelScope.launch {
            try {
                previous?.cancelAndJoin()
                val picked = c.ai.openSpeech()
                session = picked
                val ticker = launch { while (true) { delay(1000); _state.update { it.copy(seconds = it.seconds + 1) } } }
                try {
                    collect(picked, showsLevel = true)
                } finally {
                    ticker.cancel()
                }
            } finally {
                gate.end(token)
            }
        }
    }

    /** Stop button and Done: ask the engine for its final transcript (ignored until it is ready). */
    fun finish() {
        if (!_state.value.ready) return
        viewModelScope.launch { session?.stop() }
    }

    /** Type instead, Type it and Edit: the text box, prefilled with what was heard. */
    fun typeInstead() {
        job?.cancel()
        gate.abort()
        _state.update { it.copy(stage = Stage.Typing, typed = it.transcript, heardByVoice = false) }
        startTypedWait()
    }

    fun onTyped(text: String) = _state.update { it.copy(typed = text) }

    fun submitTyped() {
        val text = _state.value.typed.trim()
        if (text.isNotEmpty()) typed.submit(text)
    }

    private fun startTypedWait() {
        job?.cancel()
        job = viewModelScope.launch { collect(typed, showsLevel = false) }
    }

    private suspend fun collect(source: SpeechSession, showsLevel: Boolean) {
        var final: String? = null
        var failure: SpeechEvent.Failure? = null
        source.events.collect { e ->
            when (e) {
                is SpeechEvent.Ready -> _state.update { it.copy(ready = true, onDevice = e.source.location.isLocal) }
                SpeechEvent.Began -> Unit
                is SpeechEvent.Partial -> _state.update { it.copy(transcript = e.text) }
                is SpeechEvent.Level -> if (showsLevel) _state.update { it.copy(level = e.value) }
                is SpeechEvent.Final -> final = e.text
                is SpeechEvent.Failure -> failure = e
            }
        }
        val text = (final ?: _state.value.transcript).trim()
        val failed = failure
        if (showsLevel && failed != null && (failed.reason == SpeechFailure.PermissionDenied || text.isEmpty())) return trouble(failed.reason, failed.code)
        if (showsLevel && text.isEmpty()) return trouble(SpeechFailure.NoMatch, 0)
        understand(text, heardByVoice = showsLevel)
    }

    private suspend fun understand(text: String, heardByVoice: Boolean) {
        _state.update { it.copy(busy = true, transcript = text, heardByVoice = heardByVoice, level = 0f) }
        val ctx = IntentContext(
            habits = c.habits.habits.first().map { it.name },
            todoCategories = c.todos.categories.first().map { it.name },
            exercises = kit.training.exerciseNames(),
        )
        val categories = ctx.todoCategories
        val expenseCats = c.money.categories.first().filter { it.kind == "spending" }.map { it.name }
        val outcome = c.ai.parseIntent(text, ctx)
        when (val result = outcome) {
            is AiResult.Ok -> {
                val only = result.value.intents.singleOrNull()
                if (only == VoiceIntent.UndoLast || only == VoiceIntent.QueryNext || only == VoiceIntent.QueryNextWorkout) {
                    val r = kit.executor.execute(text, result.value.intents)
                    if (only == VoiceIntent.UndoLast) {
                        kit.feedback.show(r.summary, null)
                        _state.update { it.copy(busy = false, done = true) }
                    } else {
                        _state.update { it.copy(busy = false, stage = Stage.Answer, answer = r.summary) }
                    }
                    kit.speaker.speak(r.summary)
                } else {
                    val hint = moneyHint(only)
                    _state.update { it.copy(busy = false, stage = Stage.Result, drafts = result.value.intents, categories = categories, expenseCategories = expenseCats, moneyHint = hint) }
                }
            }
            is AiResult.Failed ->
                _state.update { it.copy(busy = false, stage = Stage.Partial, guesses = c.ai.guessIntents(text), transcript = text) }
        }
    }

    /** "Food so far: ₹7,340 of ₹9,000." for an expense draft. */
    private suspend fun moneyHint(intent: VoiceIntent?): String? {
        if (intent !is VoiceIntent.LogExpense || intent.received) return null
        val cat = c.money.categories.first().firstOrNull { it.name.equals(intent.category, true) } ?: return null
        val day = c.clock.now().toLocalDate()
        val spent = c.money.expenses(day.withDayOfMonth(1).startOfDayMillis(), day.plusDays(1).startOfDayMillis() - 1).first()
            .filter { it.categoryId == cat.id && it.kind == "spent" }.sumOf { it.amountPaise } + intent.amountPaise
        return if (cat.budgetPaise > 0) "${cat.name} so far: ${rupees(spent)} of ${rupees(cat.budgetPaise)}." else "${cat.name} so far: ${rupees(spent)}."
    }

    /** Changes the category of the [index]th to-do across all drafts. */
    fun setTodoCategory(index: Int, name: String) = _state.update { s ->
        var n = 0
        s.copy(
            drafts = s.drafts.map { d ->
                if (d !is VoiceIntent.AddTodos) d
                else VoiceIntent.AddTodos(d.items.map { item -> if (n++ == index) item.copy(category = name) else item })
            },
        )
    }

    /** Changes the expense draft's category; the pick is taught to the categorizer when the draft is saved. */
    fun setExpenseCategory(name: String) {
        if (!categoryPicked) {
            categoryPicked = true
            categoryBeforePick = _state.value.drafts.filterIsInstance<VoiceIntent.LogExpense>().firstOrNull()?.category
        }
        _state.update { s ->
            s.copy(drafts = s.drafts.map { if (it is VoiceIntent.LogExpense) it.copy(category = name) else it })
        }
    }

    private var categoryPicked = false
    private var categoryBeforePick: String? = null

    private suspend fun teachPickedCategory(drafts: List<VoiceIntent>) {
        val e = drafts.filterIsInstance<VoiceIntent.LogExpense>().firstOrNull() ?: return
        if (!categoryPicked || e.received) return
        val all = c.money.categories.first()
        val picked = all.firstOrNull { it.name.equals(e.category, true) } ?: return
        c.money.teach(e.note, picked.id, all.firstOrNull { it.name.equals(categoryBeforePick, true) }?.id)
    }

    /** Picks one of the "was it one of these?" guesses as the draft. */
    fun chooseGuess(intent: VoiceIntent) = _state.update { it.copy(stage = Stage.Result, drafts = listOf(intent)) }

    /** Saves every draft and closes; the chip with Undo appears over Today. */
    fun save() {
        val s = _state.value
        if (s.busy || s.drafts.isEmpty()) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = kit.executor.execute(s.transcript, s.drafts)
            teachPickedCategory(s.drafts)
            kit.newTodos.add(r.createdTodos)
            kit.feedback.show(r.summary, r.commandId)
            _state.update { it.copy(busy = false, done = true) }
            kit.speaker.speak(r.summary)
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    private fun applyDebug(d: VoiceDebug.Request) {
        when (d.state) {
            "listening" -> _state.update { it.copy(stage = Stage.Listening, transcript = d.transcript, seconds = d.seconds) }
            "partial" -> _state.update {
                it.copy(stage = Stage.Partial, transcript = d.transcript, guesses = c.ai.guessIntents(d.transcript))
            }
            "micoff" -> {
                _state.update { it.copy(stage = Stage.Typing, micOff = true, typed = d.transcript, heardByVoice = false) }
                startTypedWait()
            }
            "ready" -> _state.update { it.copy(stage = Stage.Listening, ready = true, transcript = d.transcript, seconds = d.seconds, level = 0.5f) }
            else -> viewModelScope.launch { understand(d.transcript, heardByVoice = true) }
        }
    }
}
