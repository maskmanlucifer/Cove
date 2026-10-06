package app.cove.companion.data.insights

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** One search result; higher [score] is a better match. */
data class JournalHit(val entryId: String, val score: Double)

/** Pure helpers behind [JournalSearch]: FTS query building, vector maths and rank fusion. */
object SearchMath {
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    /** Lowercase letter/digit words in [input]. */
    fun tokens(input: String): List<String> = input.lowercase().split(splitter).filter { it.isNotEmpty() }

    /**
     * FTS4 MATCH expression: every word is a quoted prefix term, all required (implicit AND).
     * Returns null when the input has no searchable words. At most [maxTerms] words are used.
     */
    fun ftsQuery(input: String, maxTerms: Int = 8): String? =
        tokens(input).take(maxTerms).takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it*\"" }

    /** Cosine similarity of [a] and [b]; 0 for different sizes or zero vectors. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na == 0.0 || nb == 0.0) 0f else (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Packs floats as little-endian bytes for `SearchIndexEntity.embedding`. */
    fun toBytes(v: FloatArray): ByteArray =
        ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putFloat(it) } }.array()

    fun fromBytes(b: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buf.remaining()).also { buf.get(it) }
    }

    /** How many times the words of [query] occur in [text]; used to order keyword matches. */
    fun keywordScore(text: String, query: List<String>): Int {
        val words = tokens(text)
        return query.sumOf { q -> words.count { it.startsWith(q) } }
    }

    /**
     * Merges a keyword ranking and a semantic ranking with reciprocal rank fusion, so an entry that both lists
     * like beats one only a single list likes. [semantic] scores below [minSemantic] are ignored.
     */
    fun rank(
        keyword: List<Pair<String, Int>>,
        semantic: List<Pair<String, Float>>,
        limit: Int = 20,
        minSemantic: Float = 0.3f,
        k: Int = 60,
    ): List<JournalHit> {
        val scores = HashMap<String, Double>()
        keyword.sortedByDescending { it.second }.forEachIndexed { i, (id, _) ->
            scores.merge(id, 1.0 / (k + i + 1), Double::plus)
        }
        semantic.filter { it.second >= minSemantic }.sortedByDescending { it.second }.forEachIndexed { i, (id, _) ->
            scores.merge(id, 1.0 / (k + i + 1), Double::plus)
        }
        return scores.map { JournalHit(it.key, it.value) }.sortedWith(compareByDescending<JournalHit> { it.score }.thenBy { it.entryId }).take(limit)
    }
}
