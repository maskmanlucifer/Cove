package app.cove.companion.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.cove.companion.core.Clock
import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.backup.BackupScheduler
import app.cove.companion.data.backup.BackupService
import app.cove.companion.data.backup.RoomBackupStore
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.media.JournalFiles
import app.cove.companion.data.media.MediaFetcher
import app.cove.companion.data.media.MediaRows
import app.cove.companion.data.media.MediaUploadScheduler
import app.cove.companion.data.media.MediaUploader
import app.cove.companion.data.media.ThumbStore
import app.cove.companion.data.repo.ChangeLog
import app.cove.companion.data.repo.JournalRepository
import app.cove.companion.data.repo.SettingsRepository
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.sync.RoomSyncStore
import app.cove.companion.data.sync.SyncTables
import io.ktor.client.HttpClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Wires Drive into the app: authorization, client, uploader, fetcher and backups. Everything is inert (uploads stay
 * `pending`) while [enabled] is false, i.e. no Google client id, signed out, or no consent yet.
 * [useFake] swaps in a folder-backed fake Drive for the debug flag.
 */
class DriveKit(
    private val context: Context,
    private val db: CoveDatabase,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val changeLog: ChangeLog,
    private val auth: AuthRepository,
    private val webClientId: String,
    private val http: HttpClient,
    private val thumbs: ThumbStore?,
    private val files: JournalFiles,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("cove_drive", Context.MODE_PRIVATE)
    private var fake: FakeDriveClient? = null

    private val _connected = MutableStateFlow(prefs.getBoolean(CONNECTED, false))

    /** True once Google has granted Drive access on this phone. */
    val connected: StateFlow<Boolean> = _connected

    private val googleAuth = GoogleDriveAuth(
        context, { webClientId.isNotBlank() && auth.state.value is AuthState.SignedIn },
        onGranted = {
            prefs.edit().putBoolean(CONNECTED, true).apply()
            _connected.value = true
        },
    )
    private val realClient by lazy { KtorDriveClient(http, googleAuth) }

    /** True when Drive can be used right now. */
    val enabled: Boolean get() = fake != null || (webClientId.isNotBlank() && auth.state.value is AuthState.SignedIn)

    /** The client in use: the fake in debug mode, else the real one. */
    val client: DriveClient get() = fake ?: realClient

    /** Consent screen the activity should show, if any. */
    val consentRequests: StateFlow<PendingIntent?> get() = googleAuth.consentRequests

    /** The activity launched the consent screen. */
    fun consumeConsent() = googleAuth.consumeConsent()

    /** The consent screen returned; caches the grant and retries uploads. */
    fun onConsentResult(data: Intent?) {
        googleAuth.onConsentResult(data)
        requestUpload()
    }

    /**
     * Asks Google for Drive access now, for the "Connect Drive" button. [DriveToken.NeedsConsent] means Google's
     * approval screen is being shown (see [consentRequests]); the result arrives through [onConsentResult].
     */
    suspend fun connect(): DriveToken = googleAuth.token()

    /** Debug only: keep "Drive" in plain files under `filesDir/drive-fake`. */
    fun useFake() {
        fake = FakeDriveClient(File(context.filesDir, "drive-fake"), clock::now)
        _lastBackup.value = prefs.getLong(LAST_BACKUP, 0)
    }

    private val _lastBackup = MutableStateFlow(prefs.getLong(LAST_BACKUP, 0))

    /** Epoch millis of the last successful backup from this device, 0 when none. */
    val lastBackupAt: StateFlow<Long> = _lastBackup

    /** Builds an uploader over the current client. */
    fun uploader() = MediaUploader(RoomMediaRows(), client, thumbs)

    /** Fetcher for media that is not on this device yet. */
    val fetcher = MediaFetcher({ if (enabled) client else null }, thumbs, { files.fetched(it.id, it.kind) }, files::thumb)

    /** Builds the backup/restore service over the current client. */
    fun backupService() = BackupService(
        RoomBackupStore(RoomSyncStore(db), changeLog, SyncTables.all), client, clock::now,
        tempFile = { File(context.cacheDir, "restore.json.gz") },
    )

    /** Runs a backup now and remembers when it finished. */
    suspend fun backUpNow() = backupService().backUp().also {
        if (it is app.cove.companion.data.backup.BackupResult.Done) {
            prefs.edit().putLong(LAST_BACKUP, clock.now()).apply()
            _lastBackup.value = clock.now()
        }
    }

    /** Queues an upload pass respecting the Wi-Fi-only setting; does nothing while Drive is off. */
    fun requestUpload() {
        if (!enabled) return
        scope.launch { MediaUploadScheduler.requestNow(context, settings.settings.first().uploadOnWifiOnly) }
    }

    /** Starts the schedules once signed in and keeps the network constraint in step with the setting. Call once. */
    fun start() {
        if (webClientId.isBlank()) {
            if (fake == null) {
                BackupScheduler.cancel(context)
                MediaUploadScheduler.cancelAll(context)
            }
            return
        }
        scope.launch {
            auth.state.collect { state ->
                if (state is AuthState.SignedIn) {
                    BackupScheduler.schedule(context)
                    requestUpload()
                } else {
                    BackupScheduler.cancel(context)
                    MediaUploadScheduler.cancelAll(context)
                }
            }
        }
        scope.launch {
            settings.settings.map { it.uploadOnWifiOnly }.distinctUntilChanged().collect {
                if (enabled) MediaUploadScheduler.schedulePeriodic(context, it)
            }
        }
    }

    private inner class RoomMediaRows : MediaRows {
        override suspend fun pending(): List<JournalMediaEntity> = db.journal().pendingUploads()
        override suspend fun save(media: JournalMediaEntity) = journal.saveMedia(media)
    }

    private companion object {
        const val LAST_BACKUP = "lastBackupAt"
        const val CONNECTED = "connected"
    }
}
