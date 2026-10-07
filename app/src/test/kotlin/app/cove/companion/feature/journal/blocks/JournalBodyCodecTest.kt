package app.cove.companion.feature.journal.blocks

import app.cove.companion.feature.journal.blocks.JournalBlock.Photo
import app.cove.companion.feature.journal.blocks.JournalBlock.Text
import app.cove.companion.feature.journal.blocks.JournalBlock.Voice
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Canonical, id-free form of [blocks] for comparisons. */
internal fun List<JournalBlock>.shape(): String = joinToString("|") {
    when (it) {
        is Text -> "T(${it.text})"
        is Photo -> "P(${it.mediaId})"
        is Voice -> "V(${it.mediaId})"
    }
}

class JournalBodyCodecTest {
    private val media = listOf("a", "b", "c", "v1", "v2").map { BlockMedia(it, if (it.startsWith("v")) "voice" else "photo") }
    private fun parse(body: String) = JournalBodyCodec.parse(body, media.filter { body.contains(m(it.id)) })
    private fun m(id: String) = JournalBodyCodec.marker(id)

    @Test fun textOnlyIsOneTextBlock() {
        assertEquals("T(hello\n\nworld)", parse("hello\n\nworld").shape())
        assertEquals("T()", parse("").shape())
    }

    @Test fun interleavedSequences() {
        assertEquals("T(x)|P(a)|T(y)", parse("x\n${m("a")}\ny").shape())
        assertEquals("P(a)|P(b)|T()", parse("${m("a")}\n${m("b")}").shape())
        assertEquals("V(v1)|V(v2)|T()", parse("${m("v1")}\n${m("v2")}").shape())
        assertEquals("P(a)|V(v1)|P(b)|T()", parse("${m("a")}\n${m("v1")}\n${m("b")}").shape())
        assertEquals("T(t)|V(v1)|T(u)|P(a)|T(w)", parse("t\n${m("v1")}\nu\n${m("a")}\nw").shape())
        assertEquals("T(x)|P(a)|P(b)|T(y)|V(v1)|V(v2)|T(z)", parse("x\n${m("a")}\n${m("b")}\ny\n${m("v1")}\n${m("v2")}\nz").shape())
    }

    @Test fun emptyTextBetweenMediaIsKept() {
        val body = "${m("a")}\n\n${m("b")}"
        assertEquals("P(a)|T()|P(b)|T()", parse(body).shape())
        assertEquals(body, JournalBodyCodec.serialize(parse(body)))
    }

    @Test fun legacyEntryShowsTextThenPhotosThenVoice() {
        val rows = listOf(media[3], media[0], media[1])
        assertEquals("T(Dear diary)|P(a)|P(b)|V(v1)|T()", JournalBodyCodec.parse("Dear diary", rows).shape())
    }

    @Test fun unreferencedMediaIsAppendedAfterMarkedOnes() {
        val rows = listOf(media[0], media[1], media[3])
        assertEquals("P(b)|T(x)|P(a)|V(v1)|T()", JournalBodyCodec.parse("${m("b")}\nx", rows).shape())
    }

    @Test fun missingMediaIsAPlaceholderBlockNotACrash() {
        assertEquals("T(a)|P(gone)|T(b)", JournalBodyCodec.parse("a\n${m("gone")}\nb", emptyList()).shape())
    }

    @Test fun malformedMarkersStayText() {
        for (bad in listOf("⟦media:abc", "⟦media:⟧", "⟦media:a b⟧", "⟦media:${"x".repeat(65)}⟧", "x ⟦media:a⟧", "⟦media:a⟧ x")) {
            assertEquals("T($bad)", JournalBodyCodec.parse(bad, emptyList()).shape())
        }
    }

    @Test fun duplicateMarkerIsDropped() {
        assertEquals("T(x)|P(a)|T(y\nz)", parse("x\n${m("a")}\ny\n${m("a")}\nz").shape())
    }

    @Test fun markerForAnotherEntrysMediaIsAPlaceholder() {
        assertEquals("P(other)|T()", parse(m("other")).shape())
    }

    @Test fun typedMarkerTextCannotForgeStructure() {
        val typed = listOf(Text("${m("a")}\n${m("b")}"), Photo("c"), Text("\u2060${m("v1")}"))
        val back = JournalBodyCodec.parse(JournalBodyCodec.serialize(typed), listOf(BlockMedia("c", "photo")))
        assertEquals(typed.shape(), back.shape())
    }

