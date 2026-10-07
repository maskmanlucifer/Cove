package app.cove.companion.data.memory

import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.MemoryEntity

/** What [MemoryRepository.remember] did, so the command can be undone. */
data class MemorySaved(val id: String, val replaced: List<String>)

/** Things the user asked Cove to remember. Local-only: nothing here is synced, backed up or sent to a cloud model. */
class MemoryRepository(private val database: CoveDatabase, private val clock: Clock) {
    private val dao get() = database.memories()

    /** Keeps [text]; a newer memory about the same [subject] replaces the older one. */
    suspend fun remember(text: String, subject: String, detail: String, kind: String, keepForMs: Long?): MemorySaved {
        val now = clock.now()
        val memory = MemoryEntity(
            id = newId(), text = text, subject = subject.lowercase(), detail = detail, kind = kind,
            keywords = MemoryText.keywords("$subject $text").joinToString(" "),
            createdAt = now, expiresAt = keepForMs?.let { now + it },
        )
        dao.insert(memory)
        val replaced = dao.activeIds(memory.subject, memory.id)
        replaced.forEach { dao.setActive(it, false) }
        return MemorySaved(memory.id, replaced)
    }

    /** Spoken and shown answer to [question]. */
    suspend fun recall(question: String): String {
        val now = clock.now()
        return MemoryRanker.pick(question, dao.live(now))?.let { MemoryAnswer.say(it, now) } ?: MemoryAnswer.NOTHING
    }

    /** Undoes [remember]: removes [id] and brings back what it replaced. */
    suspend fun undo(id: String, replaced: List<String>) {
        dao.delete(id)
        replaced.forEach { dao.setActive(it, true) }
    }
}
