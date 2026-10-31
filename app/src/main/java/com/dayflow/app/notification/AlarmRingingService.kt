package com.dayflow.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.core.text.Ar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/** What is currently ringing - observed by [AlarmActivity]. */
data class RingingAlarm(
    val reminderId: Long,
    val taskId: Long,
    val title: String,
    val description: String,
    val timeText: String,
    val snoozeMinutes: Int,
)

/**
 * Foreground service that plays the alarm sound and vibration until the user dismisses or
 * snoozes it, or until the configured ring duration ends (then "repeat if unanswered" applies).
 * Justification for a foreground service: sound must continue while the screen is locked and
 * the full-screen alarm UI is shown; it runs only while an alarm is actually ringing.
 */
class AlarmRingingService : Service() {

    companion object {
        private val _ringing = MutableStateFlow<RingingAlarm?>(null)
        val ringing: StateFlow<RingingAlarm?> = _ringing

        fun command(context: Context, action: String, minutes: Int = 0): Intent =
            Intent(context, AlarmRingingService::class.java).setAction(action).putExtra(IntentKeys.EXTRA_MINUTES, minutes)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var current: RingingAlarm? = null
    private val timeout = Runnable { finishWith(IntentKeys.ACTION_ALARM_TIMEOUT, 0) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentKeys.ACTION_START_ALARM -> {
                // Must call startForeground quickly; details are filled in once loaded.
                goForeground(buildNotification(null))
                val reminderId = intent.getLongExtra(IntentKeys.EXTRA_REMINDER_ID, 0)
                current?.let { previous ->
                    // A second alarm arrived while one is ringing: the first counts as unanswered.
                    stopSound()
                    val c = appContainer()
                    scope.launch(Dispatchers.IO) { c.reminderScheduler.unanswered(previous.reminderId) }
                }
                start(reminderId)
            }
            IntentKeys.ACTION_DISMISS_ALARM -> finishWith(IntentKeys.ACTION_DISMISS_ALARM, 0)
            IntentKeys.ACTION_SNOOZE -> finishWith(IntentKeys.ACTION_SNOOZE, intent.getIntExtra(IntentKeys.EXTRA_MINUTES, 10))
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(reminderId: Long) {
        val c = appContainer()
        scope.launch {
            val data = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val r = c.taskRepository.reminder(reminderId)
                val t = r?.let { c.taskRepository.entity(it.taskId) }
                val s = c.settingsRepository.current()
                Triple(r, t, s)
            }
            val (r, t, s) = data
            if (r == null || t == null) { stopEverything(); return@launch }
            val task = c.taskRepository.snapshot().firstOrNull { it.id == t.id }
            val alarm = RingingAlarm(
                reminderId = r.id,
                taskId = t.id,
                title = t.title,
                description = t.description.take(160),
                timeText = task?.startAt?.let { Ar.time(it.toLocalTime()) } ?: Ar.time(LocalTime.now()),
                snoozeMinutes = s.defaultSnoozeMinutes,
            )
            current = alarm
            _ringing.value = alarm
            NotificationManagerCompat.from(this@AlarmRingingService).let {
                if (Notifier.canPost(this@AlarmRingingService)) {
                    @Suppress("MissingPermission")
                    it.notify(Notifier.ID_RINGING, buildNotification(alarm))
                }
            }
            val soundUri = (r.soundUri ?: s.defaultSoundUri)?.let { Uri.parse(it) }
            playSound(soundUri, r.volumePercent.coerceIn(0, 100) * s.alarmVolumePercent.coerceIn(0, 100) / 100)
            if (r.vibrate) vibrate(s.vibrationPattern)
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, r.ringSeconds.coerceIn(10, 600) * 1000L)
        }
    }

    private fun finishWith(action: String, minutes: Int) {
        val alarm = current
        stopSound()
        if (alarm != null) {
            val c = appContainer()
            // Use the application scope: this service is about to stop.
            c.appScope.launch(Dispatchers.IO) {
                when (action) {
                    IntentKeys.ACTION_DISMISS_ALARM -> c.reminderScheduler.dismissed(alarm.reminderId)
                    IntentKeys.ACTION_SNOOZE -> c.reminderScheduler.snooze(alarm.reminderId, minutes)
                    IntentKeys.ACTION_ALARM_TIMEOUT -> c.reminderScheduler.unanswered(alarm.reminderId)
                }
            }
        }
        stopEverything()
    }

    private fun stopEverything() {
        stopSound()
        current = null
        _ringing.value = null
        handler.removeCallbacks(timeout)
        NotificationManagerCompat.from(this).cancel(Notifier.ID_RINGING)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(n: Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
            ServiceCompat.startForeground(this, Notifier.ID_RINGING, n, type)
        } catch (e: Exception) {
            Log.w("DAYFLOW", "startForeground failed", e)
        }
    }

    private fun buildNotification(alarm: RingingAlarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 7,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun svc(action: String, code: Int, minutes: Int = 0) = PendingIntent.getService(
            this, code, command(this, action, minutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = alarm?.snoozeMinutes ?: 10
        val b = NotificationCompat.Builder(this, Channels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_dayflow)
            .setContentTitle(alarm?.title ?: getString(R.string.alarm_ringing))
            .setContentText(alarm?.let { listOf(it.timeText, it.description).filter { s -> s.isNotBlank() }.joinToString(" — ") } ?: "")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.action_stop), svc(IntentKeys.ACTION_DISMISS_ALARM, 8))
            .addAction(0, getString(R.string.action_snooze_minutes, snooze), svc(IntentKeys.ACTION_SNOOZE, 9, snooze))
        // Android 14+: full-screen intents are only allowed when the user/system granted it.
        if (NotificationManagerCompat.from(this).canUseFullScreenIntent()) b.setFullScreenIntent(fullScreen, true)
        return b.build()
    }

    private fun playSound(uri: Uri?, volumePercent: Int) {
        stopSound()
        val candidates = listOfNotNull(
            uri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (u in candidates) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                mp.setDataSource(this, u)
                mp.isLooping = true
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                val v = volumePercent / 100f
                mp.setVolume(v, v) // relative to the alarm volume the user set in Android
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                Log.w("DAYFLOW", "Cannot play $u, trying fallback", e)
            }
        }
    }

    private fun vibrate(pattern: String) {
        val timings = when (pattern) {
            "NONE" -> return
            "SHORT" -> longArrayOf(0, 300, 700)
            "HEARTBEAT" -> longArrayOf(0, 120, 120, 120, 800)
            else -> longArrayOf(0, 900, 600)
        }
        val v = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(timings, 0))
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopSound()
        handler.removeCallbacks(timeout)
        _ringing.value = null
        scope.cancel()
        super.onDestroy()
    }
}
