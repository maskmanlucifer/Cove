package app.cove.companion.data.categorize

import java.text.Normalizer

/** Splits expense notes into the merchant-like words the categorizer and its memory work with. */
object CategoryTokens {
    private val marks = Regex("\\p{M}+")
    private val notLetters = Regex("[^a-z]+")

    private val stopWords = setOf(
        "a", "an", "the", "to", "for", "at", "on", "in", "of", "and", "or", "with", "from", "my", "me", "our", "by", "via",
        "i", "is", "was", "paid", "pay", "spent", "spend", "bought", "buy", "got", "get", "log", "add", "expense", "rs",
        "inr", "rupee", "rupees", "today", "yesterday", "tonight", "this", "that", "some", "just", "upi", "cash", "card",
        "out", "new", "again", "little", "bit",
    )

    /** Words of the generic fallback notes ("Payment"); they never teach or match, so they cannot pollute every unknown payee. */
    val genericWords = setOf("payment", "payments", "wallet")

    /** Distinct tokens of [text] in order: lowercase letters only, accents folded, digits/currency/stop words dropped. */
    fun tokens(text: String): List<String> =
        fold(text).split(notLetters).filter { it.length >= 2 && it !in stopWords }.distinct()

    /** Lowercase with accents removed ("Café" to "cafe") and apostrophes dropped ("Domino's" to "dominos"). */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase().replace("'", "").replace("’", "")

    /** Crude plural stem: "gifts" to "gift", "groceries" to "grocery", "bills" to "bill"; short words are kept. */
    fun stem(token: String): String = when {
        token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    /** True when [a] and [b] are the same word up to a plural. */
    fun same(a: String, b: String): Boolean = a == b || stem(a) == stem(b)

    /** Key for comparing category names: letters only, each word stemmed ("Eating out" and "eating Out" match). */
    fun nameKey(name: String): String =
        fold(name).split(notLetters).filter { it.isNotEmpty() }.joinToString(" ") { stem(it) }

    /** The user's [keywords] ("gift, birthday") as trimmed non-empty phrases. */
    fun keywordList(keywords: String): List<String> =
        keywords.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Canonical comma-separated form of what the user typed: trimmed, no blanks, no duplicates (case-insensitive). */
    fun cleanKeywords(raw: String): String =
        keywordList(raw).distinctBy { it.lowercase() }.joinToString(", ")
}
