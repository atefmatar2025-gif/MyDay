package com.dayflow.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.widget.DayflowWidgetProvider
import com.dayflow.core.text.Ar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Runs suspend work from a receiver while keeping the process alive (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("DAYFLOW", "Receiver failure", e)
        } finally {
            pending.finish()
        }
    }
}

/** Fired by AlarmManager for task reminders, habit reminders and the end of a focus phase. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer()
        runAsync {
            when (intent.action) {
                IntentKeys.ACTION_FIRE_REMINDER -> {
                    val reminderId = intent.getLongExtra(IntentKeys.EXTRA_REMINDER_ID, 0)
                    val decision = c.reminderScheduler.onFired(
                        reminderId,
                        intent.getLongExtra(IntentKeys.EXTRA_SCHEDULED_FOR, 0),
                        intent.getBooleanExtra(IntentKeys.EXTRA_IS_SNOOZE, false),
                    ) ?: return@runAsync
                    val settings = c.settingsRepository.current()
                    val started = decision.ringing && startRinging(context, reminderId)
                    if (!started) {
                        val t = decision.task
                        val text = buildString {
                            t.startAt?.let { append(context.getString(R.string.notif_at_time, Ar.dateTime(it, LocalDate.now()))) }
                            if (t.description.isNotBlank()) { if (isNotEmpty()) append(" — "); append(t.description) }
                        }.ifBlank { context.getString(R.string.notif_reminder_default) }
                        Notifier.taskReminder(context, t.id, reminderId, t.title, text, settings.defaultSnoozeMinutes)
                    }
                    DayflowWidgetProvider.refresh(context)
                }
                IntentKeys.ACTION_FIRE_HABIT -> {
                    val habitId = intent.getLongExtra(IntentKeys.EXTRA_HABIT_ID, 0)
                    val habit = c.habitRepository.habit(habitId)
                    val settings = c.settingsRepository.current()
                    val doneToday = c.habitRepository.logsSnapshot().any { it.habitId == habitId && it.date == LocalDate.now() }
                    if (habit != null && settings.habitRemindersEnabled && !doneToday && !habit.archived) {
                        Notifier.habitReminder(context, habit.id, habit.name)
                    }
                    c.reminderScheduler.rescheduleHabits()
                }
                IntentKeys.ACTION_FOCUS_END -> c.focusTimer.onPhaseFinished()
            }
        }
    }

    private fun startRinging(context: Context, reminderId: Long): Boolean = try {
        val i = Intent(context, AlarmRingingService::class.java)
            .setAction(IntentKeys.ACTION_START_ALARM)
            .putExtra(IntentKeys.EXTRA_REMINDER_ID, reminderId)
        ContextCompat.startForegroundService(context, i)
        true
    } catch (e: Exception) {
        // e.g. ForegroundServiceStartNotAllowedException when the alarm was inexact (no exact permission).
        Log.w("DAYFLOW", "Cannot start ringing service, falling back to notification", e)
        false
    }
}

/** Buttons inside notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer()
        val nid = intent.getIntExtra(IntentKeys.EXTRA_NOTIFICATION_ID, 0)
        if (nid != 0) Notifier.cancel(context, nid)
        runAsync {
            when (intent.action) {
                IntentKeys.ACTION_COMPLETE_TASK -> c.taskInteractor.complete(intent.getLongExtra(IntentKeys.EXTRA_TASK_ID, 0))
                IntentKeys.ACTION_SNOOZE -> c.reminderScheduler.snooze(
                    intent.getLongExtra(IntentKeys.EXTRA_REMINDER_ID, 0),
                    intent.getIntExtra(IntentKeys.EXTRA_MINUTES, 10),
                )
                IntentKeys.ACTION_HABIT_DONE -> c.habitRepository.setDone(intent.getLongExtra(IntentKeys.EXTRA_HABIT_ID, 0), LocalDate.now(), true)
            }
            DayflowWidgetProvider.refresh(context)
        }
    }
}

/**
 * Re-registers every alarm after reboot, app update, manual time/date change, time-zone change
 * (wall-clock times are re-converted with the new zone) and exact-alarm permission changes.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer()
        runAsync {
            c.reminderScheduler.rescheduleAll()
            c.focusTimer.restoreAlarm()
            DailySummaryWorker.schedule(context, c.settingsRepository.current())
            DayflowWidgetProvider.refresh(context)
        }
    }
}
