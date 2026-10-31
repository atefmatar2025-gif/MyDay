package com.myday.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.myday.app.container
import com.myday.app.data.autoSendsSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * خدمة أمامية تشغّل صوت المنبه الحقيقي (مسار صوت المنبه USAGE_ALARM)
 * مع الاهتزاز ورفع الصوت تدريجياً، وتنهي الرنين بعد المدة المحددة
 * ثم تعيد الجدولة تلقائياً عند عدم الاستجابة.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timeoutJob: Job? = null
    private var rampJob: Job? = null
    private var currentTaskId: Long? = null
    private var sendJob: Job? = null
    private var cancelWatchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        // يجب استدعاء startForeground فوراً
        startInForeground(NotificationHelper.buildRingingPlaceholder(this))

        if (taskId < 0) {
            finish()
            return START_NOT_STICKY
        }

        val previous = currentTaskId
        if (previous != null && previous != taskId) {
            // منبه جديد قاطع منبهاً يرن: نعامل السابق كعدم استجابة (سيعاد لاحقاً)
            stopPlayback()
            sendJob?.cancel(); cancelWatchJob?.cancel()
            AlarmState.cancelAutoSend()
            val repo = container.repository
            scope.launch(Dispatchers.IO + NonCancellable) { repo.onRingTimeout(previous) }
        }
        currentTaskId = taskId
        scope.launch { beginRinging(taskId) }
        return START_NOT_STICKY
    }

    private suspend fun beginRinging(taskId: Long) {
        val repo = container.repository
        val task = withContext(Dispatchers.IO) { repo.getTask(taskId) }
        if (currentTaskId != taskId) return
        if (task == null || task.isCompleted || !task.alarmEnabled) {
            finish()
            return
        }
        val settings = withContext(Dispatchers.IO) { container.settings.current() }

        AlarmState.set(RingingInfo(task.id, task.title, task.description, task.dueAt))
        val autoSend = task.autoSendsSms
        if (autoSend) AlarmState.setAutoSend(AutoSendInfo(task.id, System.currentTimeMillis() + AUTO_SEND_DELAY_MS))
        startInForeground(NotificationHelper.buildRingingNotification(this, task, autoSendPending = autoSend))

        stopPlayback()
        startSound(task.soundUri ?: settings.defaultSoundUri, settings.gradualVolume)
        if (settings.vibrate) startVibration()

        // مهمة تُرسل نفسها: بعد العدّاد تُرسل الرسالة ما لم يُلغِها المستخدم
        if (autoSend) {
            sendJob?.cancel()
            sendJob = scope.launch {
                delay(AUTO_SEND_DELAY_MS)
                if (AlarmState.autoSend.value?.taskId == taskId && currentTaskId == taskId) {
                    cancelWatchJob?.cancel()
                    AlarmState.cancelAutoSend()
                    stopPlayback()
                    withContext(Dispatchers.IO + NonCancellable) { repo.executeSms(taskId) }
                    if (currentTaskId == taskId) finish()
                }
            }
            // عند الإلغاء: تحديث الإشعار ليظهر زر الإيقاف بدل «إلغاء الإرسال»
            cancelWatchJob?.cancel()
            cancelWatchJob = scope.launch {
                AlarmState.autoSend.collect { info ->
                    if (info == null && currentTaskId == taskId && sendJob?.isActive == true) {
                        sendJob?.cancel()
                        startInForeground(NotificationHelper.buildRingingNotification(this@AlarmService, task, autoSendPending = false))
                    }
                }
            }
        }

        val minDuration = if (autoSend) AUTO_SEND_DELAY_MS + 10_000L else 0L
        val duration = maxOf(task.soundDurationSec.coerceIn(10, 600) * 1000L, minDuration)
        timeoutJob = scope.launch {
            delay(duration)
            onTimeout(taskId)
        }
    }

    private fun onTimeout(taskId: Long) {
        stopPlayback()
        val repo = container.repository
        scope.launch {
            withContext(Dispatchers.IO + NonCancellable) { repo.onRingTimeout(taskId) }
            if (currentTaskId == taskId) finish()
        }
    }

    private fun startInForeground(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NotificationHelper.RINGING_NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    // ---------------- الصوت ----------------

    private fun candidateUris(custom: String?): List<Uri> = buildList {
        custom?.let { runCatching { Uri.parse(it) }.getOrNull()?.let(::add) }
        RingtoneManager.getActualDefaultRingtoneUri(this@AlarmService, RingtoneManager.TYPE_ALARM)?.let(::add)
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
        RingtoneManager.getActualDefaultRingtoneUri(this@AlarmService, RingtoneManager.TYPE_RINGTONE)?.let(::add)
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
    }

    private fun startSound(customUri: String?, gradual: Boolean) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (uri in candidateUris(customUri)) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.prepare()
                if (gradual) mp.setVolume(0.15f, 0.15f)
                mp.start()
                player = mp
                if (gradual) startRamp(mp)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $uri, trying next", e)
                runCatching { mp.release() }
            }
        }
        Log.e(TAG, "No playable alarm sound; relying on vibration")
    }

    private fun startRamp(mp: MediaPlayer) {
        rampJob?.cancel()
        rampJob = scope.launch {
            var v = 0.15f
            while (isActive && v < 1f) {
                delay(1_500)
                v = (v + 0.08f).coerceAtMost(1f)
                runCatching { mp.setVolume(v, v) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vib.hasVibrator()) return
        vibrator = vib
        val pattern = longArrayOf(0, 900, 700)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        runCatching { vib.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs) }
    }

    private fun stopPlayback() {
        timeoutJob?.cancel(); timeoutJob = null
        rampJob?.cancel(); rampJob = null
        player?.let { runCatching { if (it.isPlaying) it.stop() }; runCatching { it.release() } }
        player = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
    }

    private fun finish() {
        currentTaskId = null
        sendJob?.cancel(); cancelWatchJob?.cancel()
        AlarmState.cancelAutoSend()
        stopPlayback()
        AlarmState.set(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        AlarmState.cancelAutoSend()
        AlarmState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_START = "com.myday.app.action.START_RINGING"
        const val EXTRA_TASK_ID = "task_id"
        const val AUTO_SEND_DELAY_MS = 30_000L

        /** يبدأ الرنين؛ وإن منع النظام الخدمة الأمامية نعرض إشعار منبه احتياطي بصوت. */
        fun start(context: Context, taskId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TASK_ID, taskId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "FGS start not allowed, using fallback notification", e)
                val app = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    app.container.repository.getTask(taskId)?.let {
                        NotificationHelper.showFallbackAlarm(app, it)
                    }
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
