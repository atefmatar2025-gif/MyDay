package com.myday.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.container
import com.myday.app.data.TaskEntity
import com.myday.app.data.TaskAction
import com.myday.app.data.action
import android.app.Activity
import android.app.KeyguardManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import com.myday.app.ui.theme.MyDayTheme
import com.myday.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** شاشة المنبه بملء الشاشة — تظهر فوق شاشة القفل وتشغّل الشاشة. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val perform = intent.getBooleanExtra(EXTRA_PERFORM, false)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s != null) {
                MyDayTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor, textScale = s.textScale) {
                    AlarmScreen(taskId = taskId, perform = perform, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_PERFORM = "perform"
        fun intent(context: Context, taskId: Long, perform: Boolean = false): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_PERFORM, perform)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun AlarmScreen(taskId: Long, perform: Boolean, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext
    val repo = app.container.repository
    val ringing by AlarmState.ringing.collectAsStateWithLifecycle()
    var seenRinging by remember { mutableStateOf(false) }

    val task by produceState<TaskEntity?>(initialValue = null, taskId) {
        value = withContext(Dispatchers.IO) { repo.getTask(taskId) }
    }

    // إغلاق الشاشة تلقائياً عند توقف الرنين (من الإشعار أو انتهاء المدة)
    LaunchedEffect(ringing) {
        val r = ringing
        if (r != null && r.taskId == taskId) seenRinging = true
        else if (seenRinging) onClose()
        else {
            delay(4_000)
            if (AlarmState.ringing.value?.taskId != taskId) onClose()
        }
    }

    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1_000) }
    }
    val use24 = DateFormat.is24HourFormat(context)

    fun act(block: suspend () -> Unit) {
        AlarmService.stop(app)
        // نطاق على مستوى التطبيق حتى لا يُلغى الحفظ عند إغلاق الشاشة
        app.container.appScope.launch { block() }
        onClose()
    }

    /** فتح واتساب أو شاشة الاتصال (بعد فتح القفل إن لزم) وتعليم المهمة منجزة. */
    fun openExternal(t: TaskEntity) {
        val phone = t.actionPhone ?: return
        val external = if (t.action == TaskAction.CALL) ActionExecutor.dialIntent(phone)
        else ActionExecutor.whatsappIntent(app, phone, t.actionMessage)
        AlarmService.stop(app)
        app.container.appScope.launch { repo.complete(taskId) }
        val activity = context as? Activity
        val km = app.getSystemService(KeyguardManager::class.java)
        fun go() {
            try { context.startActivity(external) } catch (e: Exception) { }
            onClose()
        }
        if (activity != null && km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { go() }
                override fun onDismissCancelled() { onClose() }
                override fun onDismissError() { go() }
            })
        } else go()
    }

    // زر «اتصل الآن / افتح واتساب» من الإشعار
    LaunchedEffect(task, perform) {
        val t = task
        if (perform && t != null && (t.action == TaskAction.CALL || t.action == TaskAction.WHATSAPP)) openExternal(t)
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    val cs = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface, cs.surface)))
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Text(Fmt.time(Fmt.toMillis(now), use24),
                        fontSize = 56.sp, fontWeight = FontWeight.Light, color = cs.onSurface)
                    Text(Fmt.fullDate(now.toLocalDate()), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val actionTask = task?.takeIf { it.action != null }
                    if (actionTask == null) {
                        Box(
                            Modifier.size(140.dp).scale(scale).background(cs.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(100.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AlarmOn, null, tint = cs.onPrimary, modifier = Modifier.size(52.dp))
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                    Text(
                        task?.title ?: ringing?.title ?: "منبه",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    val desc = task?.description ?: ringing?.description ?: ""
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(desc, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    task?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("موعد المهمة: ${Fmt.time(it.dueAt, use24)}", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    }
                    if (actionTask != null) {
                        Spacer(Modifier.height(16.dp))
                        ActionPanel(
                            task = actionTask,
                            onSendNow = { text -> act { repo.executeSms(taskId, text) } },
                            onOpenExternal = { openExternal(actionTask) }
                        )
                    }
                }

                if (task?.isCare == true) {
                    // وضع الرعاية: زر تأكيد كبير وواضح، ولا إيقاف دون تأكيد
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مهمة رعاية — يُرجى التأكيد", style = MaterialTheme.typography.titleMedium, color = cs.error)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { act { repo.complete(taskId) } },
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(36.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("تمّ", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = { act { repo.snooze(taskId, 10) } },
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) {
                            Icon(Icons.Rounded.Snooze, null)
                            Spacer(Modifier.size(8.dp))
                            Text("ذكّرني بعد 10 دقائق", fontSize = 18.sp)
                        }
                    }
                } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("غفوة", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            FilledTonalButton(onClick = { act { repo.snooze(taskId, m) } }) {
                                Icon(Icons.Rounded.Snooze, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("$m د")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { act { repo.stopRinging(taskId) } },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)
                    ) { Text("إيقاف المنبه", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { act { repo.complete(taskId) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.size(8.dp))
                        Text("إيقاف وتحديد المهمة كمنجزة")
                    }
                }
            }
        }
    }
}

/** لوحة «المهمة التي تُنجز نفسها»: رسالة جاهزة مع عدّاد إرسال تلقائي، أو واتساب/اتصال بلمسة. */
@Composable
private fun ActionPanel(task: TaskEntity, onSendNow: (String) -> Unit, onOpenExternal: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val action = task.action ?: return
    val auto by AlarmState.autoSend.collectAsStateWithLifecycle()
    var text by remember(task.id) { mutableStateOf(task.actionMessage.orEmpty()) }
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(250) }
    }
    val who = task.actionContactName ?: task.actionPhone.orEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when (action) {
                TaskAction.SMS -> "رسالة إلى $who"
                TaskAction.WHATSAPP -> "واتساب إلى $who"
                TaskAction.CALL -> "اتصال بـ $who"
            },
            style = MaterialTheme.typography.titleMedium
        )
        when (action) {
            TaskAction.SMS -> {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; AlarmState.cancelAutoSend() },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                val info = auto
                if (info != null && info.taskId == task.id) {
                    val left = ((info.sendAt - nowMs) / 1000).coerceAtLeast(0)
                    Spacer(Modifier.height(6.dp))
                    Text("تُرسل تلقائياً خلال $left ثانية", style = MaterialTheme.typography.titleSmall, color = cs.primary)
                    TextButton(onClick = { AlarmState.cancelAutoSend() }) { Text("إلغاء الإرسال التلقائي", color = cs.error) }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { onSendNow(text) }, enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Rounded.Send, null); Spacer(Modifier.size(8.dp))
                    Text("أرسل الآن", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            TaskAction.WHATSAPP, TaskAction.CALL -> {
                if (action == TaskAction.WHATSAPP && !task.actionMessage.isNullOrBlank()) {
                    Text("«${task.actionMessage}»", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                } else Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(if (action == TaskAction.CALL) Icons.Rounded.Call else Icons.Rounded.Chat, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (action == TaskAction.CALL) "اتصل الآن" else "افتح واتساب وأرسل", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
