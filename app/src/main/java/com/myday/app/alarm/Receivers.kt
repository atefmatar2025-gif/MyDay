package com.myday.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.myday.app.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** تشغيل عمل غير متزامن داخل BroadcastReceiver بأمان (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("MyDayReceiver", "Receiver work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** يستقبل المنبه من AlarmManager في الوقت المحدد حتى لو كان التطبيق مغلقاً. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val kind = intent.getIntExtra(AlarmScheduler.EXTRA_KIND, AlarmScheduler.KIND_MAIN)
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis())
        val app = context.applicationContext

        if (kind == AlarmScheduler.KIND_MAIN) {
            // نبدأ الخدمة فوراً داخل onReceive (ضمن استثناء المنبه الدقيق لبدء الخدمات الأمامية)
            AlarmService.start(app, taskId)
            runAsync { app.container.repository.afterAlarmFired(taskId, at) }
        } else {
            runAsync {
                val repo = app.container.repository
                val task = repo.getTask(taskId) ?: return@runAsync
                if (!task.isCompleted) NotificationHelper.showReminder(app, task, at)
                repo.rescheduleTask(taskId, maxOf(System.currentTimeMillis(), at - task.reminderMinutesBefore * 60_000L) + 1_000)
            }
        }
    }
}

/** أزرار الإشعار: إيقاف / غفوة / تمّت. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val app = context.applicationContext
        val action = intent.action
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 5)
        // إيقاف الصوت فوراً قبل أي عمل على قاعدة البيانات
        val ringing = AlarmState.ringing.value
        if (ringing == null || ringing.taskId == taskId) AlarmService.stop(app)
        runAsync {
            val repo = app.container.repository
            when (action) {
                ACTION_STOP -> repo.stopRinging(taskId)
                ACTION_SNOOZE -> repo.snooze(taskId, minutes)
                ACTION_COMPLETE -> repo.complete(taskId)
            }
            NotificationHelper.cancelForTask(app, taskId)
        }
    }

    companion object {
        const val ACTION_STOP = "com.myday.app.action.STOP"
        const val ACTION_SNOOZE = "com.myday.app.action.SNOOZE"
        const val ACTION_COMPLETE = "com.myday.app.action.COMPLETE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** إعادة جدولة كل المنبهات بعد إعادة تشغيل الهاتف أو تغيير الوقت/المنطقة أو تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> runAsync {
                app.container.repository.rescheduleAll()
                AlarmSyncWorker.enqueue(app)
            }
        }
    }
}
