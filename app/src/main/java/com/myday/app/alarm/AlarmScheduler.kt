package com.myday.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.myday.app.MainActivity
import com.myday.app.data.TaskEntity
import com.myday.app.util.TaskTime

/** حساب أوقات المنبه والتذكير — دوال نقية قابلة للاختبار. */
object AlarmTimes {

    /** وقت المنبه الرئيسي القادم، أو null إن لم يكن هناك منبه مستحق. */
    fun nextAlarm(task: TaskEntity, now: Long): Long? {
        if (task.isCompleted || !task.alarmEnabled) return null
        task.snoozedUntil?.let { if (it > now) return it }
        return TaskTime.firstAfter(task, now)
    }

    /** وقت التذكير المسبق القادم (trigger) والموعد الذي يخصه (occurrence). */
    fun nextReminder(task: TaskEntity, now: Long): Pair<Long, Long>? {
        if (task.isCompleted || task.reminderMinutesBefore <= 0) return null
        val offset = task.reminderMinutesBefore * 60_000L
        // أول موعد يكون تذكيره بعد الآن
        val occurrence = TaskTime.firstAfter(task, now + offset) ?: return null
        return (occurrence - offset) to occurrence
    }
}

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(task: TaskEntity, now: Long = System.currentTimeMillis()) {
        val alarmAt = AlarmTimes.nextAlarm(task, now)
        if (alarmAt != null) setMain(task.id, alarmAt) else cancelMain(task.id)

        val reminder = AlarmTimes.nextReminder(task, now)
        if (reminder != null) setReminder(task.id, reminder.first, reminder.second) else cancelReminder(task.id)
    }

    fun cancel(taskId: Long) {
        cancelMain(taskId)
        cancelReminder(taskId)
    }

    // -------- المنبه الرئيسي: setAlarmClock هو الأدق ويتجاوز وضع Doze --------

    private fun setMain(taskId: Long, at: Long) {
        val pi = firePendingIntent(taskId, KIND_MAIN, at)
        try {
            if (canScheduleExact()) {
                val info = AlarmManager.AlarmClockInfo(at, showPendingIntent(taskId))
                alarmManager.setAlarmClock(info, pi)
            } else {
                // بديل عند رفض صلاحية المنبهات الدقيقة (قد يتأخر بضع دقائق)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun setReminder(taskId: Long, at: Long, occurrence: Long) {
        val pi = firePendingIntent(taskId, KIND_REMINDER, occurrence)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelMain(taskId: Long) = cancelKind(taskId, KIND_MAIN)
    private fun cancelReminder(taskId: Long) = cancelKind(taskId, KIND_REMINDER)

    private fun cancelKind(taskId: Long, kind: Int) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode(taskId, kind), baseIntent(taskId, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /** النية الأساسية: الـ data URI يجعلها فريدة لكل (مهمة، نوع) فلا تتكرر المنبهات أبداً. */
    private fun baseIntent(taskId: Long, kind: Int): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("myday://alarm/$kind/$taskId"))

    private fun firePendingIntent(taskId: Long, kind: Int, at: Long): PendingIntent {
        val intent = baseIntent(taskId, kind)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            context, requestCode(taskId, kind), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPendingIntent(taskId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode(taskId, 9),
            MainActivity.openTaskIntent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(taskId: Long, kind: Int): Int = (taskId * 10 + kind).toInt()

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE = "com.myday.app.action.FIRE_ALARM"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_AT = "at"
        const val KIND_MAIN = 1
        const val KIND_REMINDER = 2
    }
}
