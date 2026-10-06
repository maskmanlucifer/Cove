package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity

/** Which layer produced a [Suggestion]; [label] is the short plain-language reason shown in the UI. */
enum class Reason(val label: String) {
    Learned("learned"),
    Keyword("your keyword"),
    Name("its name"),
    BuiltIn("built-in"),
    Ai("AI"),
}

/**
 * A category guess. [categoryId] is null when nothing matched (the caller then uses "Other" if it exists).
 *
 * @property confidence 0..1, see [ExpenseCategorizer] for the scale
 */
data class Suggestion(val categoryId: String?, val confidence: Float, val reason: Reason?) {
    companion object {
        val NONE = Suggestion(null, 0f, null)
    }
}

/**
 * Cheap, instant, offline expense categoriser. Layers, strongest first:
 * 1. learned memory (what the user filed before),
 * 2. the user's own words for a category, then the category's own name,
 * 3. built-in rules (common words and Indian merchants) mapped onto the user's categories by name.
 *
 * Confidence scale: learned 0.75 (one pick) up to 0.95 (five or more); your keyword 0.85, 0.90 when a
 * multi-word phrase matched; category name 0.70; built-in 0.55 up to 0.65 for several words; none 0.
 * Ties are broken by the user's category order, then name, then id, so results are deterministic.
 */
object ExpenseCategorizer {
    /** Suggests a spending category for [note] among [categories], using learned [memory] keyed by token. */
    fun suggest(note: String, categories: List<ExpenseCategoryEntity>, memory: Map<String, CategoryMemoryEntity> = emptyMap()): Suggestion {
        val tokens = CategoryTokens.tokens(note)
        val usable = categories.filter { it.deletedAt == null && it.kind == "spending" }
        if (tokens.isEmpty() || usable.isEmpty()) return Suggestion.NONE
        return learned(tokens, usable, memory) ?: keyword(tokens, usable) ?: byName(tokens, usable) ?: builtIn(tokens, usable) ?: Suggestion.NONE
    }

    /** The "Other" category, or null when the user has none. */
    fun fallback(categories: List<ExpenseCategoryEntity>): ExpenseCategoryEntity? =
        categories.firstOrNull { it.deletedAt == null && it.kind == "spending" && isOther(it) }

    private fun learned(tokens: List<String>, cats: List<ExpenseCategoryEntity>, memory: Map<String, CategoryMemoryEntity>): Suggestion? {
        if (memory.isEmpty()) return null
        val ids = cats.map { it.id }.toSet()
        val score = HashMap<String, Int>()
        for (t in tokens) {
            val m = memory[t] ?: memory[CategoryTokens.stem(t)] ?: continue
            if (m.deletedAt == null && m.count > 0 && m.categoryId in ids) score.merge(m.categoryId, m.count, Int::plus)
        }
        val best = pick(cats, score) ?: return null
        return Suggestion(best.first, 0.70f + 0.05f * minOf(best.second, 5), Reason.Learned)
    }

    private fun keyword(tokens: List<String>, cats: List<ExpenseCategoryEntity>): Suggestion? {
        val score = HashMap<String, Int>()
        var phrase = false
        for (c in cats.filterNot(::isOther)) {
            for (k in CategoryTokens.keywordList(c.keywords)) {
                val kt = CategoryTokens.tokens(k)
                if (kt.isNotEmpty() && kt.all { w -> tokens.any { CategoryTokens.same(it, w) } }) {
                    score.merge(c.id, kt.size, Int::plus)
                    if (kt.size > 1) phrase = true
                }
            }
        }
        val best = pick(cats, score) ?: return null
        return Suggestion(best.first, if (phrase) 0.90f else 0.85f, Reason.Keyword)
    }

    private fun byName(tokens: List<String>, cats: List<ExpenseCategoryEntity>): Suggestion? {
        val score = cats.filterNot(::isOther).associate { c ->
            c.id to CategoryTokens.tokens(c.name).count { w -> tokens.any { CategoryTokens.same(it, w) } }
        }
        return pick(cats, score)?.let { Suggestion(it.first, 0.70f, Reason.Name) }
    }

    private fun builtIn(tokens: List<String>, cats: List<ExpenseCategoryEntity>): Suggestion? {
        val byKey = cats.filterNot(::isOther).groupBy { CategoryTokens.nameKey(it.name) }
        val ranked = BuiltInRules.groups.mapIndexed { i, g -> Triple(i, g, groupScore(g, tokens)) }
            .filter { it.third > 0 }
            .sortedWith(compareBy<Triple<Int, RuleGroup, Int>>({ -it.third }, { it.first }))
        for ((_, group, score) in ranked) {
            val hit = group.candidates.firstNotNullOfOrNull { byKey[CategoryTokens.nameKey(it)]?.first() } ?: continue
            return Suggestion(hit.id, if (score >= 3) 0.65f else if (score == 2) 0.60f else 0.55f, Reason.BuiltIn)
        }
        return null
    }

    private fun groupScore(g: RuleGroup, tokens: List<String>): Int =
        tokens.sumOf { t ->
            val hit = if (t in g.words) t else g.words.firstOrNull { CategoryTokens.same(it, t) }
            when {
                hit == null -> 0
                hit in g.strong -> 2
                else -> 1
            }
        }

    private fun isOther(c: ExpenseCategoryEntity) = c.name.trim().equals("Other", true)

    /** Highest score wins; ties go to the earlier category in [cats], then name, then id. */
    private fun pick(cats: List<ExpenseCategoryEntity>, score: Map<String, Int>): Pair<String, Int>? {
        val order = cats.withIndex().associate { it.value.id to it.index }
        val names = cats.associate { it.id to it.name.lowercase() }
        return score.entries.filter { it.value > 0 }
            .minWithOrNull(
                compareBy<Map.Entry<String, Int>>({ -it.value }, { order[it.key] ?: Int.MAX_VALUE }, { names[it.key] ?: "" }, { it.key }),
            )?.let { it.key to it.value }
    }
}
