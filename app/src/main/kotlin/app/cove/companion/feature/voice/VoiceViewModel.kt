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
import app.cove.companion.ai.model.MemoryNotes
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
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
    private var finishRequested = false
    private var segmentActive = false
    private val heard = StringBuilder()

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
                    dictate(picked)
                } finally {
                    ticker.cancel()
                }
            } finally {
                gate.end(token)
            }
        }
    }

    /**
     * Stop button and Done: end the dictation and understand everything heard so far. While an engine is capturing it is asked
     * for its final transcript; before it is ready, or between two engine runs, the words already on screen are used.
     */
    fun finish() {
        val s = _state.value
        if (s.stage != Stage.Listening || s.busy) return
        finishRequested = true
        val engine = session
        if (segmentActive && s.ready && engine != null) {
            viewModelScope.launch { engine.stop() }
        } else {
            job?.cancel()
            viewModelScope.launch {
                val text = _state.value.transcript.trim()
                if (text.isEmpty()) trouble(SpeechFailure.NoMatch, 0) else understand(text, heardByVoice = true)
            }
        }
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

    /**
     * One engine run: its final text (or last partial) and why it ended, if it failed.
     * [quiet] is true when the run was cut short because nobody resumed speaking in time (see [EndpointPolicy]).
     */
    private class Segment(val text: String, val failure: SpeechEvent.Failure?, val quiet: Boolean = false)

    /**
     * Keeps listening across the recognizer's own pauses: Android engines end a run after a short silence, which used to
     * cut a sentence in half. Each run's words are appended; the loop ends on Done, a pause after speech longer than
     * [EndpointPolicy] allows (so Done is optional), a hard failure or [MAX_DICTATION_MS]. Then everything heard is understood at once.
     */
    private suspend fun dictate(first: SpeechSession) {
        heard.clear()
        finishRequested = false
        val began = android.os.SystemClock.elapsedRealtime()
        var engine: SpeechSession = first
        var silentRuns = 0
        var busyRetries = 0
        var lastSpeechAt = 0L
        while (true) {
            val resumeBy = if (heard.isEmpty()) null else lastSpeechAt + EndpointPolicy.graceMs(heard.toString())
            val seg = listenSegment(engine, resumeBy)
            val said = seg.text.trim()
            if (said.isNotEmpty()) {
                if (heard.isNotEmpty()) heard.append(' ')
                heard.append(said)
                silentRuns = 0
                lastSpeechAt = android.os.SystemClock.elapsedRealtime()
            }
            val failure = seg.failure
            val elapsed = android.os.SystemClock.elapsedRealtime() - began
            when {
                finishRequested -> break
                seg.quiet && said.isEmpty() && heard.isNotEmpty() -> break
                failure?.reason == SpeechFailure.PermissionDenied -> return trouble(failure.reason, failure.code)
                failure?.reason == SpeechFailure.NoMatch || (failure == null && said.isEmpty()) -> {
                    silentRuns++
                    if (heard.isEmpty() && (silentRuns >= MAX_SILENT_RUNS || elapsed > QUIET_GIVE_UP_MS)) return trouble(SpeechFailure.NoMatch, 0)
                    if (heard.isNotEmpty() && silentRuns >= MAX_SILENT_RUNS) break
                }
                failure?.reason == SpeechFailure.Busy && busyRetries++ < MAX_BUSY_RETRIES -> delay(400)
                failure != null -> {
                    if (heard.isEmpty()) return trouble(failure.reason, failure.code)
                    break
                }
            }
            if (elapsed > MAX_DICTATION_MS) break
            delay(RESTART_GAP_MS)
            engine = c.ai.openSpeech().also { session = it }
        }
        val text = heard.toString().trim().ifEmpty { _state.value.transcript.trim() }
        if (text.isEmpty()) trouble(SpeechFailure.NoMatch, 0) else understand(text, heardByVoice = true)
    }

    /**
     * Collects one engine run. With [resumeBy] set (words were already heard), the run is stopped as [quiet] if nobody has
     * started speaking by then, but never sooner than [EndpointPolicy.MIN_LISTEN_MS] after the engine was ready.
     */
    private suspend fun listenSegment(source: SpeechSession, resumeBy: Long? = null): Segment = coroutineScope {
        var final: String? = null
        var lastPartial = ""
        var failure: SpeechEvent.Failure? = null
        var spoke = false
        var quiet = false
        var readyAt = 0L
        val watchdog = launch {
            if (resumeBy == null) return@launch
            while (readyAt == 0L) delay(50)
            val deadline = maxOf(resumeBy, readyAt + EndpointPolicy.MIN_LISTEN_MS)
            delay((deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
            if (!spoke) { quiet = true; source.stop() }
        }
        segmentActive = true
        try {
            source.events.collect { e ->
                when (e) {
                    is SpeechEvent.Ready -> {
                        readyAt = android.os.SystemClock.elapsedRealtime()
                        _state.update { it.copy(ready = true, onDevice = e.source.location.isLocal) }
                    }
                    SpeechEvent.Began -> spoke = true
                    is SpeechEvent.Partial -> {
                        spoke = true
                        lastPartial = e.text
                        _state.update { it.copy(transcript = joinHeard(e.text)) }
                    }
                    is SpeechEvent.Level -> _state.update { it.copy(level = e.value) }
                    is SpeechEvent.Final -> final = e.text
                    is SpeechEvent.Failure -> failure = e
                }
            }
        } finally {
            segmentActive = false
            watchdog.cancel()
        }
        val text = final ?: lastPartial
        if (text.isNotBlank()) _state.update { it.copy(transcript = joinHeard(text), level = 0f) }
        Segment(text, failure, quiet)
    }

    /** Words from earlier runs plus [tail], for the live transcript. */
    private fun joinHeard(tail: String) = if (heard.isEmpty()) tail else if (tail.isBlank()) heard.toString() else "$heard $tail"

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
                if (only == VoiceIntent.UndoLast || only == VoiceIntent.QueryNext || only == VoiceIntent.QueryNextWorkout || only is VoiceIntent.Recall) {
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
            is AiResult.Failed -> {
                // No rule fits. First read dates, amounts and numbers out of it (resolved properly, unlike a bare "5 am" guess),
                // then offer the half-heard guesses, then keep it as a plain note.
                val details = c.ai.readDetails(text)
                val guesses = if (details.isEmpty()) c.ai.guessIntents(text) else emptyList()
                if (details.isNotEmpty()) {
                    _state.update { it.copy(busy = false, stage = Stage.Result, drafts = details, categories = categories, expenseCategories = expenseCats) }
                } else if (guesses.isEmpty() && MemoryNotes.looksLikeNote(text)) {
                    _state.update { it.copy(busy = false, stage = Stage.Result, drafts = listOf(MemoryNotes.note(text)), categories = categories, expenseCategories = expenseCats) }
                } else {
                    _state.update { it.copy(busy = false, stage = Stage.Partial, guesses = guesses, transcript = text) }
                }
            }
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

    private companion object {
        /** Pause between two engine runs while dictating. */
        const val RESTART_GAP_MS = 150L

        /** Longest a single dictation may last. */
        const val MAX_DICTATION_MS = 3 * 60_000L

        /** Silent engine runs in a row before giving up (about 7 s each with nothing said). */
        const val MAX_SILENT_RUNS = 4

        /** Nothing said at all for this long: show the "didn't hear anything" help. */
        const val QUIET_GIVE_UP_MS = 25_000L
        const val MAX_BUSY_RETRIES = 3
    }
}
