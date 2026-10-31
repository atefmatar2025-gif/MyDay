package com.dayflow.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dayflow.app.MainActivity
import com.dayflow.app.R

/** Intent actions/extras shared by the notification layer and MainActivity. */
object IntentKeys {
    const val ACTION_FIRE_REMINDER = "com.dayflow.app.action.FIRE_REMINDER"
    const val ACTION_FIRE_HABIT = "com.dayflow.app.action.FIRE_HABIT"
    const val ACTION_FOCUS_END = "com.dayflow.app.action.FOCUS_END"
    const val ACTION_COMPLETE_TASK = "com.dayflow.app.action.COMPLETE_TASK"
    const val ACTION_SNOOZE = "com.dayflow.app.action.SNOOZE"
    const val ACTION_HABIT_DONE = "com.dayflow.app.action.HABIT_DONE"
    const val ACTION_DISMISS_ALARM = "com.dayflow.app.action.DISMISS_ALARM"
    const val ACTION_START_ALARM = "com.dayflow.app.action.START_ALARM"
    const val ACTION_ALARM_TIMEOUT = "com.dayflow.app.action.ALARM_TIMEOUT"

    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_TASK_ID = "taskId"
    const val EXTRA_HABIT_ID = "habitId"
    const val EXTRA_SCHEDULED_FOR = "scheduledFor"
    const val EXTRA_IS_SNOOZE = "isSnooze"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    /** MainActivity deep-link destinations. */
    const val EXTRA_DESTINATION = "destination"
    const val DEST_TASK = "task"
    const val DEST_ADD_TASK = "add_task"
    const val DEST_ASSISTANT = "assistant"
    const val DEST_FOCUS = "focus"
    const val DEST_SAVE_MY_DAY = "save_my_day"
    const val DEST_HABITS = "habits"
}

object Channels {
    const val ALARMS = "alarms"
    const val REMINDERS = "reminders"
    const val HABITS = "habits"
    const val SUMMARY = "daily_summary"
    const val ACHIEVEMENTS = "achievements"
    const val FOCUS = "focus"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        val channels = listOf(
            // Alarm sound is played by AlarmRingingService, so the channel itself is silent.
            NotificationChannel(ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alarms_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            },
            NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_desc)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), alarmAttrs)
                enableVibration(true)
            },
            NotificationChannel(HABITS, context.getString(R.string.channel_habits), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_habits_desc)
            },
            NotificationChannel(SUMMARY, context.getString(R.string.channel_summary), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_summary_desc)
            },
            NotificationChannel(ACHIEVEMENTS, context.getString(R.string.channel_achievements), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_achievements_desc)
            },
            NotificationChannel(FOCUS, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_focus_desc)
            },
        )
        nm.createNotificationChannels(channels)
    }
}

object Notifier {
    const val ID_SUMMARY = 900_001
    const val ID_ACHIEVEMENT = 900_002
    const val ID_FOCUS = 900_003
    const val ID_RINGING = 900_004

    fun taskNotificationId(taskId: Long) = (100_000 + taskId % 400_000).toInt()
    fun habitNotificationId(habitId: Long) = (600_000 + habitId % 200_000).toInt()

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openAppIntent(context: Context, destination: String? = null, taskId: Long = 0, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            destination?.let { putExtra(IntentKeys.EXTRA_DESTINATION, it) }
            if (taskId > 0) putExtra(IntentKeys.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun actionIntent(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission")
    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** Normal (non-ringing) task reminder with Complete / Snooze / Open actions. */
    fun taskReminder(context: Context, taskId: Long, reminderId: Long, title: String, text: String, snoozeMinutes: Int) {
        val nid = taskNotificationId(taskId)
        val b = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dayflow)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, IntentKeys.DEST_TASK, taskId, nid))
            .addAction(0, context.getString(R.string.action_complete), actionIntent(context, IntentKeys.ACTION_COMPLETE_TASK, nid) {
                putExtra(IntentKeys.EXTRA_TASK_ID, taskId); putExtra(IntentKeys.EXTRA_NOTIFICATION_ID, nid)
            })
            .addAction(0, context.getString(R.string.action_snooze_minutes, snoozeMinutes), actionIntent(context, IntentKeys.ACTION_SNOOZE, nid + 1) {
                putExtra(IntentKeys.EXTRA_REMINDER_ID, reminderId); putExtra(IntentKeys.EXTRA_MINUTES, snoozeMinutes)
                putExtra(IntentKeys.EXTRA_NOTIFICATION_ID, nid)
            })
        post(context, nid, b)
    }

    fun habitReminder(context: Context, habitId: Long, name: String) {
        val nid = habitNotificationId(habitId)
        val b = NotificationCompat.Builder(context, Channels.HABITS)
            .setSmallIcon(R.drawable.ic_stat_dayflow)
            .setContentTitle(context.getString(R.string.notif_habit_title, name))
            .setContentText(context.getString(R.string.notif_habit_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, IntentKeys.DEST_HABITS, requestCode = nid))
            .addAction(0, context.getString(R.string.action_mark_done), actionIntent(context, IntentKeys.ACTION_HABIT_DONE, nid) {
                putExtra(IntentKeys.EXTRA_HABIT_ID, habitId); putExtra(IntentKeys.EXTRA_NOTIFICATION_ID, nid)
            })
        post(context, nid, b)
    }

    fun simple(context: Context, channel: String, id: Int, title: String, text: String, destination: String? = null) {
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_dayflow)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, destination, requestCode = id))
        post(context, id, b)
    }
}
