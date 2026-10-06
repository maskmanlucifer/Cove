package app.cove.companion.resilience

import java.io.File

/**
 * A small, content-free record of one failure: when, where, and the exception class with its top stack frames.
 * Exception messages are never stored because they can quote user content, SQL or keys.
 *
 * @property fatal true when the process died (an uncaught exception); only fatal notes count towards crash-loop detection.
 */
data class CrashNote(
    val timeMs: Long,
    val thread: String,
    val exception: String,
    val frames: List<String>,
    val version: String,
    val where: String,
    val fatal: Boolean,
) {
    /** Plain-text form shown under "Technical details" and copied by "Copy details". */
    fun toReadableText(): String = buildString {
        appendLine("Cove $version")
        appendLine("Time: $timeMs")
        appendLine("Where: $where${if (fatal) " (crash)" else ""}")
        appendLine("Thread: $thread")
        appendLine("Error: $exception")
        frames.forEach { appendLine("  at $it") }
    }.trimEnd()

    internal fun encode(): String = listOf(
        "time=$timeMs", "thread=${thread.oneLine()}", "exception=${exception.oneLine()}", "version=${version.oneLine()}",
        "where=${where.oneLine()}", "fatal=$fatal", "frames=${frames.joinToString("|") { it.oneLine() }}",
    ).joinToString("\n")

    companion object {
        /** Builds a note for [t] without its message; [frameCount] top frames of it and of its root cause are kept. */
        fun of(t: Throwable, where: String, thread: String, version: String, timeMs: Long, fatal: Boolean, frameCount: Int = 6): CrashNote {
            var root = t
            var guard = 0
            while (root.cause != null && root.cause !== root && guard++ < 10) root = root.cause!!
            val frames = t.stackTrace.take(frameCount).map(::frameText) +
                if (root !== t) listOf("root cause ${root.javaClass.name}") + root.stackTrace.take(3).map(::frameText) else emptyList()
            return CrashNote(timeMs, thread, t.javaClass.name, frames, version, where, fatal)
        }

        internal fun decode(text: String): CrashNote? {
            val map = text.lineSequence().mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }.toMap()
            return CrashNote(
                map["time"]?.toLongOrNull() ?: return null, map["thread"].orEmpty(), map["exception"].orEmpty(),
                map["frames"].orEmpty().split("|").filter { it.isNotBlank() }, map["version"].orEmpty(),
                map["where"].orEmpty(), map["fatal"] == "true",
            )
        }

        private fun frameText(e: StackTraceElement) = "${e.className}.${e.methodName}(${e.fileName}:${e.lineNumber})"
        private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ')
    }
}

/**
 * Keeps the last few [CrashNote]s in [file] (a plain file in `no_backup`, readable without the database or any key)
 * and answers "is the app crash-looping?".
 */
class CrashStore(private val file: File, private val keep: Int = 5) {
    /** Appends [note], dropping the oldest beyond the limit. Never throws: a failing disk must not hide the real crash. */
    @Synchronized
    fun add(note: CrashNote) {
        runCatching {
            file.parentFile?.mkdirs()
            val all = (all() + note).takeLast(keep)
            file.writeText(all.joinToString(SEPARATOR) { it.encode() })
        }
    }

    /** Stored notes, oldest first; unreadable entries are skipped. */
    @Synchronized
    fun all(): List<CrashNote> = runCatching {
        if (!file.isFile) emptyList() else file.readText().split(SEPARATOR).mapNotNull { CrashNote.decode(it) }
    }.getOrDefault(emptyList())

    /** The newest note, if any. */
    fun last(): CrashNote? = all().lastOrNull()

    /** Number of fatal crashes in the [windowMs] before [now]. */
    fun recentFatal(now: Long, windowMs: Long = CrashLoop.WINDOW_MS): Int = CrashLoop.count(all(), now, windowMs)

    /** Forgets all notes, e.g. after the user chose "Try again". */
    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val SEPARATOR = "\n---\n"
    }
}

/** Crash-loop rule: [THRESHOLD] fatal crashes within [WINDOW_MS] means the next launch starts in safe mode. */
object CrashLoop {
    const val WINDOW_MS = 60_000L
    const val THRESHOLD = 2

    /** Fatal notes in the [windowMs] up to [now] (clock skew into the future counts as recent). */
    fun count(notes: List<CrashNote>, now: Long, windowMs: Long = WINDOW_MS): Int =
        notes.count { it.fatal && now - it.timeMs <= windowMs }

    /** True when [notes] show a crash loop at [now]. */
    fun isLooping(notes: List<CrashNote>, now: Long, threshold: Int = THRESHOLD, windowMs: Long = WINDOW_MS): Boolean =
        count(notes, now, windowMs) >= threshold
}
