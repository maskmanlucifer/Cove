package app.cove.companion.feature.brief

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** One spoken part of the brief; [title] is the chip label ("Money · ₹11,580 left"), [text] what is read aloud. */
@Serializable
data class BriefSegment(val title: String, val text: String)

/** JSON form of the segments stored in `BriefEntity.segments`. */
object BriefCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(BriefSegment.serializer())

    fun encode(segments: List<BriefSegment>): String = json.encodeToString(serializer, segments)

    fun decode(text: String): List<BriefSegment> = runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}

/** A slice of a segment's text that is sent to the speech engine as one utterance. */
data class SpeechChunk(val start: Int, val text: String) {
    val end: Int get() = start + text.length
}

/** Splitting of text into sentence-sized utterances and the time/progress arithmetic of the player. */
object BriefTiming {
    /** Speaking rate of the default voice at 1x, used for estimates before the engine reports progress. */
    const val WORDS_PER_SECOND = 2.5

    private const val MAX_CHUNK = 220

    /** Splits [text] into sentences (hard-wrapped at [MAX_CHUNK] characters) keeping their offsets. */
    fun chunks(text: String): List<SpeechChunk> {
        val out = mutableListOf<SpeechChunk>()
        var start = 0
        Regex("[.!?]+[\"”’)]*\\s+").findAll(text).forEach { m ->
            addWrapped(out, text, start, m.range.last + 1)
            start = m.range.last + 1
        }
        if (start < text.length) addWrapped(out, text, start, text.length)
        return out
    }

    private fun addWrapped(out: MutableList<SpeechChunk>, text: String, from: Int, to: Int) {
        var s = from
        while (to - s > MAX_CHUNK) {
            val cut = text.lastIndexOf(' ', s + MAX_CHUNK).takeIf { it > s } ?: (s + MAX_CHUNK)
            out += SpeechChunk(s, text.substring(s, cut + 1))
            s = cut + 1
        }
        if (s < to) out += SpeechChunk(s, text.substring(s, to))
    }

    /** Estimated reading time of [text] at 1x, at least two seconds. */
    fun segmentSeconds(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return max(2, ceil(words / WORDS_PER_SECOND).toInt())
    }

    fun totalSeconds(segments: List<BriefSegment>): Int = segments.sumOf { segmentSeconds(it.text) }

    /** "2 min" for the header; never below one minute. */
    fun minutesLabel(totalSeconds: Int): String = "${max(1, (totalSeconds / 60.0).roundToInt())} min"

    /** "0:51" style clock. */
    fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    /** Seconds (at 1x) already read when [fraction] of segment [index] is done. */
    fun elapsedSeconds(segments: List<BriefSegment>, index: Int, fraction: Float): Float {
        val before = segments.take(index).sumOf { segmentSeconds(it.text) }
        val current = segments.getOrNull(index)?.let { segmentSeconds(it.text) } ?: 0
        return before + current * fraction.coerceIn(0f, 1f)
    }

    /** Share of segment text already spoken when the engine is [charInChunk] characters into [chunk]. */
    fun segmentFraction(text: String, chunk: SpeechChunk?, charInChunk: Int): Float =
        if (chunk == null || text.isEmpty()) 0f else ((chunk.start + charInChunk).toFloat() / text.length).coerceIn(0f, 1f)
}
