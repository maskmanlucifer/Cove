package app.cove.companion.feature.datacontrols

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.backup.BackupResult
import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.sync.SyncTables
import app.cove.companion.data.wipe.CloudEraser
import app.cove.companion.data.wipe.CloudReport
import app.cove.companion.data.wipe.DataPreview
import app.cove.companion.data.wipe.DeviceWipe
import app.cove.companion.data.wipe.PendingWipe
import app.cove.companion.feature.recovery.StartFreshRule
import app.cove.companion.resilience.CrashHandler
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the "Clear all data" flow is. */
sealed interface ClearPhase {
    /** Reading the preview and waiting for the owner to confirm. */
    data object Confirm : ClearPhase

    /** Deleting cloud copies; [step] is a plain sentence about the current step. */
    data class CloudRunning(val step: String) : ClearPhase

    /** The cloud deletion ended; [report] says exactly what was and was not deleted. */
    data class CloudDone(val report: CloudReport) : ClearPhase

    /** The phone is being cleared and the app restarts. */
    data object Clearing : ClearPhase

    /** The phone could not be prepared for clearing; nothing was deleted. */
    data class Failed(val message: String) : ClearPhase
}

/** Everything the sheet draws. */
data class ClearUi(
    val preview: DataPreview? = null,
    val signedIn: Boolean = false,
    val driveReady: Boolean = false,
    val hasConnections: Boolean = false,
    val alsoCloud: Boolean = false,
    val typed: String = "",
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val phase: ClearPhase = ClearPhase.Confirm,
) {
    /** The cloud option needs its typed word before the final hold works. */
    val canConfirm: Boolean get() = phase == ClearPhase.Confirm && !backupBusy && (!alsoCloud || StartFreshRule.confirmed(typed))
}

/** Drives "Clear all data": preview, optional backup, optional cloud deletion, then the local wipe and restart. */
class ClearDataViewModel(private val c: AppContainer, context: Context) : ViewModel() {
    private val appContext: Context = context.applicationContext
    private val _ui = MutableStateFlow(ClearUi(hasConnections = c.credentialStore.saved != app.cove.companion.data.config.Credentials()))
    val ui: StateFlow<ClearUi> = _ui.asStateFlow()
    private var cloudJob: Job? = null
    private var http: HttpClient? = null

    init {
        val auth = c.auth.state.value
        _ui.update { it.copy(signedIn = auth is AuthState.SignedIn, driveReady = c.driveKit.enabled && c.driveKit.connected.value) }
        viewModelScope.launch { _ui.update { it.copy(preview = withContext(Dispatchers.IO) { preview() }) } }
    }

    private fun preview(): DataPreview {
        val db = c.database.openHelper.readableDatabase
        fun count(table: String): Int = listOf("SELECT COUNT(*) FROM $table WHERE deletedAt IS NULL", "SELECT COUNT(*) FROM $table").firstNotNullOfOrNull { sql ->
            runCatching { db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrNull()
        } ?: 0
        val dbFile = app.cove.companion.security.EncryptedDatabase.file(appContext)
        val files = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).filter { it.isFile }
        return DataPreview.build(::count, File(appContext.filesDir, "journal"), files)
    }

    /** Checks or unchecks "Also delete my cloud copies". */
    fun setAlsoCloud(on: Boolean) = _ui.update { if (it.phase == ClearPhase.Confirm) it.copy(alsoCloud = on, typed = "") else it }

    /** Updates the typed confirmation word. */
    fun type(text: String) = _ui.update { it.copy(typed = text.take(12)) }

    /** "Save a backup first" to Drive. */
    fun backUpToDrive() {
        if (!beginBackup()) return
        viewModelScope.launch {
            val message = when (val r = withContext(Dispatchers.IO) { c.driveKit.backUpNow() }) {
                is BackupResult.Done -> "Backed up to Google Drive."
                BackupResult.NeedsConsent -> "Google needs your permission again. Open Connect services, tap Drive, then try again."
                BackupResult.Offline -> "No connection right now. Try again, or skip this step."
                else -> "Couldn’t back up just now. Try again, or skip this step."
            }
            _ui.update { it.copy(backupBusy = false, backupMessage = message) }
        }
    }