    @Test fun trailingBlankLinesTrimmedOnlyThere() {
        assertEquals("a\n\nb", JournalBodyCodec.serialize(listOf(Text("a\n\nb\n  \n\n"))))
        assertEquals("a  ", JournalBodyCodec.serialize(listOf(Text("a  \n"))))
        assertEquals("\n\nx", JournalBodyCodec.serialize(listOf(Text("\n\nx"))))
        assertEquals(m("a"), JournalBodyCodec.serialize(listOf(Photo("a"), Text("\n\n"))))
    }

    @Test fun plainTextRemovesMarkersAndUnescapes() {
        assertEquals("one\ntwo", JournalBodyCodec.plainText("one\n${m("a")}\ntwo"))
        assertEquals("${m("a")}", JournalBodyCodec.plainText(JournalBodyCodec.serialize(listOf(Text(m("a"))))))
        assertEquals("plain text", JournalBodyCodec.plainText("plain text"))
        assertEquals("", JournalBodyCodec.plainText(m("a")))
    }

    @Test fun placeholdersKeepOrder() {
        assertEquals("hi\n[photo]\n[voice note]\nbye", JournalBodyCodec.textWithPlaceholders("hi\n${m("a")}\n${m("v1")}\nbye", media))
    }

    @Test fun markdownExport() {
        val md = JournalBodyCodec.toMarkdown(
            "hi\n${m("a")}\n${m("v1")}\nbye\n${m("zz")}",
            mapOf("a" to JournalBodyCodec.MarkdownMedia("photo", "../Photos/a.webp"), "v1" to JournalBodyCodec.MarkdownMedia("voice", "", 42_000)),
        )
        assertEquals("hi\n\n![photo](../Photos/a.webp)\n\n🎙 voice note (0:42)\n\nbye\n\n(attachment not available)", md)
    }

    @Test fun hugeAndManyBlocks() {
        val big = "word ".repeat(10_000)
        assertEquals(big.trimEnd().length + 1, JournalBodyCodec.parse(big, emptyList()).let { (it.single() as Text).text.length })
        val blocks = (0 until 100).flatMap { listOf(Text("t$it"), if (it % 2 == 0) Photo("m$it") else Voice("m$it")) } + Text("end")
        val kinds = blocks.filter { it !is Text }.map { BlockMedia(it.id, if (it is Voice) "voice" else "photo") }
        assertEquals(blocks.shape(), JournalBodyCodec.parse(JournalBodyCodec.serialize(blocks), kinds).shape())
    }

    @Test fun randomRoundTrip() {
        val rnd = Random(7)
        val pieces = listOf("a", " ", "\n", "\n\n", "⟦", "⟧", "⟦media:", "media:a", "⁠", "\r", "x y", "⟦media:a⟧", "⁠⟦media:b⟧")
        repeat(500) {
            val ids = (0 until rnd.nextInt(0, 6)).map { "m$it" }
            val blocks = ArrayList<JournalBlock>()
            repeat(rnd.nextInt(0, 8)) {
                blocks += if (rnd.nextInt(3) == 0 && ids.isNotEmpty()) ids.random(rnd).let { id -> if (id.hashCode() % 2 == 0) Photo(id) else Voice(id) }
                else Text((0 until rnd.nextInt(0, 6)).joinToString("") { pieces.random(rnd) })
            }
            val kinds = blocks.filter { it !is Text }.map { BlockMedia(it.id, if (it is Voice) "voice" else "photo") }.distinctBy { it.id }
            val body = JournalBodyCodec.serialize(blocks)
            assertEquals(JournalBodyCodec.normalise(blocks).shape(), JournalBodyCodec.parse(body, kinds).shape())
            assertEquals(body, JournalBodyCodec.serialize(JournalBodyCodec.parse(body, kinds)))
            assertTrue(JournalBodyCodec.parse(body, kinds).last() is Text)
        }
    }

    @Test fun arbitraryBodiesNeverCrashAndKeepText() {
        val rnd = Random(3)
        val pieces = listOf("a", "\n", "⟦media:a⟧", "⟦media:", "⟧", "text ", "⁠")
        repeat(500) {
            val body = (0 until rnd.nextInt(0, 30)).joinToString("") { pieces.random(rnd) }
            val blocks = parse(body)
            assertEquals(JournalBodyCodec.serialize(blocks), JournalBodyCodec.serialize(parse(JournalBodyCodec.serialize(blocks))))
            assertTrue(JournalBodyCodec.plainText(body).count { it == 't' } >= blocks.filterIsInstance<Text>().sumOf { b -> b.text.count { it == 't' } } - 0)
        }
    }
}
