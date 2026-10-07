package app.cove.companion.data.memory

import app.cove.companion.data.local.entity.MemoryEntity

/** Turns free text into the few words worth searching on. Pure, so it is unit tested. */
object MemoryText {
    private val stop = setOf(
        "a", "an", "the", "my", "our", "your", "i", "me", "you", "is", "are", "was", "were", "am", "be", "to", "of", "in", "on", "at",
        "it", "that", "this", "and", "or", "for", "with", "did", "do", "does", "where", "what", "when", "who", "how", "have", "has", "had",
        "put", "left", "kept", "keep", "just", "remember", "note", "said", "say", "tell", "about", "there", "here", "can", "could", "would", "will",
    )

    /** Light stemming so "parked", "parking" and "park" meet, and "keys" meets "key". */
    fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /** Normalised search words of [text], in order and without repeats. */
    fun keywords(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 && it !in stop }.map(::stem).distinct()
}

/** How long a kind of memory stays useful. */
object MemoryPolicy {
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private val vehicles = setOf("car", "bike", "scooter", "vehicle", "bus", "cab")

    /** Parked vehicles are only worth answering for a day; everything else is kept until forgotten. */
    fun keepFor(subject: String): Long? = if (subject.lowercase() in vehicles) DAY_MS else null
}

/** Picks the memory that answers a question. */
object MemoryRanker {
    /** Match strength of [m] for the question words [q]: a subject hit counts more than a hit anywhere in the text. */
    fun score(q: List<String>, m: MemoryEntity): Int {
        val subject = MemoryText.keywords(m.subject).toSet()
        val words = m.keywords.split(' ').toSet()
        return q.sumOf { if (it in subject) 3 else if (it in words) 1 else 0 }
    }

    /** The best match for [question] among [memories] (newest first), or null when nothing matches. */
    fun pick(question: String, memories: List<MemoryEntity>): MemoryEntity? {
        val q = MemoryText.keywords(question)
        if (q.isEmpty()) return null
        return memories.map { it to score(q, it) }.filter { it.second > 0 }
            .maxWithOrNull(compareBy({ it.second }, { it.first.createdAt }))?.first
    }
}

/** The sentence Cove says back. */
object MemoryAnswer {
    const val NOTHING = "I don't have anything about that yet. Tell me once and I'll remember it."

    /** "an hour ago", "3 days ago" for an age in milliseconds. */
    fun ago(ageMs: Long): String {
        val minutes = (ageMs / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> if (minutes == 1L) "a minute ago" else "$minutes minutes ago"
            minutes < 24 * 60 -> (minutes / 60).let { if (it == 1L) "an hour ago" else "$it hours ago" }
            else -> (minutes / (24 * 60)).let { if (it == 1L) "yesterday" else "$it days ago" }
        }
    }

    /** "Car: on level 3, pillar B. You told me 2 hours ago." */
    fun say(m: MemoryEntity, now: Long): String {
        val when_ = ago(now - m.createdAt)
        return if (m.detail.isNotBlank()) "${m.subject.replaceFirstChar { it.uppercase() }}: ${m.detail}. You told me $when_."
        else "You told me \u201c${m.text}\u201d $when_."
    }
}
