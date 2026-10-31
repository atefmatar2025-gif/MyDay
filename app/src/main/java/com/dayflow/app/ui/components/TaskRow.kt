package com.dayflow.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dayflow.app.R
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.text.Ar
import java.time.LocalDateTime

/** One task in any list. The check button completes (or reopens) the task. */
@Composable
fun TaskRow(
    task: Task,
    now: LocalDateTime,
    onToggle: (Task) -> Unit,
    onClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val status = task.effectiveStatus(now)
    val done = status == TaskStatus.COMPLETED
    val cancelled = status == TaskStatus.CANCELLED
    val extra = LocalExtraColors.current
    DfCard(modifier = modifier, onClick = { onClick(task) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggle(task) }, enabled = !cancelled) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = stringResource(if (done) R.string.cd_reopen_task else R.string.cd_complete_task, task.title),
                    tint = if (done) extra.success else AppColors.priority(task.priority),
                )
            }
            Column(Modifier.weight(1f).padding(end = Spacing.md)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
                    color = if (done || cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    ColorDot(AppColors.area(task.area), 8.dp)
                    val time = task.startAt ?: task.dueAt
                    if (time != null) {
                        Text(
                            if (showDate) Ar.dateTime(time, now.toLocalDate()) else Ar.time(time.toLocalTime()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == TaskStatus.OVERDUE) extra.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    task.estimatedMinutes?.let {
                        Text("• ${Ar.duration(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.recurrence != null) Icon(Icons.Filled.Repeat, stringResource(R.string.recurring), Modifier.padding(0.dp).width(14.dp))
                }
                if (status == TaskStatus.OVERDUE || status == TaskStatus.IN_PROGRESS || status == TaskStatus.POSTPONED || task.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        when (status) {
                            TaskStatus.OVERDUE -> SmallChip(Ar.status(status), extra.danger)
                            TaskStatus.IN_PROGRESS -> SmallChip(Ar.status(status), MaterialTheme.colorScheme.secondary)
                            TaskStatus.POSTPONED -> SmallChip(Ar.status(status), MaterialTheme.colorScheme.outline)
                            else -> Unit
                        }
                        task.tags.take(3).forEach { SmallChip("#$it") }
                    }
                }
            }
            if (task.priority.weight >= 3 && !done) {
                PriorityBadge(task.priority)
                Spacer(Modifier.width(Spacing.sm))
            }
        }
    }
}
