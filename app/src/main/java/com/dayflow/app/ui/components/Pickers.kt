package com.dayflow.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dayflow.app.R
import com.dayflow.core.text.Ar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogDf(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogDf(initial: LocalTime?, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val t = initial ?: LocalTime.now().plusHours(1).withMinute(0)
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Date + time selector used by the task editor. [value] null = not set. */
@Composable
fun DateTimeField(
    label: String,
    value: LocalDateTime?,
    onChange: (LocalDateTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        AssistChip(
            onClick = { pickDate = true },
            label = { Text(value?.toLocalDate()?.let { if (it == today) stringResource(R.string.today) else Ar.shortDate(it) } ?: stringResource(R.string.set_date)) },
            leadingIcon = { Icon(Icons.Filled.Event, null) },
        )
        AssistChip(
            onClick = { pickTime = true },
            label = { Text(value?.let { Ar.time(it.toLocalTime()) } ?: stringResource(R.string.set_time)) },
            leadingIcon = { Icon(Icons.Filled.Schedule, null) },
        )
        if (value != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear)) }
        }
    }
    if (pickDate) {
        DatePickerDialogDf(value?.toLocalDate(), { pickDate = false }) { d ->
            onChange(d.atTime(value?.toLocalTime() ?: LocalTime.of(9, 0)))
        }
    }
    if (pickTime) {
        TimePickerDialogDf(value?.toLocalTime(), { pickTime = false }) { t ->
            onChange((value?.toLocalDate() ?: today).atTime(t))
        }
    }
}

