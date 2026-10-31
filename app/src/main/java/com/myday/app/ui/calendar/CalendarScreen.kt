package com.myday.app.ui.calendar

import com.myday.app.data.isRecurring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.TaskQueries
import com.myday.app.ui.components.EmptyState
import com.myday.app.ui.components.SectionHeader
import com.myday.app.ui.components.TaskCard
import com.myday.app.ui.theme.isAppInDarkTheme
import com.myday.app.util.Fmt
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onOpenTask: (Long) -> Unit,
    onAddTask: (LocalDate) -> Unit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val dark = isAppInDarkTheme(settings.themeMode)
    val today = LocalDate.now()

    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.from(today).plusMonths(monthOffset)

    val catMap = remember(categories) { categories.associateBy { it.id } }
    // عدد المهام لكل يوم في الشهر المعروض (لنقاط المؤشر)
    val monthCounts = remember(tasks, completions, month) {
        (1..month.lengthOfMonth()).associateWith { d ->
            val st = TaskQueries.statsForDay(month.atDay(d), tasks, completions)
            st
        }
    }
    val entries = remember(tasks, completions, selectedDay) { TaskQueries.entriesForDay(selected, tasks, completions) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddTask(selected) }) { Icon(Icons.Rounded.Add, "مهمة في هذا اليوم") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("التقويم", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { monthOffset = 0L; selectedDay = today.toEpochDay() }) { Icon(Icons.Rounded.Today, "اليوم") }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // في الواجهة العربية: السهم الأيمن = الشهر السابق
                        IconButton(onClick = { monthOffset-- }) { Icon(Icons.Rounded.ChevronRight, "الشهر السابق") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.monthYear(month.atDay(1)), style = MaterialTheme.typography.titleMedium)
                            Text(Fmt.hijri(month.atDay(1)).substringAfter(' '), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { monthOffset++ }) { Icon(Icons.Rounded.ChevronLeft, "الشهر التالي") }
                    }
                    AnimatedContent(targetState = month, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "month") { m ->
                        MonthGrid(
                            month = m, today = today, selected = selected,
                            stats = { d -> if (m == month) monthCounts[d] else null },
                            onSelect = { selectedDay = it.toEpochDay() }
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    if (selected == today) "مهام اليوم" else "${Fmt.dayName(selected)} ${Fmt.dayMonth(selected)}",
                    count = entries.size
                )
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.EventBusy, "لا مهام في هذا اليوم", "اضغط + لإضافة مهمة في هذا التاريخ.",
                        actionLabel = "إضافة مهمة", onAction = { onAddTask(selected) }
                    )
                }
            } else {
                items(entries, key = { "${it.task.id}_${it.time}" }) { e ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.time(e.time, settings.use24h), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp)
                        )
                        Box(Modifier.weight(1f)) {
                            TaskCard(
                                task = if (e.done && e.task.isRecurring) e.task.copy(isCompleted = true, dueAt = e.time) else if (!e.done && e.task.isRecurring) e.task.copy(dueAt = e.time) else e.task,
                                category = catMap[e.task.categoryId], now = now, use24h = settings.use24h, dark = dark,
                                showDate = false,
                                onClick = { onOpenTask(e.task.id) },
                                onToggleComplete = {
                                    if (e.task.isRecurring && (e.done || e.time != e.task.dueAt)) onOpenTask(e.task.id)
                                    else vm.toggleComplete(e.task)
                                },
                                onToggleFavorite = { vm.toggleFavorite(e.task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    stats: (Int) -> com.myday.app.ui.DayStats?,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - Fmt.weekStart.value) + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Fmt.weekDaysShort.forEach { d ->
                Text(d, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 1..month.lengthOfMonth()) {
                            val date = month.atDay(idx)
                            val isSel = date == selected
                            val isToday = date == today
                            val st = stats(idx)
                            Column(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (isSel) cs.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .then(if (isToday && !isSel) Modifier.border(1.5.dp, cs.primary, CircleShape) else Modifier)
                                    .clickable { onSelect(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "$idx",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) cs.onPrimary else cs.onSurface
                                )
                                if (st != null && st.total > 0) {
                                    val allDone = st.done == st.total
                                    Box(
                                        Modifier.padding(top = 2.dp).size(width = if (st.total > 2) 14.dp else 6.dp, height = 5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSel) cs.onPrimary
                                                else if (allDone) cs.primary
                                                else if (date.isBefore(today)) cs.error
                                                else cs.secondary
                                            )
                                    )
                                } else Spacer(Modifier.height(7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
