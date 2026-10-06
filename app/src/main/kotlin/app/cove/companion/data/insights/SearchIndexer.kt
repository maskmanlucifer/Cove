package app.cove.companion.data.insights

import app.cove.companion.ai.AiService
import app.cove.companion.core.Clock
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.SearchIndexEntity
import java.io.File

/**
 * Keeps the local search index in step with saved entries. [indexText] is cheap and runs on every save;
 * [enrich] asks [ai] for a summary, mood, tags, photo captions and an embedding. Those capabilities are
 * journal-grade: they run on the phone only, and only while the app is on screen (the router enforces both).
 * Results are stored in `search_index`, which is never synced.
 */
class SearchIndexer(
    private val db: CoveDatabase,
    private val search: JournalSearch,
    private val ai: AiService,
    private val clock: Clock,
) {
    /** Makes [entry]'s title, body and any stored insight text searchable. */
    suspend fun indexText(entry: JournalEntryEntity) {
        val stored = db.journal().index(entry.id)
        search.put(entry.id, searchableText(entry, stored))
    }

    /** Runs the on-device model over [entry]; a no-op in the background or when no model is available. */
    suspend fun enrich(entry: JournalEntryEntity) {
        if (entry.deletedAt != null) return
        val text = "${entry.title}\n${entry.body}".trim()
        val photos = db.journal().mediaOf(entry.id).filter { it.kind == "photo" }
        if (text.isEmpty() && photos.isEmpty()) return

        val summary = if (text.isEmpty()) null else ai.summarize(text).valueOrNull()
        val captions = captions(photos.mapNotNull { it.thumbPath })
        val old = db.journal().index(entry.id)
        val base = old ?: SearchIndexEntity(entry.id)
        val row = if (summary == null && captions.isEmpty()) base else base.copy(
            summary = if (text.isEmpty()) "" else summary?.sentence.orEmpty().ifEmpty { base.summary },
            aiMood = if (text.isEmpty()) null else summary?.mood ?: base.aiMood,
            tags = if (text.isEmpty()) "" else summary?.tags.orEmpty().joinToString(" ").ifEmpty { base.tags },
            caption = captions.ifEmpty { base.caption },
        )
        val vector = ai.embed(searchableText(entry, row)).valueOrNull()
        if (summary == null && captions.isEmpty() && vector == null) return
        val saved = row.copy(embedding = vector?.let(SearchMath::toBytes) ?: row.embedding, updatedAt = clock.now())
        db.journal().upsertIndex(saved)
        search.put(entry.id, searchableText(entry, saved))
    }

    private suspend fun captions(thumbPaths: List<String>): String =
        thumbPaths.take(MAX_CAPTIONS).mapNotNull { ai.captionImage(File(it)).valueOrNull() }.joinToString(" ")

    private companion object {
        const val MAX_CAPTIONS = 3
    }
}

/** Everything about [entry] that search should see. */
internal fun searchableText(entry: JournalEntryEntity, index: SearchIndexEntity?): String =
    listOfNotNull(entry.title, entry.body, entry.mood, index?.summary, index?.transcript, index?.caption, index?.tags)
        .filter { it.isNotBlank() }.joinToString("\n")
