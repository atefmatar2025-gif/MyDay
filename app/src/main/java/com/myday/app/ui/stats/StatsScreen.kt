package com.myday.app.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.DayStats
import com.myday.app.ui.TaskQueries
import com.myday.app.ui.components.ProgressRing
import com.myday.app.ui.components.StatTile
import com.myday.app.ui.components.color
import com.myday.app.util.Fmt
import java.time.LocalDate

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 أسبوع، 1 شهر

    val todayStats = remember(tasks, completions, today) { TaskQueries.statsForDay(today, tasks, completions) }
    val overdueCount = remember(tasks, now) { tasks.count { TaskQueries.isOverdue(it, now) } }
    val streak = remember(completions) { TaskQueries.streak(completions) }

    val days: List<LocalDate> = remember(period, today) {
        if (period == 0) {
            val start = Fmt.startOfWeek(today)
            (0L until 7L).map { start.plusDays(it) }
        } else {
            val first = today.withDayOfMonth(1)
            (0 until today.lengthOfMonth()).map { first.plusDays(it.toLong()) }
        }
    }
    val dayStats: List<Pair<LocalDate, DayStats>> = remember(days, tasks, completions) {
        days.map { it to TaskQueries.statsForDay(it, tasks, completions) }
    }
    val upToToday = dayStats.filter { !it.first.isAfter(today) }
    val periodDone = upToToday.sumOf { it.second.done }
    val periodTotal = upToToday.sumOf { it.second.total }
    val periodRate = if (periodTotal == 0) 0f else periodDone.toFloat() / periodTotal
    val bestDay = upToToday.filter { it.second.done > 0 }.maxByOrNull { it.second.done }

    // التوزيع حسب التصنيف للإنجازات في الفترة
    val byCategory = remember(completions, tasks, categories, days) {
        val start = Fmt.startOfDay(days.first())
        val end = Fmt.endOfDay(days.last())
        val taskCat = tasks.associate { it.id to it.categoryId }
        completions.filter { it.occurrenceDueAt in start until end }
            .groupingBy { taskCat[it.taskId] }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .map { (catId, n) -> (categories.firstOrNull { it.id == catId }) to n }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("الإحصائيات", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))

            // اليوم
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRing(todayStats.rate, size = 86.dp, track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                    Text("${(todayStats.rate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("نسبة إنجاز اليوم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (todayStats.total == 0) "لا مهام مجدولة اليوم" else "${todayStats.done} من ${todayStats.total} مهام",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("مهام اليوم", "${todayStats.total}", Icons.Rounded.Today, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatTile("مكتملة", "${todayStats.done}", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("متبقية", "${todayStats.pending}", Icons.Rounded.HourglassBottom, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatTile("متأخرة", "$overdueCount", Icons.Rounded.Warning, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("هذا الأسبوع", "هذا الشهر").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = period == i, onClick = { period = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)
            ) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("مستوى الإنجاز", style = MaterialTheme.typography.titleMedium)
                        Text("$periodDone منجزة من $periodTotal • ${(periodRate * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                BarChart(dayStats, today, compact = period == 1)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("أيام متتالية بإنجاز", "$streak", Icons.Rounded.Whatshot, Color(0xFFEA580C), Modifier.weight(1f))
                StatTile(
                    "أفضل يوم",
                    bestDay?.let { "${Fmt.dayName(it.first)} (${it.second.done})" } ?: "—",
                    Icons.Rounded.EmojiEvents, Color(0xFFCA8A04), Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)
            ) {
                Text("الإنجاز حسب التصنيف", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (byCategory.isEmpty()) {
                    Text("لا إنجازات في هذه الفترة بعد.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val max = byCategory.maxOf { it.second }.coerceAtLeast(1)
                    byCategory.forEach { (cat, n) ->
                        val col = cat?.color() ?: MaterialTheme.colorScheme.outline
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cat?.name ?: "بلا تصنيف", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(col.copy(alpha = 0.15f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / max).clip(CircleShape).background(col))
                            }
                            Text("$n", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** رسم بياني بسيط بالأعمدة: الإنجاز مقابل الإجمالي لكل يوم. */
@Composable
private fun BarChart(data: List<Pair<LocalDate, DayStats>>, today: LocalDate, compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    val maxTotal = (data.maxOfOrNull { it.second.total } ?: 0).coerceAtLeast(1)
    val anim by animateFloatAsState(1f, tween(800), label = "bars")
    val doneColor = cs.primary
    val trackColor = cs.primary.copy(alpha = 0.15f)
    val todayColor = cs.secondary

    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = data.size
            val slot = size.width / n
            val barW = (slot * if (compact) 0.6f else 0.45f).coerceAtMost(28.dp.toPx())
            data.forEachIndexed { i, (date, st) ->
                // RTL: اليوم الأول على اليمين
                val cx = size.width - (i + 0.5f) * slot
                val left = cx - barW / 2
                val totalH = size.height * st.total / maxTotal * anim
                val doneH = size.height * st.done / maxTotal * anim
                val r = CornerRadius(barW / 2, barW / 2)
                drawRoundRect(trackColor, Offset(left, size.height - totalH), Size(barW, totalH), r)
                if (doneH > 0f) {
                    drawRoundRect(if (date == today) todayColor else doneColor, Offset(left, size.height - doneH), Size(barW, doneH), r)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (!compact) {
            Row(Modifier.fillMaxWidth()) {
                data.forEach { (date, _) ->
                    Text(
                        Fmt.weekDaysShort[((date.dayOfWeek.value - Fmt.weekStart.value) + 7) % 7],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == today) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text("${data.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(doneColor))
            Text(" منجزة   ", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.size(10.dp).clip(CircleShape).background(trackColor))
            Text(" إجمالي المهام", style = MaterialTheme.typography.labelSmall)
        }
    }
}
