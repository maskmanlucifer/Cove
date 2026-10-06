package app.cove.companion.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cove.companion.container
import java.util.concurrent.TimeUnit

/** Writes the monthly backup to Drive; retried later when Drive is unreachable. */
class MonthlyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kit = applicationContext.container.driveKit
        if (!kit.enabled) return Result.success()
        return when (kit.backupService().backUp()) {
            is BackupResult.Offline, is BackupResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

/** Schedules [MonthlyBackupWorker] every 30 days while charging on an unmetered network. */
object BackupScheduler {
    private const val NAME = "cove-monthly-backup"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonthlyBackupWorker>(30, TimeUnit.DAYS, 2, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
