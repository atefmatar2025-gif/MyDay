@file:OptIn(ExperimentalMaterial3Api::class)

package com.myday.app.ui.detail

import com.myday.app.data.isRecurring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.container
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.components.CategoryIcons
import com.myday.app.ui.components.ConfirmDialog
import com.myday.app.ui.components.PostponeSheet
import com.myday.app.ui.components.color
import com.myday.app.ui.theme.PriorityColors
import com.myday.app.ui.theme.isAppInDarkTheme
import com.myday.app.util.Fmt
import com.myday.app.util.TaskTime
import androidx.compose.material.icons.rounded.Brightness2
import com.myday.app.alarm.AlarmTimes
import androidx.compose.ui.platform.LocalContext

@Composable
fun TaskDetailScreen(
    taskId: Long,
    vm: AppViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCopy: (Long) -> Unit,
) {
    val context = LocalContext.current
    val taskFlow = remember(taskId) { context.container.repository.observeTask(taskId) }
    val taskState by taskFlow.collectAsStateWithLifecycle(initialValue = null)
    var loadedOnce by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showPostpone by remember { mutableStateOf(false) }
    val dark = isAppInDarkTheme(settings.themeMode)

    // إذا حُذفت المهمة نعود للخلف
    LaunchedEffect(taskState) {
        if (taskState != null) loadedOnce = true
        else if (loadedOnce) onBack()
    }

    val task = taskState
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل المهمة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { vm.toggleFavorite(task) }) {
                            Icon(
                                if (task.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "مهمة",
                                tint = if (task.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onCopy(task.id) }) { Icon(Icons.Rounded.ContentCopy, "نسخ المهمة") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "حذف") }
                    }
                }
            )
        }
    ) { padding ->
        if (task == null) return@Scaffold
        val category = categories.firstOrNull { it.id == task.categoryId }
        val overdue = !task.isCompleted && task.dueAt < now
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // بطاقة العنوان
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(if (task.isCompleted) cs.primaryContainer else if (overdue) cs.errorContainer else cs.surfaceContainerLow)
                    .padding(20.dp)
            ) {
                Text(
                    when {
                        task.isCompleted -> "✓ منجزة"
                        overdue -> "متأخرة"
                        else -> Fmt.countdown(task.dueAt, now)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (overdue) cs.onErrorContainer else cs.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(task.title, style = MaterialTheme.typography.headlineSmall,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(task.description, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoRow(Icons.Rounded.CalendarMonth, "الموعد", Fmt.dateTime(task.dueAt, settings.use24h))
            TaskTime.anchorLabel(task)?.let { label ->
                InfoRow(Icons.Rounded.Brightness2, "مرتبط بالصلاة", if (task.isRecurring) "$label — يتغير يومياً مع وقت الصلاة" else label)
            }
            InfoRow(Icons.Rounded.Flag, "الأولوية", task.priority.label, PriorityColors.of(task.priority, dark))
            InfoRow(
                if (category != null) CategoryIcons.get(category.iconKey) else Icons.Rounded.Category,
                "التصنيف", category?.name ?: "بلا تصنيف", category?.color()
            )
            InfoRow(Icons.Rounded.Repeat, "التكرار", task.repeatType.label)
            if (task.alarmEnabled) {
                val next = AlarmTimes.nextAlarm(task, System.currentTimeMillis())
                InfoRow(Icons.Rounded.Alarm, "المنبه", if (next != null) "سيرن ${Fmt.dateTime(next, settings.use24h)}" else "لا يوجد رنين قادم")
                InfoRow(Icons.Rounded.MusicNote, "النغمة", "${task.soundName ?: settings.defaultSoundName} • ${Fmt.secondsLabel(task.soundDurationSec)}")
                InfoRow(
                    Icons.Rounded.Snooze, "عند عدم الاستجابة",
                    if (task.repeatIfNoResponse) "يعيد الرنين كل ${task.repeatIntervalMin} د حتى ${task.maxRepeats} مرات" else "لا يعيد"
                )
            } else {
                InfoRow(Icons.Rounded.AlarmOff, "المنبه", "معطّل")
            }
            InfoRow(
                Icons.Rounded.NotificationsActive, "تذكير مسبق",
                if (task.reminderMinutesBefore > 0) "قبل ${Fmt.minutesLabel(task.reminderMinutesBefore)}" else "بدون"
            )
            task.completedAt?.let { InfoRow(Icons.Rounded.CheckCircle, "أُنجزت في", Fmt.dateTime(it, settings.use24h)) }

            Spacer(Modifier.height(20.dp))
            if (task.isCompleted) {
                OutlinedButton(onClick = { vm.uncomplete(task) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Undo, null); Spacer(Modifier.width(8.dp)); Text("إلغاء الإنجاز")
                }
            } else {
                Button(onClick = { vm.complete(task) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp))
                    Text(if (task.isRecurring) "إنجاز هذا الموعد" else "تحديد كمنجزة")
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { showPostpone = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.Snooze, null); Spacer(Modifier.width(6.dp)); Text("تأجيل")
                    }
                    FilledTonalButton(onClick = { onEdit(task.id) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text("تعديل")
                    }
                }
            }
            if (task.isCompleted) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { onEdit(task.id) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text("تعديل")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && task != null) {
        ConfirmDialog(
            title = "حذف المهمة؟",
            text = "سيتم حذف \"${task.title}\" وإلغاء منبهاتها.",
            confirmLabel = "حذف", destructive = true,
            onConfirm = { vm.delete(task) },
            onDismiss = { confirmDelete = false }
        )
    }
    if (showPostpone && task != null) {
        PostponeSheet(task, settings.use24h, onDismiss = { showPostpone = false }) { at, label -> vm.postpone(task, at, label) }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, tint: Color? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background((tint ?: cs.primary).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint ?: cs.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

