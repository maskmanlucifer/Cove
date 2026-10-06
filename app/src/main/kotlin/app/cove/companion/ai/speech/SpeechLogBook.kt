package app.cove.companion.ai.speech

/** The last few content-free speech log lines (engine, outcome, error code, timings), kept in memory for the Voice check sheet. */
object SpeechLogBook {
    private val lines = ArrayDeque<String>()

    /** Remembers [line], dropping the oldest beyond 30. */
    @Synchronized fun add(line: String) {
        lines.addLast(line)
        while (lines.size > 30) lines.removeFirst()
    }

    /** Oldest first. */
    @Synchronized fun recent(): List<String> = lines.toList()
}
