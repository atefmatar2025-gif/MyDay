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
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s != null) {
                MyDayTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                    AlarmScreen(taskId = taskId, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun AlarmScreen(taskId: Long, onClose: () -> Unit) {
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
                    Box(
                        Modifier.size(140.dp).scale(scale).background(cs.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(100.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AlarmOn, null, tint = cs.onPrimary, modifier = Modifier.size(52.dp))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
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
                }

                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
