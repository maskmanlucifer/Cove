package app.cove.companion.data.insights

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cove.companion.ai.AiService
import app.cove.companion.data.local.CoveDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device journal search: SQLite FTS4 over entry text plus stored insight text, fused with cosine similarity over
 * stored embeddings when [ai] can embed on the phone. Nothing here touches the network.
 * The FTS table lives beside Room's tables (created on first use) so the Room schema stays unchanged.
 */
class JournalSearch(private val db: CoveDatabase, private val ai: AiService) {
    private val lock = Mutex()
    private var ready = false

    private suspend fun sql(): SupportSQLiteDatabase = lock.withLock {
        val sdb = db.openHelper.writableDatabase
        if (!ready) {
            sdb.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS journal_fts USING fts4(entryId, content, notindexed=entryId, tokenize=unicode61)")
            ready = true
        }
        sdb
    }

    /** Replaces the searchable text of [entryId]. */
    suspend fun put(entryId: String, text: String) = withContext(Dispatchers.IO) {
        val sdb = sql()
        sdb.execSQL("DELETE FROM journal_fts WHERE entryId = ?", arrayOf(entryId))
        if (text.isNotBlank()) sdb.execSQL("INSERT INTO journal_fts (entryId, content) VALUES (?, ?)", arrayOf(entryId, text))
    }

    suspend fun remove(entryId: String) = put(entryId, "")

    /** Entries matching [query], best first. Empty for blank input. */
    suspend fun search(query: String, limit: Int = 20): List<JournalHit> = withContext(Dispatchers.IO) {
        val words = SearchMath.tokens(query)
        val match = SearchMath.ftsQuery(query) ?: return@withContext emptyList()
        val keyword = mutableListOf<Pair<String, Int>>()
        sql().query("SELECT entryId, content FROM journal_fts WHERE journal_fts MATCH ?", arrayOf(match)).use { c ->
            while (c.moveToNext()) keyword += c.getString(0) to SearchMath.keywordScore(c.getString(1), words)
        }
        val semantic = ai.embed(query).valueOrNull()?.let { q ->
            db.journal().allIndex().mapNotNull { row ->
                row.embedding?.let { row.entryId to SearchMath.cosine(q, SearchMath.fromBytes(it)) }
            }
        }.orEmpty()
        SearchMath.rank(keyword, semantic, limit)
    }
}