    /** "Save a backup first" to a file the owner picked: a readable copy of the entries (not the photos). */
    fun backUpToFile(uri: Uri) {
        if (!beginBackup()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val store = c.backupStore()
                    val now = c.clock.now()
                    val tables = SyncTables.all.associate { it.name to store.readAll(it) }
                    val json = ExportBuilder.snapshotJson(now, tables) { name -> SyncTables.find(name)?.key ?: "id" }
                    appContext.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(ExportBuilder.gzip(json)) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashHandler.report("clear-backup", e)
                    false
                }
            }
            _ui.update {
                it.copy(backupBusy = false, backupMessage = if (ok) "Saved. Keep that file somewhere safe." else "Couldn’t save the file. Choose another place, such as Downloads.")
            }
        }
    }

    /** Suggested name of the backup file. */
    fun backupFileName(): String = "cove-backup-${YearMonth.from(Instant.ofEpochMilli(c.clock.now()).atZone(ZoneId.systemDefault()))}.json.gz"

    private fun beginBackup(): Boolean {
        var started = false
        _ui.update { if (it.backupBusy || it.phase != ClearPhase.Confirm) it else { started = true; it.copy(backupBusy = true, backupMessage = null) } }
        return started
    }

    /** The owner held the destructive button: runs the cloud deletion first when asked, otherwise clears the phone. */
    fun confirm() {
        var go = false
        _ui.update { if (it.canConfirm) { go = true; it.copy(phase = if (it.alsoCloud) ClearPhase.CloudRunning("Starting") else ClearPhase.Clearing) } else it }
        if (!go) return
        if (_ui.value.alsoCloud) runCloud() else clearPhone(null)
    }

    private fun runCloud() {
        cloudJob = viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { eraser().eraseAll { step -> _ui.update { it.copy(phase = ClearPhase.CloudRunning(step)) } } }
            _ui.update { it.copy(phase = ClearPhase.CloudDone(report)) }
        }
    }

    private fun eraser(): CloudEraser {
        val creds = c.credentials.value
        val auth = c.auth
        val client = http ?: HttpClient(OkHttp).also { http = it }
        val driveKit = c.driveKit
        return CloudEraser(
            creds.supabaseUrl, creds.supabaseAnonKey, client, SyncTables.all,
            token = { auth.accessToken() }, invalidateToken = { auth.invalidateAccessToken() },
            userId = (auth.state.value as? AuthState.SignedIn)?.userId,
            drive = if (driveKit.enabled && driveKit.connected.value) driveKit.client else null,
        )
    }

    /** Stops a running cloud deletion; nothing on the phone has been touched yet. */
    fun stopCloud() {
        cloudJob?.cancel()
        _ui.update { if (it.phase is ClearPhase.CloudRunning) it.copy(phase = ClearPhase.Confirm, typed = "") else it }
    }

    /** After a cloud report: try the cloud steps again. */
    fun retryCloud() {
        var go = false
        _ui.update { if (it.phase is ClearPhase.CloudDone) { go = true; it.copy(phase = ClearPhase.CloudRunning("Starting")) } else it }
        if (go) runCloud()
    }

    /** After a cloud report: clear this phone too (the report is shown once more after the restart). */
    fun continueToPhone() {
        val done = _ui.value.phase as? ClearPhase.CloudDone ?: return
        var go = false
        _ui.update { if (it.phase is ClearPhase.CloudDone) { go = true; it.copy(phase = ClearPhase.Clearing) } else it }
        if (go) clearPhone(done.report)
    }

    private fun clearPhone(report: CloudReport?) {
        viewModelScope.launch(Dispatchers.IO) {
            val notice = if (report == null) PendingWipe.DEFAULT_NOTICE else PendingWipe.DEFAULT_NOTICE + "\n" + report.summary()
            try {
                DeviceWipe.request(appContext, notice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashHandler.report("clear-all", e)
                _ui.update { it.copy(phase = ClearPhase.Failed("Cove couldn’t get ready to clear this phone. Nothing was deleted. Check that the phone has free space, then try again.")) }
            }
        }
    }

    /** Back to the confirm step after a failure. */
    fun reset() = _ui.update { if (it.phase is ClearPhase.Failed) it.copy(phase = ClearPhase.Confirm, typed = "") else it }

    override fun onCleared() {
        http?.close()
    }
}
