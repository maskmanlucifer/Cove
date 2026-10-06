package app.cove.companion.feature.voice.intent

/** Turns spoken English numbers ("six thirty", "three hundred forty") into digits inside a transcript. */
object SpokenNumbers {
    private val units = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ).withIndex().associate { it.value to it.index }
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        .withIndex().associate { it.value to (it.index + 2) * 10 }

    /** "one" after these reads as a pronoun ("no one", "every one"), not a number. */
    private val pronounLead = setOf("no", "any", "every", "each", "the", "which", "this", "that", "another", "which", "some")

    private enum class Kind { None, Unit, Teen, Tens, Hundred, Thousand }

    private val word = Regex("\\p{L}+")

    /** Replaces runs of number words with digits; "six thirty" becomes "6 30" and "six oh five" "6 05". */
    fun digitize(text: String): String {
        val words = word.findAll(text).toList()
        if (words.none { isNumber(it.value.lowercase()) }) return text
        val out = StringBuilder()
        var pos = 0
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val lower = w.value.lowercase()
            if (!isNumber(lower) || isPronounOne(words, i)) { i++; continue }
            var j = i
            while (j + 1 < words.size && joins(text, words, j)) j++
            out.append(text, pos, w.range.first)
            out.append(convert(words.subList(i, j + 1).map { it.value.lowercase() }))
            pos = words[j].range.last + 1
            i = j + 1
        }
        out.append(text, pos, text.length)
        return out.toString()
    }

    private fun isNumber(w: String) = w in units || w in tens || w == "hundred" || w == "thousand"

    private fun isPronounOne(words: List<MatchResult>, i: Int): Boolean {
        val w = words[i].value.lowercase()
        if (w != "one") return false
        val before = words.getOrNull(i - 1)?.value?.lowercase()
        val after = words.getOrNull(i + 1)?.value?.lowercase()
        return before in pronounLead || after == "of" || after == "another"
    }

    /** True when the word after [j] continues the number run that includes [j]. */
    private fun joins(text: String, words: List<MatchResult>, j: Int): Boolean {
        val gap = text.substring(words[j].range.last + 1, words[j + 1].range.first)
        if (gap.isNotBlank() && gap.trim() != "-") return false
        val cur = words[j].value.lowercase()
        val next = words[j + 1].value.lowercase()
        val after = words.getOrNull(j + 2)?.value?.lowercase()
        return when {
            next == "oh" -> after != null && units[after] in 1..9
            next == "and" -> (cur == "hundred" || cur == "thousand") && after != null && isNumber(after)
            isNumber(next) -> !isPronounOne(words, j + 1)
            else -> false
        }
    }

    private fun convert(run: List<String>): String {
        val out = mutableListOf<String>()
        var total = 0
        var cur = 0
        var kind = Kind.None
        var pending = false
        fun flush() {
            if (pending) out += (total + cur).toString()
            total = 0; cur = 0; kind = Kind.None; pending = false
        }
        var k = 0
        while (k < run.size) {
            val w = run[k]
            val v = units[w] ?: tens[w]
            when {
                w == "and" -> Unit
                w == "oh" -> {
                    flush()
                    out += "0" + units[run[k + 1]]
                    k++
                }
                w == "zero" -> { flush(); out += "0" }
                w == "hundred" -> {
                    if (pending && cur in 1..99) cur *= 100 else { flush(); cur = 100 }
                    kind = Kind.Hundred; pending = true
                }
                w == "thousand" -> {
                    if (pending) total += maxOf(cur, 1) * 1000 else total += 1000
                    cur = 0; kind = Kind.Thousand; pending = true
                }
                v != null -> {
                    val big = kind == Kind.Hundred || kind == Kind.Thousand
                    val attachUnit = v in 1..9 && (big || kind == Kind.Tens)
                    if (!(pending && (attachUnit || (v >= 10 && big)))) flush()
                    cur += v
                    kind = if (v >= 20) Kind.Tens else if (v >= 10) Kind.Teen else Kind.Unit
                    pending = true
                }
            }
            k++
        }
        flush()
        return out.joinToString(" ")
    }
}
