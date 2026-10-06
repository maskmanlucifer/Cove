package app.cove.companion.data.wipe

import java.io.File

/** One line of the confirm sheet: how many things of a kind are stored on this phone. [label] is `singular|plural`. */
data class PreviewLine(val label: String, val count: Int)

/** What "Clear all data" is about to delete, counted from the database and the files. */
data class DataPreview(val lines: List<PreviewLine>, val mediaFiles: Int, val mediaBytes: Long, val databaseBytes: Long) {
    /** Plain sentences, one per non-empty area, e.g. "12 to-dos" or "9 photos, voice notes and thumbnails (14.2 MB)". */
    fun sentences(): List<String> {
        val out = lines.filter { it.count > 0 }.map { "${"%,d".format(it.count)} ${noun(it)}" }.toMutableList()
        if (mediaFiles > 0) out += "${"%,d".format(mediaFiles)} photos, voice notes and thumbnails (${formatSize(mediaBytes)})"
        if (out.isEmpty()) out += "No entries yet."
        return out
    }

    /** Size of the encrypted database, as text. */
    fun databaseSize(): String = formatSize(databaseBytes)

    private fun noun(line: PreviewLine): String =
        if (line.count == 1) line.label.substringBefore('|') else line.label.substringAfter('|', line.label)

    companion object {
        /** Areas shown, as `table to "singular|plural"`. */
        val AREAS = listOf(
            "journal_entries" to "journal entry|journal entries",
            "todos" to "to-do|to-dos",
            "events" to "event|events",
            "alarms" to "alarm|alarms",
            "habits" to "habit|habits",
            "expenses" to "money entry|money entries",
            "workout_sessions" to "workout|workouts",
            "body_weights" to "weight entry|weight entries",
        )

        /** Counts, files and sizes. [count] returns the live rows in a table (0 when it cannot be read). */
        fun build(count: (String) -> Int, journalDir: File, databaseFiles: List<File>): DataPreview {
            val media = journalDir.walkTopDown().filter { it.isFile }.toList()
            return DataPreview(
                AREAS.map { (table, label) -> PreviewLine(label, count(table)) },
                media.size, media.sumOf { it.length() }, databaseFiles.sumOf { it.length() },
            )
        }

        /** 1536 -> "1.5 KB". */
        fun formatSize(bytes: Long): String = when {
            bytes < 1_000 -> "$bytes B"
            bytes < 1_000_000 -> "%.1f KB".format(bytes / 1_000.0)
            bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            else -> "%.2f GB".format(bytes / 1_000_000_000.0)
        }
    }

    private fun formatSize(bytes: Long) = Companion.formatSize(bytes)
}
