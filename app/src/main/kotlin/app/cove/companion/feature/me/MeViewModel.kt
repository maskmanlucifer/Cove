package app.cove.companion.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.backup.BackupResult
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.feature.onboarding.saveWakeTime
import app.cove.companion.data.sync.ConflictDescriber
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reads and writes the settings shown on the Me tab; every change persists immediately. */
class MeViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<SettingsEntity?> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Clears the "Import from messages" log; expenses stay. */
    fun forgetImportedMessages() {
        viewModelScope.launch { c.smsImport.forgetHistory() }
    }

    /** Clears what Cove learned about payees (`payee_memory`); expenses and word memory stay. */
    fun forgetPayees() {
        viewModelScope.launch { c.money.forgetAllPayees() }
    }

    /** Enabled alarms, earliest first. */
    val alarms: StateFlow<List<AlarmEntity>> = c.plan.alarms
        .map { list -> list.filter { it.deletedAt == null && it.enabled }.sortedBy { it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sync row text, refreshed every 30 s so "2 min ago" keeps moving. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sync: StateFlow<SyncUi> = c.cloudChanges().flatMapLatest { cloud ->
        combine(
            cloud.sync.authState, cloud.sync.status, cloud.sync.conflicts,
            flow { while (true) { emit(Unit); delay(30_000) } },
        ) { auth, status, conflicts, _ -> syncUi(auth, status, conflicts.size, c.clock.now()) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUi("", false, false, 0))

    /** Title of the oldest unresolved conflict, for the banner. */
    val conflictTitle: StateFlow<String?> = c.sync.conflicts
        .map { list -> list.firstOrNull()?.let { ConflictDescriber.describe(it).title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Back-up/restore sheet state: what is running and the message to show. */
    data class BackupUi(val busy: Boolean = false, val message: String? = null, val needsConnect: Boolean = false)

    private val _backup = kotlinx.coroutines.flow.MutableStateFlow(BackupUi())
    val backup: StateFlow<BackupUi> = _backup

    /** "Never", "2 d ago" or "Not set up" for the Back up now row. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val backupLabel: StateFlow<String> = c.cloudChanges().flatMapLatest { cloud ->
        combine(
            cloud.drive.lastBackupAt, flow { while (true) { emit(Unit); delay(30_000) } },
        ) { last, _ -> backupLabel(cloud.drive.enabled, last, c.clock.now()) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Clears the sheet message when a sheet opens or closes. */
    fun resetBackup() {
        _backup.value = BackupUi()
    }

    /** Backs up to Drive now. */
    fun backUp() = runBackup(restoring = false) { c.driveKit.backUpNow() }

    /** Restores the newest Drive backup into this empty Cove. */
    fun restore() = runBackup(restoring = true) { c.driveKit.backupService().restoreLatest() }

    private fun runBackup(restoring: Boolean, work: suspend () -> BackupResult) {
        if (_backup.value.busy) return
        if (!c.driveKit.enabled) {
            _backup.value = BackupUi(message = BackupNotSignedIn, needsConnect = true)
            return
        }
        _backup.value = BackupUi(busy = true)
        viewModelScope.launch {
            // Room refuses the main thread, and a stuck network call must not spin forever.
            val result = withTimeoutOrNull(BACKUP_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    try {
                        work()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CrashHandler.report("backup", e)
                        BackupResult.Failed("")
                    }
                }
            } ?: BackupResult.Offline
            if (result is BackupResult.Failed) result.cause?.let { CrashHandler.report("backup", it) }
            _backup.value = BackupUi(message = backupMessage(result, restoring), needsConnect = backupNeedsConnect(result))
        }
    }

    /** Asks sync to run now. */
    fun retrySync() = c.sync.requestSync()

    private companion object {
        const val BACKUP_TIMEOUT_MS = 120_000L
    }

    fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { c.settings.update(change) }
    }

    fun setWake(minutes: Int) {
        viewModelScope.launch { saveWakeTime(c, minutes) }
    }
}
