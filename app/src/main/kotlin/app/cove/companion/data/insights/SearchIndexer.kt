package app.cove.companion.data.insights

import android.graphics.BitmapFactory
import app.cove.companion.core.Clock
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.SearchIndexEntity

/** True while an Activity of the app is started; Nano only runs then (PLAN 6a). */
fun interface ForegroundState {
    fun isForeground(): Boolean
}

/**
 * Keeps the local search index in step with saved entries. [indexText] is cheap and runs on every save;
 * [enrich] asks the on-device model for a summary, mood, tags, photo captions and an embedding, and only does so
 * while [foreground] says the app is on screen. Results are stored in `search_index`, which is never synced.
 */
class SearchIndexer(
    private val db: CoveDatabase,
    private val search: JournalSearch,
    private val insights: OnDeviceInsights,
    private val embedder: TextEmbedder,
    private val clock: Clock,
    private val foreground: ForegroundState,
) {
    /** Makes [entry]'s title, body and any stored insight text searchable. */
    suspend fun indexText(entry: JournalEntryEntity) {
        val stored = db.journal().index(entry.id)
        search.put(entry.id, searchableText(entry, stored))
    }

    /** Runs the on-device model over [entry]; a no-op in the background or when no model is available. */
    suspend fun enrich(entry: JournalEntryEntity) {
        if (!foreground.isForeground() || entry.deletedAt != null) return
        val text = "${entry.title}\n${entry.body}".trim()
        val photos = db.journal().mediaOf(entry.id).filter { it.kind == "photo" }
        if (text.isEmpty() && photos.isEmpty()) return
        val canThink = insights.isAvailable()
        val canEmbed = embedder.isAvailable()
        if (!canThink && !canEmbed) return

        val old = db.journal().index(entry.id)
        val row = (old ?: SearchIndexEntity(entry.id)).let { base ->
            if (!canThink) base else base.copy(
                summary = if (text.isEmpty()) "" else insights.summarize(text).orEmpty().ifEmpty { base.summary },
                aiMood = if (text.isEmpty()) null else insights.mood(text) ?: base.aiMood,
                tags = if (text.isEmpty()) "" else insights.tags(text).joinToString(" ").ifEmpty { base.tags },
                caption = captions(photos.mapNotNull { it.thumbPath }).ifEmpty { base.caption },
            )
        }
        val vector = if (canEmbed) embedder.embed(searchableText(entry, row)) else null
        val saved = row.copy(embedding = vector?.let(SearchMath::toBytes) ?: row.embedding, updatedAt = clock.now())
        db.journal().upsertIndex(saved)
        search.put(entry.id, searchableText(entry, saved))
    }

    private suspend fun captions(thumbPaths: List<String>): String =
        thumbPaths.take(MAX_CAPTIONS).mapNotNull { path ->
            BitmapFactory.decodeFile(path)?.let { bmp -> insights.caption(bmp).also { bmp.recycle() } }
        }.joinToString(" ")

    private companion object {
        const val MAX_CAPTIONS = 3
    }
}

/** Everything about [entry] that search should see. */
internal fun searchableText(entry: JournalEntryEntity, index: SearchIndexEntity?): String =
    listOfNotNull(entry.title, entry.body, entry.mood, index?.summary, index?.transcript, index?.caption, index?.tags)
        .filter { it.isNotBlank() }.joinToString("\n")
