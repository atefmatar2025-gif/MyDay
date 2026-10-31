package com.myday.app.ui.components

import com.myday.app.data.isRecurring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myday.app.data.CategoryEntity
import com.myday.app.data.TaskEntity
import com.myday.app.ui.theme.PriorityColors
import com.myday.app.util.Fmt

@Composable
fun CompleteToggle(done: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (done) color else Color.Transparent, label = "chk")
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (done) "إلغاء الإنجاز" else "تحديد كمنجزة" },
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    category: CategoryEntity?,
    now: Long,
    use24h: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val overdue = !task.isCompleted && task.dueAt < now
    val pColor = PriorityColors.of(task.priority, dark)
    val contentAlpha by animateFloatAsState(if (task.isCompleted) 0.55f else 1f, label = "alpha")
    val cs = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // شريط الأولوية
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(4.dp)).background(pColor))
            Spacer(Modifier.width(12.dp))
            CompleteToggle(task.isCompleted, cs.primary, onToggleComplete)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).alpha(contentAlpha)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description, style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val timeText = if (showDate) Fmt.dateTime(task.dueAt, use24h) else Fmt.time(task.dueAt, use24h)
                    MetaChip(Icons.Rounded.Schedule, if (overdue) "متأخرة • $timeText" else timeText, if (overdue) cs.error else cs.primary)
                    if (category != null) {
                        val cc = category.color()
                        MetaChip(CategoryIcons.get(category.iconKey), category.name, cc)
                    }
                    if (task.isRecurring) MetaChip(Icons.Rounded.Repeat, task.repeatType.label, cs.tertiary)
                    if (task.alarmEnabled && !task.isCompleted) MetaChip(Icons.Rounded.Alarm, "منبه", cs.secondary)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (task.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (task.isFavorite) "إزالة من المهمة" else "إضافة للمهمة",
                    tint = if (task.isFavorite) Color(0xFFF59E0B) else cs.onSurfaceVariant
                )
            }
        }
    }
}
