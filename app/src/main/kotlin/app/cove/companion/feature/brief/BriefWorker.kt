package app.cove.companion.feature.brief

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cove.companion.container
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Generates and caches today's brief ahead of the wake-up time. Never uses Nano (foreground only). */
class BriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (!c.settings.settings.first().briefOn) return Result.success()
        return runCatching { c.briefGenerator.generate() }.fold({ Result.success() }, { Result.retry() })
    }
}

/** Keeps the daily [BriefWorker] scheduled about [LEAD_MINUTES] before the wake time while the brief is on. */
object BriefScheduler {
    const val LEAD_MINUTES = 15
    private const val NAME = "daily-brief"

    /** Milliseconds from [nowMillis] until the next occurrence of ([wakeMinutes] - lead) in [zone]. */
    fun initialDelayMillis(nowMillis: Long, wakeMinutes: Int, zone: ZoneId = ZoneId.systemDefault(), lead: Int = LEAD_MINUTES): Long {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val target = Math.floorMod(wakeMinutes - lead, 24 * 60)
        var next = now.toLocalDate().atStartOfDay().plusMinutes(target.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        return java.time.Duration.between(now, next).toMillis()
    }

    /** Applies the current settings: schedules, reschedules or cancels the periodic work. */
    fun apply(context: Context, briefOn: Boolean, wakeMinutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (!briefOn) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BriefWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(System.currentTimeMillis(), wakeMinutes), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
