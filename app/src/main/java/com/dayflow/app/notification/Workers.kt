package com.dayflow.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.data.db.UserSettingsEntity
import com.dayflow.app.widget.DayflowWidgetProvider
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.text.Ar
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Morning summary notification at the time chosen in settings (inexact by design). */
class DailySummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer()
        val settings = c.settingsRepository.current()
        if (settings.dailySummaryEnabled) {
            val ctx = c.assistantService.snapshot()
            val today = ctx.now.toLocalDate()
            val todays = TaskOccurrences.forDate(ctx.tasks, today).filter { it.status.isOpen }
            val overdue = ctx.tasks.filter { it.recurrence == null && it.isOverdue(ctx.now) && !it.archived }
            val habitsDue = ctx.habits.count { !it.archived && it.isScheduledOn(today) }
            val first = todays.filter { it.startAt != null }.minByOrNull { it.startAt!! }
            val text = buildString {
                append(applicationContext.getString(R.string.summary_tasks, Ar.tasks(todays.size)))
                if (overdue.isNotEmpty()) append(" • ").append(applicationContext.getString(R.string.summary_overdue, overdue.size))
                if (habitsDue > 0) append(" • ").append(applicationContext.getString(R.string.summary_habits, habitsDue))
                first?.let { append("\n").append(applicationContext.getString(R.string.summary_first, Ar.time(it.startAt!!.toLocalTime()), it.title)) }
            }
            Notifier.simple(applicationContext, Channels.SUMMARY, Notifier.ID_SUMMARY, applicationContext.getString(R.string.summary_title), text)
        }
        schedule(applicationContext, settings)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_summary"

        fun schedule(context: Context, settings: UserSettingsEntity) {
            val wm = WorkManager.getInstance(context)
            if (!settings.dailySummaryEnabled) {
                wm.cancelUniqueWork(NAME); return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atStartOfDay().plusMinutes(settings.dailySummaryMinute.toLong())
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            wm.enqueueUniqueWork(
                NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DailySummaryWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        }
    }
}

/**
 * Safety net: every 6 hours re-registers alarms (some OEM "battery savers" silently drop alarms)
 * and refreshes the widget. Alarms are idempotent, so this never creates duplicates.
 */
class MaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer()
        c.reminderScheduler.rescheduleAll()
        c.focusTimer.restoreAlarm()
        DayflowWidgetProvider.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "maintenance", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
