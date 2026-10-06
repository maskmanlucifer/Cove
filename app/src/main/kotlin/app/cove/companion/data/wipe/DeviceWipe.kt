package app.cove.companion.data.wipe

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import app.cove.companion.container
import app.cove.companion.core.Clock
import app.cove.companion.feature.alarms.AlarmMirror
import app.cove.companion.feature.alarms.AlarmScheduler
import app.cove.companion.feature.nudges.NudgeScheduler
import app.cove.companion.feature.recovery.RecoveryActions
import app.cove.companion.feature.training.rest.RestAlarm
import app.cove.companion.resilience.CrashHandler
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren

/**
 * Android side of "Clear all data" (Me) and "Start fresh" (Recovery): [request] stops everything and records the
 * `pending_wipe` marker, a restart follows, and [processAtStart] deletes the files before anything opens the database.
 */
object DeviceWipe {
    /** Folders a wipe works on for [context]. */
    fun dirs(context: Context): WipeDirs {
        val app = context.applicationContext
        return WipeDirs(
            databases = File(app.dataDir, "databases"), files = app.filesDir, noBackup = app.noBackupFilesDir,
            cache = app.cacheDir, prefs = File(app.dataDir, "shared_prefs"), root = app.dataDir,
        )
    }

    /** Deletes every Cove key from the Android Keystore. */
    private val keystore = KeyEraser {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.aliases().toList().forEach(store::deleteEntry)
    }

    /**
     * Finishes a requested wipe. Call from the main process only, before the [app.cove.companion.AppContainer] is created.
     * Blocks the calling thread: nothing may open the database until it is done.
     */
    fun processAtStart(context: Context): WipeOutcome =
        runCatching { WipeProcessor(dirs(context), keystore).processIfPending() }.getOrElse {
            CrashHandler.report("wipe", it as? Exception ?: Exception(it))
            WipeOutcome.Partial(listOf("unexpected error"))
        }

    /** Message to show once on this launch (and forgets it), or null. */
    fun takeNotice(context: Context): String? {
        val file = dirs(context).notice
        if (!file.isFile) return null
        return runCatching { file.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull().also { file.delete() }
    }

    /**
     * Stops background work and alarms, records the marker, closes the database and restarts the app. The deletion
     * itself happens in the next process. Call off the main thread.
     *
     * @param notice shown once after the restart; may carry the cloud deletion report.
     * @param restart false when the caller restarts itself afterwards.
     */
    fun request(context: Context, notice: String = PendingWipe.DEFAULT_NOTICE, restart: Boolean = true) {
        val app = context.applicationContext
        val container = app.container
        runCatching { container.appScope.coroutineContext[Job]?.cancelChildren() }
        runCatching { container.cloud.close() }
        cancelScheduled(app)
        PendingWipe.write(dirs(app).marker, PendingWipe(Clock.System.now(), notice))
        runCatching { container.database.close() }
        if (restart) RecoveryActions(app).restart()
    }

    /** Cancels alarms, snoozes, reminders, nudges, the rest timer, queued jobs and posted notifications. */
    fun cancelScheduled(app: Context) {
        runCatching {
            val scheduler = AlarmScheduler(app, Clock.System)
            val ids = AlarmMirror.forContext(app).ids() +
                app.getSharedPreferences("alarm_scheduler", Context.MODE_PRIVATE).getStringSet("registered_ids", emptySet()).orEmpty()
            ids.toSet().forEach { scheduler.cancel(it); scheduler.cancelSnooze(it) }
        }
        runCatching { NudgeScheduler(app, app.container).cancelAll() }
        runCatching { RestAlarm.cancel(app) }
        runCatching { WorkManager.getInstance(app).apply { cancelAllWork(); pruneWork() } }
        runCatching { app.getSystemService(NotificationManager::class.java).cancelAll() }
    }

    /** True for the process Android started for the app itself (not the `:restart` helper). */
    fun isMainProcess(context: Context): Boolean =
        android.app.Application.getProcessName() == context.packageName
}
