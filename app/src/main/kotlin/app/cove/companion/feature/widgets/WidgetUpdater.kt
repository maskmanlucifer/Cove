package app.cove.companion.feature.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import app.cove.companion.AppContainer
import app.cove.companion.container
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.feature.nudges.NudgeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Redraws the home-screen widgets after any repository write and every 30 minutes. */
object WidgetUpdater {
    private const val WORK_NAME = "widgets_refresh"

    /** Redraws every widget from the database (cheap; no AI). */
    suspend fun refresh(context: Context) {
        NextWidget().updateAll(context)
        SpentWidget().updateAll(context)
        TasksWidget().updateAll(context)
        VoiceWidget().updateAll(context)
    }

    /** Starts the debounced collector and the periodic worker; call once from the Application. */
    @OptIn(FlowPreview::class)
    fun start(context: Context, c: AppContainer, scope: CoroutineScope) {
        val app = context.applicationContext
        val day = c.clock.now().toLocalDate()
        scope.launch {
            combine(
                c.todos.todos,
                c.plan.upcomingEvents(30),
                c.plan.alarms,
                c.money.categories,
                c.money.expenses(day.minusDays(40).startOfDayMillis(), Long.MAX_VALUE / 2),
            ) { _, _, _, _, _ -> }.debounce(1_000).collect { refresh(app) }
        }
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
        )
    }
}

/** Periodic safety net: day rollover, "in 25 min" ageing, and a resync of reminders. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        NudgeScheduler(applicationContext, applicationContext.container).syncNow()
        return Result.success()
    }
}
