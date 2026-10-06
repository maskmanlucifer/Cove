package app.cove.companion.data.local.entity

import androidx.room.Entity

/**
 * A row edited on this phone and on another device since the last pull (local only, never synced).
 * [localJson] and [remoteJson] are server-shaped row JSON; [localAt]/[remoteAt] are epoch millis.
 */
@Entity(tableName = "sync_conflicts", primaryKeys = ["tableName", "rowId"])
data class SyncConflictEntity(
    val tableName: String,
    val rowId: String,
    val localJson: String,
    val remoteJson: String,
    val remoteDevice: String,
    val localAt: Long,
    val remoteAt: Long,
    val detectedAt: Long,
)
