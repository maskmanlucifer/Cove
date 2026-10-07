package app.cove.companion.data.sms

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Why the inbox could not be read. The screen turns these into calm sentences. */
class SmsReadException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { PermissionRevoked, Unavailable }
}

/** Where messages come from: the phone's inbox, text the user pasted, or a fake in debug builds. */
interface SmsSource {
    /** Number of messages at or after [since], or null when it cannot be counted cheaply. */
    suspend fun count(since: Long): Int?

    /** Calls [onPage] with messages at or after [since], newest first, in pages of at most [pageSize]. */
    suspend fun read(since: Long, pageSize: Int = PAGE, onPage: suspend (List<SmsMessage>) -> Unit)

    companion object {
        /** Messages parsed per chunk, so a 50,000-message inbox never sits in memory at once. */
        const val PAGE = 500
    }
}

/** Reads the system SMS inbox (needs READ_SMS) through a cursor, off the main thread, one page at a time. */
class ContentResolverSmsInbox(private val context: Context) : SmsSource {
    private val uri: Uri = Uri.parse("content://sms/inbox")

    override suspend fun count(since: Long): Int? = withContext(Dispatchers.IO) {
        open(arrayOf("_id"), since)?.use { it.count }
    }

    override suspend fun read(since: Long, pageSize: Int, onPage: suspend (List<SmsMessage>) -> Unit) {
        val cursor = withContext(Dispatchers.IO) { open(arrayOf("_id", "address", "body", "date"), since) } ?: throw SmsReadException(SmsReadException.Kind.Unavailable)
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("_id")
            val addrCol = c.getColumnIndexOrThrow("address")
            val bodyCol = c.getColumnIndexOrThrow("body")
            val dateCol = c.getColumnIndexOrThrow("date")
            val page = ArrayList<SmsMessage>(pageSize)
            while (true) {
                currentCoroutineContext().ensureActive()
                page.clear()
                withContext(Dispatchers.IO) {
                    try {
                        while (page.size < pageSize && c.moveToNext()) {
                            page += SmsMessage(c.getLong(idCol), c.getString(addrCol), c.getString(bodyCol).orEmpty(), c.getLong(dateCol))
                        }
                    } catch (e: SecurityException) {
                        throw SmsReadException(SmsReadException.Kind.PermissionRevoked, e)
                    } catch (e: RuntimeException) {
                        throw SmsReadException(SmsReadException.Kind.Unavailable, e)
                    }
                }
                if (page.isEmpty()) break
                onPage(page.toList())
                if (page.size < pageSize) break
            }
        }
    }

    private fun open(projection: Array<String>, since: Long) = try {
        context.contentResolver.query(uri, projection, "date >= ?", arrayOf(since.toString()), "date DESC")
    } catch (e: SecurityException) {
        throw SmsReadException(SmsReadException.Kind.PermissionRevoked, e)
    } catch (e: RuntimeException) {
        throw SmsReadException(SmsReadException.Kind.Unavailable, e)
    }
}

/** Messages the user pasted: no sender, stamped with [receivedAt], split with [SmsTransactionParser.splitPasted]. */
class PastedSource(text: String, private val receivedAt: Long) : SmsSource {
    private val parts = SmsTransactionParser.splitPasted(text)

    override suspend fun count(since: Long): Int = parts.size

    override suspend fun read(since: Long, pageSize: Int, onPage: suspend (List<SmsMessage>) -> Unit) {
        parts.chunked(pageSize).forEach { chunk -> onPage(chunk.map { SmsMessage(null, null, it, receivedAt) }) }
    }
}

/** One message handed over by the live receiver; nothing is read from the inbox. */
class OneMessageSource(private val message: SmsMessage) : SmsSource {
    override suspend fun count(since: Long): Int = 1

    override suspend fun read(since: Long, pageSize: Int, onPage: suspend (List<SmsMessage>) -> Unit) = onPage(listOf(message))
}
