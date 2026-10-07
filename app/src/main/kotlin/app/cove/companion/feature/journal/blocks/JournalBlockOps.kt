package app.cove.companion.feature.journal.blocks

import app.cove.companion.feature.journal.blocks.JournalBodyCodec.normalise

/** Result of inserting a block: the new [blocks] and the id of the text block the caret continues in (at its start). */
data class Inserted(val blocks: List<JournalBlock>, val focusId: String)

/** What [JournalBlockOps.delete] needs to put a block back: where it was and, if text was merged, where. */
data class Removal(val block: JournalBlock, val index: Int, val mergedId: String?, val mergeOffset: Int, val joiner: Boolean)

/** Pure edits of a block list; every result is normalised. */
object JournalBlockOps {
    /**
     * Inserts [media] at [offset] of the text block [textId], splitting it into text, media, text. The first part keeps
     * the id. An unknown [textId] means the end of the document.
     */
    fun insertAt(blocks: List<JournalBlock>, textId: String?, offset: Int, media: JournalBlock): Inserted {
        require(media !is JournalBlock.Text)
        val list = normalise(blocks).filter { it.id != media.id }
        val found = list.indexOfFirst { it is JournalBlock.Text && it.id == textId }
        val index = if (found >= 0) found else list.lastIndex
        val target = list[index] as JournalBlock.Text
        val at = (if (found >= 0) offset else target.text.length).coerceIn(0, target.text.length)
        val after = JournalBlock.Text(target.text.substring(at))
        val out = list.toMutableList()
        out[index] = target.copy(text = target.text.substring(0, at))
        out.addAll(index + 1, listOf(media, after))
        return Inserted(normalise(out), after.id)
    }

    /** Removes the block at [index]; neighbouring text blocks merge. The [Removal] lets [restore] undo it. */
    fun delete(blocks: List<JournalBlock>, index: Int): Pair<List<JournalBlock>, Removal> {
        val list = blocks.toMutableList()
        val block = list.removeAt(index)
        val before = list.getOrNull(index - 1) as? JournalBlock.Text
        val after = list.getOrNull(index) as? JournalBlock.Text
        val removal = if (before != null && after != null) {
            Removal(block, index, before.id, before.text.length, before.text.isNotEmpty() && after.text.isNotEmpty())
        } else {
            Removal(block, index, null, 0, false)
        }
        return normalise(list) to removal
    }

    /** Puts a removed block back where it was (splitting the merged text again); never loses text. */
    fun restore(blocks: List<JournalBlock>, removal: Removal): List<JournalBlock> {
        val list = normalise(blocks).filter { it.id != removal.block.id }.toMutableList()
        val at = list.indexOfFirst { it.id == removal.mergedId }.takeIf { it >= 0 }
        val merged = at?.let { list[it] as? JournalBlock.Text }
        if (merged != null && removal.mergeOffset <= merged.text.length) {
            val cut = removal.mergeOffset
            val rest = merged.text.substring(cut).let { if (removal.joiner && it.startsWith("\n")) it.substring(1) else it }
            list[at] = merged.copy(text = merged.text.substring(0, cut))
            list.addAll(at + 1, listOf(removal.block, JournalBlock.Text(rest)))
        } else {
            list.add(removal.index.coerceIn(0, list.size), removal.block)
        }
        return normalise(list)
    }

    /** Swaps the block at [index] with its neighbour in direction [delta] (-1 up, +1 down); unchanged at the ends. */
    fun move(blocks: List<JournalBlock>, index: Int, delta: Int): List<JournalBlock> {
        val to = index + delta
        if (index !in blocks.indices || to !in blocks.indices) return normalise(blocks)
        val list = blocks.toMutableList()
        list[index] = list[to].also { list[to] = list[index] }
        return normalise(list)
    }

    /** The blocks in the order of [ids] (as left by a drag); blocks not named keep their relative place at the end. */
    fun reorder(blocks: List<JournalBlock>, ids: List<String>): List<JournalBlock> {
        val byId = blocks.associateBy { it.id }
        val named = ids.mapNotNull(byId::get)
        val rest = blocks.filter { it.id !in ids }
        return normalise(named + rest)
    }
}
