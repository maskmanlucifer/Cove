package app.cove.companion.feature.journal.blocks

/**
 * Reads and writes the journal body format: plain text where each photo or voice note is a line holding only
 * `⟦media:<id>⟧`. The body stays one string, so sync, backup, search and the database schema are unchanged.
 *
 * Rules (tested in `JournalBodyCodecTest`):
 * - Text lines that would read as a marker (or as an escaped marker) get one invisible U+2060 prefix when stored and lose
 *   it when read, so typed text can never forge or break the structure and always comes back exactly.
 * - A line starting with `⟦` that is not a whole valid marker (truncated, bad id) is plain text.
 * - A repeated marker is dropped; a marker whose media row is missing becomes a block that renders a calm placeholder.
 * - Media rows with no marker are appended after the text, photos first, then voice notes (this is also how entries
 *   written before blocks existed are read).
 * - Trailing blank lines are trimmed; there is always a text block last, so the user has somewhere to type.
 */
object JournalBodyCodec {
    private const val OPEN = "⟦media:"
    private const val CLOSE = "⟧"
    private const val ESCAPE = '⁠'
    private const val MAX_ID = 64

    /** The marker line for [mediaId]. */
    fun marker(mediaId: String): String = "$OPEN$mediaId$CLOSE"

    /** The media id when [line] is exactly one valid marker line, else null. */
    fun markerId(line: String): String? {
        val l = line.removeSuffix("\r")
        if (!l.startsWith(OPEN) || !l.endsWith(CLOSE) || l.length <= OPEN.length + CLOSE.length) return null
        val id = l.substring(OPEN.length, l.length - CLOSE.length)
        return id.takeIf { it.length <= MAX_ID && it.all { ch -> ch.isLetterOrDigit() && ch.code < 128 || ch == '-' || ch == '_' } }
    }

    private fun needsEscape(line: String): Boolean {
        var i = 0
        while (i < line.length && line[i] == ESCAPE) i++
        return line.startsWith("⟦", i)
    }

    private fun escapeLine(line: String) = if (needsEscape(line)) ESCAPE + line else line

    private fun unescapeLine(line: String) = if (line.startsWith(ESCAPE) && needsEscape(line)) line.substring(1) else line

    private fun escapeText(text: String) = if (text.indexOf('⟦') < 0) text else text.split('\n').joinToString("\n", transform = ::escapeLine)

    /**
     * Blocks of [body]. [media] are this entry's live media rows (marker ids not in it become placeholders; rows that
     * no marker names are appended). The result is normalised.
     */
    fun parse(body: String, media: List<BlockMedia> = emptyList()): List<JournalBlock> {
        val kinds = media.associate { it.id to it.kind }
        val out = ArrayList<JournalBlock>()
        val seen = HashSet<String>()
        var run = ArrayList<String>()
        var texts = 0
        fun flush() {
            if (run.isNotEmpty()) out += JournalBlock.Text(run.joinToString("\n"), "p${texts++}")
            run = ArrayList()
        }
        for (line in body.split('\n')) {
            val id = markerId(line)
            when {
                id == null -> run += unescapeLine(line)
                seen.add(id) -> {
                    flush()
                    out += if (kinds[id] == "voice") JournalBlock.Voice(id) else JournalBlock.Photo(id)
                }
            }
        }
        flush()
        media.filter { it.kind == "photo" && seen.add(it.id) }.forEach { out += JournalBlock.Photo(it.id) }
        media.filter { it.kind != "photo" && seen.add(it.id) }.forEach { out += JournalBlock.Voice(it.id) }
        return normalise(out)
    }

    /** The stored body for [blocks]: normalised, text escaped, one block per line group. */
    fun serialize(blocks: List<JournalBlock>): String {
        val parts = normalise(blocks).map { if (it is JournalBlock.Text) escapeText(it.text) else marker(it.mediaId!!) }
        return (if (parts.last().isEmpty()) parts.dropLast(1) else parts).joinToString("\n")
    }

    /**
     * Merges neighbouring text blocks (the left id wins), trims trailing blank lines of the last text, drops repeated
     * media and makes sure the list ends with a text block. Empty text between or before media is kept on purpose.
     */
    fun normalise(blocks: List<JournalBlock>): List<JournalBlock> {
        val out = ArrayList<JournalBlock>()
        val seen = HashSet<String>()
        for (b in blocks) {
            val prev = out.lastOrNull()
            when {
                b is JournalBlock.Text && prev is JournalBlock.Text -> out[out.lastIndex] = prev.copy(text = joinText(prev.text, b.text))
                b is JournalBlock.Text -> out += b
                seen.add(b.id) -> out += b
            }
        }
        val last = out.lastOrNull()
        if (last is JournalBlock.Text) out[out.lastIndex] = last.copy(text = trimTrailingBlankLines(last.text)) else out += JournalBlock.Text("")
        return out
    }

    /** Joins two text blocks that became neighbours: a newline between them unless one is empty. */
    fun joinText(a: String, b: String): String = if (a.isEmpty()) b else if (b.isEmpty()) a else "$a\n$b"

    private fun trimTrailingBlankLines(text: String): String {
        val lines = text.split('\n')
        val keep = lines.indexOfLast { it.isNotBlank() } + 1
        return if (keep == lines.size) text else lines.take(keep).joinToString("\n")
    }

    /** True when [blocks] hold no media and only blank text. */
    fun isBlank(blocks: List<JournalBlock>): Boolean = blocks.all { it is JournalBlock.Text && it.text.isBlank() }

    /** The text of [body] without markers (their lines vanish), for search, titles, summaries and word counts. */
    fun plainText(body: String): String {
        if (body.indexOf('⟦') < 0 && body.indexOf(ESCAPE) < 0) return body
        return body.split('\n').filter { markerId(it) == null }.joinToString("\n", transform = ::unescapeLine)
    }

    /** Like [plainText] but each media block becomes a `[photo]` or `[voice note]` line, so a model sees the structure. */
    fun textWithPlaceholders(body: String, media: List<BlockMedia> = emptyList()): String {
        val kinds = media.associate { it.id to it.kind }
        val seen = HashSet<String>()
        return body.split('\n').mapNotNull { line ->
            val id = markerId(line)
            when {
                id == null -> unescapeLine(line)
                !seen.add(id) -> null
                kinds[id] == "voice" -> "[voice note]"
                else -> "[photo]"
            }
        }.joinToString("\n")
    }

    /** What the Markdown export needs to write a media block. */
    data class MarkdownMedia(val kind: String, val path: String, val durationMs: Long = 0)

    /**
     * [body] as Markdown for the monthly export: text as written, photos as `![photo](path)`, voice notes as
     * `🎙 voice note (0:42)`, each media block on its own paragraph in the right place.
     */
    fun toMarkdown(body: String, media: Map<String, MarkdownMedia>): String {
        val blocks = parse(body, media.map { (id, m) -> BlockMedia(id, m.kind) })
        val parts = ArrayList<String>()
        for (b in blocks) {
            val text = when (b) {
                is JournalBlock.Text -> b.text.trim('\n').takeIf { it.isNotBlank() }
                else -> media[b.mediaId]?.let {
                    if (it.kind == "voice") "🎙 voice note (${it.durationMs / 60000}:${(it.durationMs / 1000 % 60).toString().padStart(2, '0')})"
                    else "![photo](${it.path})"
                } ?: "(attachment not available)"
            }
            if (text != null) parts += text
        }
        return parts.joinToString("\n\n")
    }
}
