package app.cove.companion.feature.brief

import app.cove.companion.data.local.entity.BriefEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the brief screen shows: position is segment [index], sentence [chunk], [charInChunk] characters in. */
data class PlayerState(
    val segments: List<BriefSegment> = emptyList(),
    val index: Int = 0,
    val chunk: Int = 0,
    val charInChunk: Int = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val finished: Boolean = false,
    /** Debug only: fixed (elapsed, total) seconds shown instead of the estimate. */
    val fixed: Pair<Int, Int>? = null,
    /** Set when this phone cannot read aloud; the screen explains and offers the text view. */
    val problem: TtsProblem? = null,
) {
    val current: BriefSegment? get() = segments.getOrNull(index)
    val chunks: List<SpeechChunk> get() = current?.let { BriefTiming.chunks(it.text) }.orEmpty()

    /** Share of the current segment already spoken, 0..1. */
    val segmentFraction: Float get() = BriefTiming.segmentFraction(current?.text.orEmpty(), chunks.getOrNull(chunk), charInChunk)

    /** Seconds read so far, at the current speed. */
    val elapsedSeconds: Int get() = fixed?.first ?: (BriefTiming.elapsedSeconds(segments, index, segmentFraction) / speed).toInt()
    val totalSeconds: Int get() = fixed?.second ?: (BriefTiming.totalSeconds(segments) / speed).toInt()
    val progress: Float get() = if (fixed != null) fixed.first.toFloat() / fixed.second else if (segments.isEmpty()) 0f else BriefTiming.elapsedSeconds(segments, index, segmentFraction) / BriefTiming.totalSeconds(segments)
}

/**
 * Reads the brief aloud with text-to-speech while the app is alive; no foreground service is used.
 * App-scoped (see `AppContainer.briefPlayer`), so the ring screen can start it from anywhere.
 */
class BriefPlayer(private val speech: () -> SpeechOut, private val today: suspend () -> BriefEntity) : SpeechListener {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var out: SpeechOut? = null
    private var run = 0

    /** Loads [brief]'s segments; keeps the position when the same script is already loaded. */
    fun load(brief: BriefEntity) {
        val segments = BriefCodec.decode(brief.segments)
        if (segments != _state.value.segments) {
            halt()
            _state.value = PlayerState(segments = segments, speed = _state.value.speed)
        }
    }

    /** Loads today's brief (cached, or generated when missing) and starts reading it. */
    suspend fun playToday() {
        load(today())
        play()
    }

    /** Shows a fixed position without speaking; used by debug screenshots. */
    fun freeze(index: Int, elapsed: Int, total: Int) {
        _state.update { it.copy(index = index, chunk = 0, charInChunk = 0, playing = true, fixed = elapsed to total) }
    }

    fun play() {
        val s = _state.value
        if (s.segments.isEmpty()) return
        if (s.problem != null) {
            // Try again with a fresh engine: the user may have installed or enabled one since.
            out?.shutdown()
            out = null
        }
        val from = if (s.finished) PlayerState(s.segments, speed = s.speed) else s
        _state.value = from.copy(playing = true, finished = false, problem = null)
        speakFrom(from.index, from.chunk)
    }

    fun pause() {
        halt()
        _state.update { it.copy(playing = false) }
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun jumpTo(index: Int) {
        val s = _state.value
        if (index !in s.segments.indices) return
        _state.value = s.copy(index = index, chunk = 0, charInChunk = 0, finished = false)
        if (s.playing) speakFrom(index, 0)
    }

    fun next() = jumpTo((_state.value.index + 1).coerceAtMost(_state.value.segments.lastIndex))

    /** Restarts the current segment when it is more than a few seconds in, otherwise goes to the previous one. */
    fun previous() {
        val s = _state.value
        jumpTo(if (s.segmentFraction > 0.15f || s.index == 0) s.index else s.index - 1)
    }

    /** Cycles 1x, 1.25x, 1.5x. */
    fun cycleSpeed() {
        val s = _state.value
        val speed = when (s.speed) { 1f -> 1.25f; 1.25f -> 1.5f; else -> 1f }
        _state.value = s.copy(speed = speed)
        if (s.playing) speakFrom(s.index, s.chunk)
    }

    /** Pauses and frees the speech engine; call when the screen is left. */
    fun stop() {
        pause()
        out?.shutdown()
        out = null
    }

    private fun halt() {
        run++
        out?.stop()
    }

    private fun speakFrom(index: Int, chunk: Int) {
        halt()
        val engine = out ?: speech().also { it.listener = this; out = it }
        val s = _state.value
        s.segments.drop(index).forEachIndexed { i, seg ->
            val seq = index + i
            BriefTiming.chunks(seg.text).forEachIndexed { k, c ->
                if (i > 0 || k >= chunk) engine.speak("$run:$seq:$k", c.text, s.speed)
            }
        }
    }

    private fun parse(id: String): Triple<Int, Int, Int>? {
        val p = id.split(":").mapNotNull { it.toIntOrNull() }
        return if (p.size == 3 && p[0] == run) Triple(p[0], p[1], p[2]) else null
    }

    override fun onProblem(problem: TtsProblem) {
        run++
        _state.update { it.copy(playing = false, problem = problem) }
    }

    override fun onStart(id: String) {
        val (_, seg, chunk) = parse(id) ?: return
        _state.update { it.copy(index = seg, chunk = chunk, charInChunk = 0) }
    }

    override fun onRange(id: String, start: Int) {
        parse(id) ?: return
        _state.update { it.copy(charInChunk = start) }
    }

    override fun onDone(id: String) {
        val (_, seg, chunk) = parse(id) ?: return
        val s = _state.value
        val lastSeg = s.segments.lastIndex
        if (seg == lastSeg && chunk == BriefTiming.chunks(s.segments[seg].text).lastIndex) {
            _state.update { it.copy(playing = false, finished = true, charInChunk = s.chunks.lastOrNull()?.text?.length ?: 0) }
        }
    }
}
