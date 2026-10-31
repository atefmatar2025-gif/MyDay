package com.myday.app.ui.home

import com.myday.app.data.isRecurring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.DayEntry
import com.myday.app.ui.TaskQueries
import com.myday.app.ui.components.EmptyState
import com.myday.app.ui.components.ProgressRing
import com.myday.app.ui.components.SectionHeader
import com.myday.app.ui.components.TaskCard
import com.myday.app.ui.permissions.rememberPermissionStatus
import com.myday.app.ui.theme.isAppInDarkTheme
import com.myday.app.util.Fmt
import com.myday.app.util.Quotes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HomeScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onOpenTask: (Long) -> Unit,
    onAddTask: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val permissions by rememberPermissionStatus { vm.rescheduleAll() }
    val dark = isAppInDarkTheme(settings.themeMode)

    // ساعة حية تُحدَّث كل ثانية
    val clock by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1_000) }
    }
    val now = Fmt.toMillis(clock)
    val today = clock.toLocalDate()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val todayEntries = remember(tasks, completions, today) { TaskQueries.entriesForDay(today, tasks, completions) }
    val doneToday = todayEntries.count { it.done }
    val overdue = remember(tasks, now / 60_000) {
        tasks.filter { TaskQueries.isOverdue(it, now) && Fmt.toDate(it.dueAt) != today }
            .let { TaskQueries.smartSort(it, now) }
    }
    val overdueToday = todayEntries.count { !it.done && it.time < now }
    val favorites = remember(tasks) { tasks.filter { it.isFavorite && !it.isCompleted } }
    val suggestion = remember(tasks, now / 60_000) {
        TaskQueries.smartSort(tasks.filter { !it.isCompleted && it.dueAt < Fmt.endOfDay(today) }, now).firstOrNull()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTask,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("مهمة جديدة") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            item {
                Header(settings, clock, today)
            }
            if (!permissions.alarmsReady) {
                item {
                    WarningCard(onOpenPermissions)
                }
            }
            item {
                SummaryCard(
                    total = todayEntries.size, done = doneToday,
                    remaining = todayEntries.size - doneToday,
                    overdue = overdue.size + overdueToday
                )
            }
            item { QuoteCard(Quotes.forDate(today)) }

            if (suggestion != null) {
                item {
                    SectionHeader("ابدأ بهذه أولاً")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .clickable { onOpenTask(suggestion.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(suggestion.title, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "أولوية ${suggestion.priority.label} • ${Fmt.dateTime(suggestion.dueAt, settings.use24h)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            if (overdue.isNotEmpty()) {
                item { SectionHeader("متأخرة من أيام سابقة", count = overdue.size, actionLabel = "عرض الكل", onAction = onSeeAll) }
                items(overdue.take(3), key = { "o${it.id}" }) { t ->
                    TaskCard(
                        task = t, category = catMap[t.categoryId], now = now, use24h = settings.use24h, dark = dark,
                        onClick = { onOpenTask(t.id) }, onToggleComplete = { vm.toggleComplete(t) },
                        onToggleFavorite = { vm.toggleFavorite(t) },
                        modifier = Modifier.padding(vertical = 4.dp).animateItem()
                    )
                }
            }

            item { SectionHeader("جدول اليوم", count = todayEntries.size) }
            if (todayEntries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "لا مهام لليوم",
                        subtitle = "أضف مهمتك الأولى وحدد لها وقتاً ومنبهاً.",
                        actionLabel = "إضافة مهمة", onAction = onAddTask
                    )
                }
            } else {
                items(todayEntries, key = { "d${it.task.id}_${it.time}" }) { e ->
                    TimelineRow(e, now, settings.use24h) {
                        if (e.done && e.task.isRecurring) {
                            DoneOccurrenceCard(e, settings.use24h) { onOpenTask(e.task.id) }
                        } else {
                            TaskCard(
                                task = e.task, category = catMap[e.task.categoryId], now = now,
                                use24h = settings.use24h, dark = dark, showDate = false,
                                onClick = { onOpenTask(e.task.id) }, onToggleComplete = { vm.toggleComplete(e.task) },
                                onToggleFavorite = { vm.toggleFavorite(e.task) }
                            )
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item { SectionHeader("المهام المهمة ⭐", count = favorites.size) }
                items(favorites.take(5), key = { "f${it.id}" }) { t ->
                    TaskCard(
                        task = t, category = catMap[t.categoryId], now = now, use24h = settings.use24h, dark = dark,
                        onClick = { onOpenTask(t.id) }, onToggleComplete = { vm.toggleComplete(t) },
                        onToggleFavorite = { vm.toggleFavorite(t) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(settings: AppSettings, clock: LocalDateTime, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val hour = clock.hour
    val greeting = when (hour) {
        in 4..11 -> "صباح الخير"
        in 12..16 -> "نهارك سعيد"
        else -> "مساء الخير"
    }
    val name = settings.userName.takeIf { it.isNotBlank() }?.let { "، $it" } ?: ""
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.78f), cs.tertiary)))
            .padding(20.dp)
    ) {
        Text("$greeting$name 👋", style = MaterialTheme.typography.titleLarge, color = cs.onPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            Fmt.timeWithSeconds(clock, settings.use24h),
            fontSize = 40.sp, fontWeight = FontWeight.Light, color = cs.onPrimary
        )
        Text(Fmt.fullDate(today), style = MaterialTheme.typography.titleSmall, color = cs.onPrimary.copy(alpha = 0.92f))
        val hijri = Fmt.hijri(today)
        if (hijri.isNotEmpty()) {
            Text(hijri, style = MaterialTheme.typography.bodySmall, color = cs.onPrimary.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun WarningCard(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(cs.errorContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Warning, null, tint = cs.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("المنبهات قد لا تعمل بدقة", style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer)
            Text("اضغط لمنح الصلاحيات اللازمة", style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer)
        }
        TextButton(onClick = onClick) { Text("إصلاح", color = cs.onErrorContainer) }
    }
}

@Composable
private fun SummaryCard(total: Int, done: Int, remaining: Int, overdue: Int) {
    val cs = MaterialTheme.colorScheme
    val rate = if (total == 0) 0f else done.toFloat() / total
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerLow).padding(18.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressRing(rate, size = 92.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                Text("إنجاز", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (total == 0) "لا مهام اليوم بعد" else "أنجزت $done من $total مهام اليوم",
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniStat(Icons.Rounded.CheckCircle, "$done", "منجزة", cs.primary)
                MiniStat(Icons.Rounded.HourglassBottom, "$remaining", "متبقية", cs.secondary)
                MiniStat(Icons.Rounded.Warning, "$overdue", "متأخرة", cs.error)
            }
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuoteCard(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(cs.secondaryContainer.copy(alpha = 0.7f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.FormatQuote, null, tint = cs.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = cs.onSecondaryContainer, fontWeight = FontWeight.Medium)
    }
}

/** صف في الجدول الزمني: الوقت + خط زمني + بطاقة المهمة. */
@Composable
private fun TimelineRow(entry: DayEntry, now: Long, use24h: Boolean, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isPast = entry.time < now
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(vertical = 4.dp)) {
        Column(Modifier.width(58.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Fmt.time(entry.time, use24h), style = MaterialTheme.typography.labelMedium,
                color = if (isPast && !entry.done) cs.error else cs.onSurfaceVariant, maxLines = 1
            )
        }
        Box(Modifier.width(14.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(cs.outlineVariant))
            Box(
                Modifier.padding(top = 18.dp).size(10.dp).clip(CircleShape)
                    .background(if (entry.done) cs.primary else if (isPast) cs.error else cs.secondary)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DoneOccurrenceCard(entry: DayEntry, use24h: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerLow)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.task.title, style = MaterialTheme.typography.titleMedium, textDecoration = TextDecoration.LineThrough,
                color = cs.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("منجزة • تتكرر ${entry.task.repeatType.label}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
