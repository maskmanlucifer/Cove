package app.cove.companion.feature.journal.blocks

import app.cove.companion.feature.journal.blocks.JournalBlock.Photo
import app.cove.companion.feature.journal.blocks.JournalBlock.Text
import app.cove.companion.feature.journal.blocks.JournalBlock.Voice
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalBlockOpsTest {
    @Test fun insertSplitsTextAtCaret() {
        val t = Text("Hello world", "t")
        val r = JournalBlockOps.insertAt(listOf(t), "t", 6, Photo("a"))
        assertEquals("T(Hello )|P(a)|T(world)", r.blocks.shape())
        assertEquals("t", r.blocks[0].id)
        assertEquals(r.blocks[2].id, r.focusId)
    }

    @Test fun insertAtEndWithNoFocusAppends() {
        val r = JournalBlockOps.insertAt(listOf(Text("a", "t"), Photo("p"), Text("b", "u")), null, 0, Voice("v"))
        assertEquals("T(a)|P(p)|T(b)|V(v)|T()", r.blocks.shape())
    }

    @Test fun insertAtStartGivesTextBelowAndEmptyAbove() {
        assertEquals("T()|P(a)|T(hi)", JournalBlockOps.insertAt(listOf(Text("hi", "t")), "t", 0, Photo("a")).blocks.shape())
    }

    @Test fun repeatedInsertsBuildAnyOrder() {
        var blocks: List<JournalBlock> = listOf(Text("x", "t"))
        var focus = "t"
        var off = 1
        for (m in listOf(Photo("a"), Photo("b"), Voice("v1"), Voice("v2"), Photo("c"))) {
            val r = JournalBlockOps.insertAt(blocks, focus, off, m)
            blocks = r.blocks; focus = r.focusId; off = 0
        }
        assertEquals("T(x)|P(a)|T()|P(b)|T()|V(v1)|T()|V(v2)|T()|P(c)|T()", blocks.shape())
        assertEquals(JournalBodyCodec.serialize(blocks), JournalBodyCodec.serialize(JournalBodyCodec.parse(JournalBodyCodec.serialize(blocks), listOf("a", "b", "c").map { BlockMedia(it, "photo") } + listOf("v1", "v2").map { BlockMedia(it, "voice") })))
    }

    @Test fun deleteMergesNeighboursAndRestoreSplitsAgain() {
        val start = listOf(Text("one", "t1"), Photo("a"), Text("two", "t2"))
        val (after, removal) = JournalBlockOps.delete(start, 1)
        assertEquals("T(one\ntwo)", after.shape())
        assertEquals(start.shape(), JournalBlockOps.restore(after, removal).shape())
    }

    @Test fun deleteRestoreWithEmptyNeighbours() {
        for (start in listOf(
            listOf(Text("a", "1"), Photo("p"), Text("", "2")),
            listOf(Text("", "1"), Photo("p"), Text("b", "2")),
            listOf(Photo("p"), Photo("q"), Text("", "2")),
            listOf(Text("a", "1"), Voice("p"), Voice("q"), Text("b", "2")),
        )) {
            val (after, removal) = JournalBlockOps.delete(start, start.indexOfFirst { it.id == "p" })
            assertEquals(start.shape(), JournalBlockOps.restore(after, removal).shape())
        }
    }

    @Test fun restoreKeepsTextTypedInTheMeantime() {
        val (after, removal) = JournalBlockOps.delete(listOf(Text("one", "t1"), Photo("a"), Text("two", "t2")), 1)
        val typed = after.map { if (it is Text) it.copy(text = it.text + "!") else it }
        assertEquals("T(one)|P(a)|T(two!)", JournalBlockOps.restore(typed, removal).shape())
    }

    @Test fun moveSwapsAndNormalises() {
        val s = listOf(Text("a", "1"), Photo("p"), Text("b", "2"))
        assertEquals("T(a\nb)|P(p)|T()", JournalBlockOps.move(s, 1, 1).shape())
        assertEquals("P(p)|T(a\nb)", JournalBlockOps.move(s, 1, -1).shape())
        assertEquals(s.shape(), JournalBlockOps.move(s, 0, -1).shape())
        assertEquals("P(p)|V(v)|T()", JournalBlockOps.move(listOf(Voice("v"), Photo("p"), Text("")), 1, -1).shape())
    }

    @Test fun reorderFollowsIds() {
        val s = listOf(Photo("a"), Voice("v"), Text("x", "t"))
        assertEquals("V(v)|P(a)|T(x)", JournalBlockOps.reorder(s, listOf("v", "a", "t")).shape())
    }
}
