package app.cove.companion.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cove.companion.container
import java.util.concurrent.TimeUnit

/** Uploads pending journal media to Drive; retried with exponential backoff while Drive is unreachable. */
class MediaUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kit = applicationContext.container.driveKit
        if (!kit.enabled) return Result.success()
        val run = kit.uploader().run()
        return if (run.retryLater && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}

/** Schedules [MediaUploadWorker]: right after media is saved and every 6 hours. */
object MediaUploadScheduler {
    private const val PERIODIC = "cove-media-upload-periodic"
    private const val NOW = "cove-media-upload-now"

    /** Network the uploads need: unmetered when the user chose Wi-Fi only, any connection otherwise. */
    fun networkFor(wifiOnly: Boolean) = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder().setRequiredNetworkType(networkFor(wifiOnly)).build()

    /** Starts (or updates, when [wifiOnly] changed) the 6-hourly pass. */
    fun schedulePeriodic(context: Context, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<MediaUploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Asks for an upload pass as soon as the network allows; repeated calls collapse into the queued one. */
    fun requestNow(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Cancels scheduled passes, used on sign-out. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
