package app.cove.companion.feature.journal.blocks

import java.util.concurrent.atomic.AtomicLong

/**
 * One block of a journal entry body, in reading order. Text, photos and voice notes can follow each other in any
 * order and any number; see `docs/JOURNAL.md`.
 */
sealed interface JournalBlock {
    /** Stable key of the block while it is edited; media blocks use their media id. */
    val id: String

    /** A run of plain text (may hold several lines, may be empty). [id] is the editor key and is not part of the stored body. */
    data class Text(val text: String, override val id: String = nextTextId()) : JournalBlock

    /** A photo shown full width; [mediaId] is the `journal_media` row (which may be missing). */
    data class Photo(val mediaId: String) : JournalBlock {
        override val id: String get() = mediaId
    }

    /** A voice note row; [mediaId] is the `journal_media` row (which may be missing). */
    data class Voice(val mediaId: String) : JournalBlock {
        override val id: String get() = mediaId
    }

    companion object {
        private val counter = AtomicLong()

        /** Fresh editor key for a text block created by an edit. Parsed blocks use `p<index>`. */
        fun nextTextId(): String = "n" + counter.incrementAndGet()
    }
}

/** True for photo and voice blocks. */
val JournalBlock.isMedia: Boolean get() = this !is JournalBlock.Text

/** The media id of a photo or voice block, null for text. */
val JournalBlock.mediaId: String?
    get() = when (this) {
        is JournalBlock.Photo -> mediaId
        is JournalBlock.Voice -> mediaId
        is JournalBlock.Text -> null
    }

/** A media row as the codec needs it: its [id] and [kind] (`photo` or `voice`). */
data class BlockMedia(val id: String, val kind: String)
