package com.myday.app.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.myday.app.MainActivity
import com.myday.app.R
import com.myday.app.data.TaskEntity
import com.myday.app.data.TaskAction
import com.myday.app.data.action
import com.myday.app.util.Fmt

object NotificationHelper {

    const val CHANNEL_ALARM = "alarm_ring_v1"
    const val CHANNEL_ALARM_FALLBACK = "alarm_fallback_v1"
    const val CHANNEL_REMINDER = "reminder_v1"

    const val RINGING_NOTIFICATION_ID = 1001
    private const val REMINDER_BASE = 2_000_000
    private const val MISSED_BASE = 3_000_000
    private const val FALLBACK_BASE = 4_000_000
    private const val ACTION_BASE = 5_000_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // قناة الرنين: الصوت تشغله خدمة المنبه نفسها (لتحكم كامل بالمدة والتكرار)
        val alarm = NotificationChannel(
            CHANNEL_ALARM, context.getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // قناة احتياطية بصوت منبه — تُستخدم فقط إذا منع النظام تشغيل الخدمة
        val fallback = NotificationChannel(
            CHANNEL_ALARM_FALLBACK, "منبه احتياطي", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "يُستخدم إذا تعذر تشغيل شاشة المنبه"
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(
                uri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val reminder = NotificationChannel(
            CHANNEL_REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_reminder_desc)
            enableVibration(true)
        }

        nm.createNotificationChannels(listOf(alarm, fallback, reminder))
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun timeText(context: Context, millis: Long) =
        Fmt.time(millis, DateFormat.is24HourFormat(context))

    private fun openTaskPending(context: Context, taskId: Long, rc: Int): PendingIntent =
        PendingIntent.getActivity(
            context, rc, MainActivity.openTaskIntent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun actionPending(context: Context, action: String, taskId: Long, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java)
            .setAction(action)
            .setData(android.net.Uri.parse("myday://action/$action/$taskId/$minutes"))
            .putExtra(AlarmActionReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmActionReceiver.EXTRA_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context, (taskId * 31 + action.hashCode() + minutes).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** إشعار مؤقت فوري — يلزم لبدء الخدمة الأمامية خلال المهلة المسموحة. */
    fun buildRingingPlaceholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("منبه يومي")
            .setContentText("جارٍ التحضير…")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun buildRingingNotification(
        context: Context,
        task: TaskEntity,
        autoSendPending: Boolean = false,
        showOnLock: Boolean = true,
        quickStop: Boolean = true,
    ): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, (task.id * 10 + 5).toInt(), AlarmActivity.intent(context, task.id, showOnLock = showOnLock),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = task.action
        val text = buildString {
            append("الموعد: ").append(timeText(context, task.dueAt))
            if (task.description.isNotBlank()) append(" — ").append(task.description)
            if (action != null) {
                append("\n")
                when (action) {
                    TaskAction.SMS -> append(if (autoSendPending) "ستُرسل خلال 30 ثانية إلى " else "رسالة إلى ")
                    TaskAction.WHATSAPP -> append("واتساب إلى ")
                    TaskAction.CALL -> append("اتصال بـ ")
                }
                append(task.actionContactName ?: task.actionPhone)
                if (!task.actionMessage.isNullOrBlank() && action != TaskAction.CALL) append(": «").append(task.actionMessage).append("»")
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("⏰ ${task.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(fullScreen)
            .apply { if (showOnLock) setFullScreenIntent(fullScreen, true) }
            .apply {
                if (task.proofCode != null) {
                    // لا يتوقف إلا بمسح الرمز من شاشة المنبه
                    addAction(0, "امسح الرمز للإيقاف", fullScreen)
                    addAction(0, "غفوة 5 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 5))
                } else if (action == TaskAction.SMS) {
                    addAction(0, "أرسل الآن", actionPending(context, AlarmActionReceiver.ACTION_SEND_NOW, task.id))
                    if (autoSendPending) addAction(0, "إلغاء الإرسال", actionPending(context, AlarmActionReceiver.ACTION_CANCEL_SEND, task.id))
                    else addAction(0, "إيقاف", actionPending(context, AlarmActionReceiver.ACTION_STOP, task.id))
                    addAction(0, "غفوة 5 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 5))
                } else if (action == TaskAction.WHATSAPP || action == TaskAction.CALL) {
                    val perform = PendingIntent.getActivity(
                        context, (task.id * 10 + 2).toInt(), AlarmActivity.intent(context, task.id, perform = true),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    addAction(0, if (action == TaskAction.CALL) "اتصل الآن" else "افتح واتساب", perform)
                    addAction(0, "إيقاف", actionPending(context, AlarmActionReceiver.ACTION_STOP, task.id))
                    addAction(0, "غفوة 5 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 5))
                } else if (task.isCare) {
                    // الرعاية: لا إيقاف دون تأكيد
                    addAction(0, "✓ تمّ", actionPending(context, AlarmActionReceiver.ACTION_COMPLETE, task.id))
                    addAction(0, "ذكّرني بعد 10 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 10))
                } else if (!quickStop) {
                    // طريقة الإيقاف تتطلب تحدياً: الإيقاف من شاشة المنبه فقط
                    addAction(0, "إيقاف…", fullScreen)
                    addAction(0, "غفوة 5 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 5))
                } else {
                    addAction(0, "إيقاف", actionPending(context, AlarmActionReceiver.ACTION_STOP, task.id))
                    addAction(0, "غفوة 5 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 5))
                    addAction(0, "تمّت", actionPending(context, AlarmActionReceiver.ACTION_COMPLETE, task.id))
                }
            }
            .build()
    }

    /** يُستخدم عندما يمنع النظام بدء خدمة الرنين (مثلاً عند رفض المنبهات الدقيقة). */
    fun showFallbackAlarm(context: Context, task: TaskEntity) {
        if (!canPost(context)) return
        val fullScreen = PendingIntent.getActivity(
            context, (task.id * 10 + 6).toInt(), AlarmActivity.intent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ALARM_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("⏰ ${task.title}")
            .setContentText("حان موعد المهمة — ${timeText(context, task.dueAt)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openTaskPending(context, task.id, (task.id * 10 + 7).toInt()))
            .setFullScreenIntent(fullScreen, true)
            .setAutoCancel(true)
            .addAction(0, "غفوة 10 د", actionPending(context, AlarmActionReceiver.ACTION_SNOOZE, task.id, 10))
            .addAction(0, "تمّت", actionPending(context, AlarmActionReceiver.ACTION_COMPLETE, task.id))
            .build()
        notifySafe(context, FALLBACK_BASE + task.id.toInt(), n)
    }

    fun showReminder(context: Context, task: TaskEntity, occurrence: Long) {
        if (!canPost(context)) return
        val minutes = ((occurrence - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        val text = if (minutes > 0) "بعد ${Fmt.minutesLabel(minutes.toInt() + 1)} — ${timeText(context, occurrence)}"
        else "الموعد: ${timeText(context, occurrence)}"
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("تذكير: ${task.title}")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openTaskPending(context, task.id, (task.id * 10 + 8).toInt()))
            .setAutoCancel(true)
            .addAction(0, "تمّت", actionPending(context, AlarmActionReceiver.ACTION_COMPLETE, task.id))
            .build()
        notifySafe(context, REMINDER_BASE + task.id.toInt(), n)
    }

    fun showMissed(context: Context, task: TaskEntity, informed: List<String> = emptyList()) {
        if (!canPost(context)) return
        val text = if (informed.isNotEmpty()) "لم تُؤكَّد في موعدها ${timeText(context, task.dueAt)}. أُبلغ: ${informed.joinToString("، ")}"
        else "كان موعدها ${timeText(context, task.dueAt)}. اضغط للتفاصيل."
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("فاتك منبه: ${task.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openTaskPending(context, task.id, (task.id * 10 + 4).toInt()))
            .setAutoCancel(true)
            .addAction(0, "تمّت", actionPending(context, AlarmActionReceiver.ACTION_COMPLETE, task.id))
            .build()
        notifySafe(context, MISSED_BASE + task.id.toInt(), n)
    }

    fun showActionResult(context: Context, task: TaskEntity, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSilent(true)
            .setContentIntent(openTaskPending(context, task.id, (task.id * 10 + 3).toInt()))
            .setAutoCancel(true)
            .build()
        notifySafe(context, ACTION_BASE + task.id.toInt(), n)
    }

    fun cancelForTask(context: Context, taskId: Long) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(REMINDER_BASE + taskId.toInt())
        nm.cancel(MISSED_BASE + taskId.toInt())
        nm.cancel(FALLBACK_BASE + taskId.toInt())
    }

    @Suppress("MissingPermission")
    private fun notifySafe(context: Context, id: Int, n: Notification) {
        try {
            if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // تم سحب الصلاحية — لا شيء نفعله
        }
    }
}
