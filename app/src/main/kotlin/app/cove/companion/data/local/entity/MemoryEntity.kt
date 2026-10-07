package app.cove.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something the user asked Cove to keep ("I parked on level 3, pillar B"). Local-only: never synced, never in backups,
 * never sent to a cloud model; it is wiped with the database.
 *
 * @property text what was said, verbatim.
 * @property subject what it is about ("car", "passport"); a newer memory with the same subject replaces the older one.
 * @property detail the part that answers a question ("on level 3, pillar B"); empty for a plain note.
 * @property kind `place` or `note`.
 * @property keywords normalised search words of [text], space separated.
 * @property expiresAt epoch millis after which it is no longer answered (a parking spot lasts a day); null keeps it.
 * @property active false once a newer memory about the same subject replaced it.
 */
@Entity(tableName = "memories", indices = [Index("subject"), Index("createdAt")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val subject: String,
    val detail: String,
    val kind: String,
    val keywords: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val active: Boolean = true,
)
