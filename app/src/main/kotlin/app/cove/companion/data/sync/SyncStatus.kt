package app.cove.companion.data.sync

/** What the Me screen shows for sync. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class UpToDate(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}
