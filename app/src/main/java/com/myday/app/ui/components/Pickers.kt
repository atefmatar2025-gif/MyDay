@file:OptIn(ExperimentalMaterial3Api::class)

package com.myday.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myday.app.data.TaskEntity
import com.myday.app.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun DatePickerModal(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

@Composable
fun TimePickerModal(
    initial: LocalTime,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, use24h)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("اختر الوقت", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 16.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("تم") }
                }
            }
        }
    }
}

/** قائمة تأجيل المهمة مع خيارات سريعة وموعد مخصص. */
@Composable
fun PostponeSheet(
    task: TaskEntity,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onPostpone: (newDueAt: Long, label: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }

    val now = System.currentTimeMillis()
    val base = maxOf(now, task.dueAt)
    val due = Fmt.toLdt(task.dueAt)
    val today = LocalDate.now()

    val options = listOf(
        Triple("15 دقيقة", Icons.Rounded.Update, base + 15 * 60_000L),
        Triple("ساعة واحدة", Icons.Rounded.Update, base + 60 * 60_000L),
        Triple("3 ساعات", Icons.Rounded.Schedule, base + 3 * 60 * 60_000L),
        Triple("غداً في نفس الوقت", Icons.Rounded.Today, Fmt.toMillis(LocalDateTime.of(today.plusDays(1), due.toLocalTime()))),
        Triple("الأسبوع القادم", Icons.Rounded.CalendarMonth, Fmt.toMillis(LocalDateTime.of(today.plusWeeks(1), due.toLocalTime()))),
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "تأجيل \"${task.title}\"", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            options.forEach { (label, icon, at) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(Fmt.dateTime(at, use24h)) },
                    leadingContent = { Icon(icon, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.padding(horizontal = 12.dp)
                        .clickable { onPostpone(at, "إلى ${Fmt.dateTime(at, use24h)}"); onDismiss() }
                )
            }
            ListItem(
                headlineContent = { Text("اختيار موعد آخر…") },
                leadingContent = { Icon(Icons.Rounded.CalendarMonth, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.padding(horizontal = 12.dp).clickable { pickDate = true }
            )
        }
    }

    if (pickDate) {
        DatePickerModal(initial = maxOf(today, due.toLocalDate()), onDismiss = { pickDate = false }) { d -> pickTime = d }
    }
    pickTime?.let { date ->
        TimePickerModal(initial = due.toLocalTime(), use24h = use24h, onDismiss = { pickTime = null }) { t ->
            val at = Fmt.toMillis(LocalDateTime.of(date, t))
            onPostpone(at, "إلى ${Fmt.dateTime(at, use24h)}")
            onDismiss()
        }
    }
}

