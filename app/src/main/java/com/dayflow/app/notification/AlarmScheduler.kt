package com.dayflow.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dayflow.app.MainActivity
import com.dayflow.core.time.AlarmPlanner

/**
 * Thin wrapper around [AlarmManager]. It never assumes exact alarms are allowed:
 *  - exact permission granted: ringing alarms use setAlarmClock() (shown in the status bar,
 *    exempt from Doze), notification reminders use setExactAndAllowWhileIdle();
 *  - permission denied: setAndAllowWhileIdle() - the system may deliver it late (typically
 *    minutes, more in deep Doze). The UI explains this difference to the user.
 */
class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** @return true when the alarm was registered as exact. */
    fun scheduleReminder(reminderId: Long, triggerAtMillis: Long, ringing: Boolean, snooze: Boolean, taskId: Long): Boolean {
        val pi = reminderPendingIntent(reminderId, triggerAtMillis, snooze, PendingIntent.FLAG_UPDATE_CURRENT)!!
        return setAlarm(triggerAtMillis, pi, ringing, showIntent = Notifier.openAppIntent(context, IntentKeys.DEST_TASK, taskId, requestCode = -1))
    }

    fun cancelReminder(reminderId: Long) {
        listOf(false, true).forEach { snooze ->
            reminderPendingIntent(reminderId, 0, snooze, PendingIntent.FLAG_NO_CREATE)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
    }

    fun isReminderScheduled(reminderId: Long, snooze: Boolean = false): Boolean =
        reminderPendingIntent(reminderId, 0, snooze, PendingIntent.FLAG_NO_CREATE) != null

    fun scheduleHabit(habitId: Long, triggerAtMillis: Long): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(IntentKeys.ACTION_FIRE_HABIT)
            .putExtra(IntentKeys.EXTRA_HABIT_ID, habitId)
        val pi = PendingIntent.getBroadcast(context, habitRequestCode(habitId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return setAlarm(triggerAtMillis, pi, ringing = false, showIntent = null)
    }

    fun cancelHabit(habitId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(IntentKeys.ACTION_FIRE_HABIT)
        PendingIntent.getBroadcast(context, habitRequestCode(habitId), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            am.cancel(it); it.cancel()
        }
    }

    fun scheduleFocusEnd(triggerAtMillis: Long): Boolean {
        val pi = focusPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!
        return setAlarm(triggerAtMillis, pi, ringing = false, showIntent = null)
    }

    fun cancelFocusEnd() {
        focusPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    private fun setAlarm(triggerAtMillis: Long, pi: PendingIntent, ringing: Boolean, showIntent: PendingIntent?): Boolean {
        return try {
            if (canScheduleExact()) {
                if (ringing) {
                    val show = showIntent ?: PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                true
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                false
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            false
        }
    }

    private fun reminderPendingIntent(reminderId: Long, scheduledFor: Long, snooze: Boolean, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(IntentKeys.ACTION_FIRE_REMINDER)
            .putExtra(IntentKeys.EXTRA_REMINDER_ID, reminderId)
            .putExtra(IntentKeys.EXTRA_SCHEDULED_FOR, scheduledFor)
            .putExtra(IntentKeys.EXTRA_IS_SNOOZE, snooze)
        return PendingIntent.getBroadcast(context, AlarmPlanner.requestCode(reminderId, snooze), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun focusPendingIntent(flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(IntentKeys.ACTION_FOCUS_END)
        return PendingIntent.getBroadcast(context, FOCUS_REQUEST_CODE, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val FOCUS_REQUEST_CODE = 2_000_000_001
        fun habitRequestCode(habitId: Long) = 1_500_000_000 + (habitId % 100_000_000L).toInt()
    }
}
