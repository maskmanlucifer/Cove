package app.cove.companion.feature.journal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import app.cove.companion.feature.journal.blocks.JournalBlock
import app.cove.companion.feature.journal.blocks.JournalBlockOps
import app.cove.companion.feature.journal.blocks.JournalBodyCodec
import app.cove.companion.feature.journal.blocks.Removal

/** A caret position: [offset] inside text block [textId]; a null id means the end of the document. */
data class InsertPoint(val textId: String?, val offset: Int)

/**
 * The editable body: the block structure plus one live [TextFieldState] per text block, kept by block id so a field
 * keeps its focus, caret and IME session while other blocks come and go. All structure edits go through the pure
 * [JournalBlockOps].
 */
class JournalDocument {
    /** Blocks in reading order; text blocks carry only their id here (their text lives in [textState]). */
    var structure by mutableStateOf<List<JournalBlock>>(listOf(JournalBlock.Text("")))
        private set

    /** Text block the editor should focus next (consumed by the screen). */
    var focusRequest by mutableStateOf<String?>(null)

    /** Last text block that had focus; chips insert at its caret. Null when the title was last. */
    var anchorId: String? = null

    private val texts = HashMap<String, TextFieldState>()

    /** The live text of block [id]. */
    fun textState(id: String): TextFieldState = texts.getOrPut(id) { TextFieldState() }

    /** Current blocks with their live text. */
    fun snapshot(): List<JournalBlock> =
        structure.map { if (it is JournalBlock.Text) it.copy(text = texts[it.id]?.text?.toString() ?: it.text) else it }

    /** The body string to store. */
    fun body(): String = JournalBodyCodec.serialize(snapshot())

    /** Replaces everything with [blocks] (opening an entry). */
    fun load(blocks: List<JournalBlock>) {
        texts.clear()
        apply(blocks)
    }

    /** Shows [blocks]: reuses the state of text blocks that keep their id, creates the new ones. [caretAtStart] ids get the caret at 0. */
    private fun apply(blocks: List<JournalBlock>, caretAtStart: String? = null, caretAt: Pair<String, Int>? = null) {
        val keep = blocks.filterIsInstance<JournalBlock.Text>().map { it.id }.toSet()
        texts.keys.retainAll(keep)
        blocks.filterIsInstance<JournalBlock.Text>().forEach { b ->
            val state = texts[b.id] ?: TextFieldState(b.text).also { texts[b.id] = it }
            if (state.text.toString() != b.text) state.edit { replace(0, length, b.text); selection = TextRange(b.text.length) }
            if (b.id == caretAtStart) state.edit { selection = TextRange.Zero }
            if (caretAt?.first == b.id) state.edit { selection = TextRange(caretAt.second.coerceIn(0, length)) }
        }
        structure = blocks
        if (anchorId !in keep) anchorId = null
    }

    /** Index in [structure] of the block with [id], or -1. */
    fun indexOf(id: String) = structure.indexOfFirst { it.id == id }

    /** True when a media block with [id] exists. */
    fun hasMedia(id: String) = structure.any { it !is JournalBlock.Text && it.id == id }

    /** Where a chip would insert now: the caret of the last focused text block, or the end of the document. */
    fun insertPoint(): InsertPoint = InsertPoint(anchorId, anchorId?.let { texts[it]?.selection?.start } ?: 0)

    /** Inserts [media] at [point] (end of the document when that block is gone) and asks for focus after it. */
    fun insert(media: JournalBlock, point: InsertPoint = insertPoint()) {
        val r = JournalBlockOps.insertAt(snapshot(), point.textId, point.offset, media)
        apply(r.blocks, caretAtStart = r.focusId)
        focusRequest = r.focusId
    }

    /** Appends [media] at the end without touching focus (rows that arrived from sync). */
    fun append(media: JournalBlock) {
        val r = JournalBlockOps.insertAt(snapshot(), null, 0, media)
        apply(r.blocks)
    }

    /** Removes the media block [id]; returns what [restore] needs, or null when there is no such block. */
    fun remove(id: String): Removal? {
        val i = indexOf(id).takeIf { it >= 0 } ?: return null
        val (blocks, removal) = JournalBlockOps.delete(snapshot(), i)
        val boundary = removal.mergedId?.let { it to removal.mergeOffset }
        apply(blocks, caretAt = boundary)
        return removal
    }

    /** Puts a removed block back (Undo). */
    fun restore(removal: Removal) = apply(JournalBlockOps.restore(snapshot(), removal))

    /** Swaps block [id] with its neighbour; [delta] is -1 for up and +1 for down. */
    fun move(id: String, delta: Int) {
        val i = indexOf(id).takeIf { it >= 0 } ?: return
        apply(JournalBlockOps.move(snapshot(), i, delta))
    }

    /** Applies the order a drag ended in. */
    fun reorder(ids: List<String>) = apply(JournalBlockOps.reorder(snapshot(), ids))

    /** Id of the text block before the media block that precedes text block [id], or null. */
    fun previousTextId(id: String): String? {
        val i = indexOf(id)
        if (i < 2) return null
        return structure.getOrNull(i - 2)?.takeIf { it is JournalBlock.Text }?.id
    }

    /** True when the document is one empty text block (shows the "Write whatever…" hint). */
    val isEmpty: Boolean get() = structure.size == 1 && texts[structure[0].id]?.text.isNullOrEmpty()
}

/** The rows of the photo blocks, in reading order, for the full-screen viewer; photos whose row is missing are skipped. */
fun JournalDocument.photoRows(rows: List<app.cove.companion.data.local.entity.JournalMediaEntity>): List<app.cove.companion.data.local.entity.JournalMediaEntity> {
    val byId = rows.associateBy { it.id }
    return structure.mapNotNull { b -> byId[b.id]?.takeIf { it.kind == "photo" && b !is JournalBlock.Text } }
}
