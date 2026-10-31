package com.dayflow.app.ui.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.ui.components.BarChart
import com.dayflow.app.ui.components.BarDatum
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.HBarDatum
import com.dayflow.app.ui.components.HorizontalBars
import com.dayflow.app.ui.components.LineChart
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.StatTile
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.AssistantContext
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.stats.HabitStats
import com.dayflow.core.stats.ProductivityStats
import com.dayflow.core.text.Ar
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** All numbers come from the user's own records. Empty data shows zeros / empty states, never samples. */
data class Insights(
    val loaded: Boolean = false,
    val totalTasks: Int = 0,
    val openTasks: Int = 0,
    val overdue: Int = 0,
    val completionRate30: Double = 0.0,
    val plannedWeek: Int = 0,
    val actualWeek: Int = 0,
    val estimateSum: Int = 0,
    val actualSum: Int = 0,
    val habitRate30: Double? = null,
    val focusWeek: Int = 0,
    val focusSessionsWeek: Int = 0,
    val last7: List<Pair<LocalDate, Int>> = emptyList(),
    val last30: List<Int> = emptyList(),
    val weekCompare: List<Triple<String, Float, Float>> = emptyList(),
    val monthCompare: List<Triple<String, Float, Float>> = emptyList(),
    // memory
    val doneToday: List<ProductivityRecord> = emptyList(),
    val doneWeek: List<ProductivityRecord> = emptyList(),
    val cancelledMonth: Int = 0,
    val postponedMonth: Int = 0,
    val topAreasMonth: List<Pair<LifeArea, Int>> = emptyList(),
    val mostPostponed: List<Pair<String, Int>> = emptyList(),
    val weekdays: List<Pair<DayOfWeek, Int>> = emptyList(),
    val habitsDoneMonth: Int = 0,
    // balance
    val balanceDone: Map<LifeArea, Int> = emptyMap(),
    val balancePlanned: Map<LifeArea, Int> = emptyMap(),
    val goals: Map<LifeArea, Int> = emptyMap(),
)

class InsightsViewModel(assistant: AssistantService, private val settings: SettingsRepository) : ViewModel() {
    private val monthly = MutableStateFlow(false)
    val balanceMonthly: Boolean get() = monthly.value
    fun setBalanceMonthly(v: Boolean) { monthly.value = v }

    val state: StateFlow<Insights> = combine(assistant.context, settings.areaGoals, monthly) { ctx, goals, m ->
        compute(ctx, goals.associate { LifeArea.fromName(it.area) to it.weeklyMinutes }, m)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Insights())

    private fun compute(c: AssistantContext, goals: Map<LifeArea, Int>, balanceMonthly: Boolean): Insights {
        val today = c.now.toLocalDate()
        val weekStart = LocalTimes.startOfWeek(today, c.weekStart)
        val weekEnd = weekStart.plusDays(6)
        val monthStart = today.withDayOfMonth(1)
        val h = c.history
        val completed30 = ProductivityStats.countEvent(h, HistoryEvent.COMPLETED, today.minusDays(29), today)
        val openOverdue = c.tasks.count { it.recurrence == null && !it.archived && it.isOverdue(c.now) }
        var planned = 0
        val plannedByArea = mutableMapOf<LifeArea, Int>()
        var d = weekStart
        while (!d.isAfter(weekEnd)) {
            TaskOccurrences.forDate(c.tasks, d).filter { it.status.name != "CANCELLED" }.forEach {
                planned += it.estimatedMinutes ?: 0
                plannedByArea[it.area] = (plannedByArea[it.area] ?: 0) + (it.estimatedMinutes ?: 0)
            }
            d = d.plusDays(1)
        }
        val doneWeekMinutes = ProductivityStats.minutesByArea(h, weekStart, weekEnd).sumOf { it.minutes }
        val (est, act) = ProductivityStats.estimateAccuracy(h, today.minusDays(29), today)
        val habitRates = c.habits.filter { !it.archived }.map { hb ->
            HabitStats.consistency(hb, c.habitLogs.filter { it.habitId == hb.id }.map { it.date }.toSet(), today.minusDays(29), today, today)
        }
        val perDay30 = ProductivityStats.completedPerDay(h, today.minusDays(29), today)
        fun cmp(label: String, event: HistoryEvent?, from1: LocalDate, to1: LocalDate, from0: LocalDate, to0: LocalDate): Triple<String, Float, Float> =
            if (event == null) {
                Triple(label, ProductivityStats.focusMinutes(c.focusSessions, from1, to1).toFloat(), ProductivityStats.focusMinutes(c.focusSessions, from0, to0).toFloat())
            } else {
                Triple(label, ProductivityStats.countEvent(h, event, from1, to1).toFloat(), ProductivityStats.countEvent(h, event, from0, to0).toFloat())
            }
        val prevWeekStart = weekStart.minusDays(7)
        val prevMonthStart = monthStart.minusMonths(1)
        val balancePeriodStart = if (balanceMonthly) monthStart else weekStart
        val balancePeriodEnd = if (balanceMonthly) today.withDayOfMonth(today.lengthOfMonth()) else weekEnd
        return Insights(
            loaded = true,
            totalTasks = c.tasks.count { !it.archived },
            openTasks = c.tasks.count { !it.archived && it.status.isOpen },
            overdue = openOverdue,
            completionRate30 = ProductivityStats.completionRate(completed30, 0, openOverdue),
            plannedWeek = planned,
            actualWeek = doneWeekMinutes,
            estimateSum = est, actualSum = act,
            habitRate30 = habitRates.takeIf { it.isNotEmpty() }?.average(),
            focusWeek = ProductivityStats.focusMinutes(c.focusSessions, weekStart, weekEnd),
            focusSessionsWeek = c.focusSessions.count { it.type.name == "FOCUS" && !it.startedAt.toLocalDate().isBefore(weekStart) && !it.startedAt.toLocalDate().isAfter(weekEnd) },
            last7 = (6L downTo 0L).map { today.minusDays(it) }.map { it to (perDay30[it] ?: 0) },
            last30 = perDay30.values.toList(),
            weekCompare = listOf(
                cmp("completed", HistoryEvent.COMPLETED, weekStart, weekEnd, prevWeekStart, prevWeekStart.plusDays(6)),
                cmp("postponed", HistoryEvent.POSTPONED, weekStart, weekEnd, prevWeekStart, prevWeekStart.plusDays(6)),
                cmp("focus", null, weekStart, weekEnd, prevWeekStart, prevWeekStart.plusDays(6)),
            ),
            monthCompare = listOf(
                cmp("completed", HistoryEvent.COMPLETED, monthStart, today, prevMonthStart, monthStart.minusDays(1)),
                cmp("postponed", HistoryEvent.POSTPONED, monthStart, today, prevMonthStart, monthStart.minusDays(1)),
                cmp("focus", null, monthStart, today, prevMonthStart, monthStart.minusDays(1)),
            ),
            doneToday = ProductivityStats.completedBetween(h, today, today),
            doneWeek = ProductivityStats.completedBetween(h, weekStart, weekEnd),
            cancelledMonth = ProductivityStats.countEvent(h, HistoryEvent.CANCELLED, monthStart, today),
            postponedMonth = ProductivityStats.countEvent(h, HistoryEvent.POSTPONED, monthStart, today),
            topAreasMonth = ProductivityStats.minutesByArea(h, monthStart, today).filter { it.minutes > 0 }.map { it.area to it.minutes },
            mostPostponed = ProductivityStats.mostPostponed(h, 5),
            weekdays = ProductivityStats.productiveWeekdays(h, today.minusDays(89), today),
            habitsDoneMonth = c.habitLogs.count { !it.date.isBefore(monthStart) },
            balanceDone = ProductivityStats.minutesByArea(h, balancePeriodStart, balancePeriodEnd).associate { it.area to it.minutes },
            balancePlanned = plannedByArea,
            goals = goals,
        )
    }

    fun setGoal(area: LifeArea, minutes: Int?) = viewModelScope.launch { settings.setAreaGoal(area, minutes) }
}

@Composable
private fun compareLabel(key: String) = stringResource(
    when (key) { "completed" -> R.string.cmp_completed; "postponed" -> R.string.cmp_postponed; else -> R.string.cmp_focus_min },
)

@Composable
fun StatsScreen(nav: NavController) {
    val vm = dfViewModel { InsightsViewModel(it.assistantService, it.settingsRepository) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.statistics), onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.stat_tasks), s.totalTasks.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_open), s.openTasks.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_overdue), s.overdue.toString(), Modifier.weight(1f), accent = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.stat_completion_rate), "${(s.completionRate30 * 100).toInt()}٪", Modifier.weight(1f), accent = MaterialTheme.colorScheme.tertiary)
                StatTile(stringResource(R.string.stat_habit_rate), s.habitRate30?.let { "${(it * 100).toInt()}٪" } ?: "—", Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_focus_week), Ar.duration(s.focusWeek), Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.stat_planned_week), Ar.duration(s.plannedWeek), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_actual_week), Ar.duration(s.actualWeek), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_focus_sessions), s.focusSessionsWeek.toString(), Modifier.weight(1f))
            }
            SectionHeader(stringResource(R.string.last_7_days))
            DfCard { BarChart(s.last7.map { BarDatum(Ar.shortDayName(it.first.dayOfWeek), it.second.toFloat()) }) }

            SectionHeader(stringResource(R.string.weekly_comparison))
            DfCard { CompareTable(s.weekCompare, stringResource(R.string.this_week), stringResource(R.string.last_week)) }
            SectionHeader(stringResource(R.string.monthly_comparison))
            DfCard { CompareTable(s.monthCompare, stringResource(R.string.this_month), stringResource(R.string.last_month)) }

            SectionHeader(stringResource(R.string.trend_30))
            DfCard {
                LineChart(
                    s.last30.map { it.toFloat() },
                    description = stringResource(R.string.trend_30) + ": " + s.last30.joinToString("، "),
                    startLabel = stringResource(R.string.days_ago_30), endLabel = stringResource(R.string.today),
                )
            }
            SectionHeader(stringResource(R.string.planned_vs_actual))
            DfCard {
                if (s.estimateSum == 0) {
                    Text(stringResource(R.string.no_estimate_data), style = MaterialTheme.typography.bodySmall)
                } else {
                    BarChart(
                        listOf(
                            BarDatum(stringResource(R.string.estimated), s.estimateSum.toFloat(), MaterialTheme.colorScheme.primary),
                            BarDatum(stringResource(R.string.actual), s.actualSum.toFloat(), MaterialTheme.colorScheme.secondary),
                        ),
                        height = 120.dp,
                        valueFormatter = { Ar.duration(it.toInt()) },
                    )
                    val ratio = s.actualSum.toDouble() / s.estimateSum
                    Text(
                        stringResource(if (ratio > 1.1) R.string.estimate_under else if (ratio < 0.9) R.string.estimate_over else R.string.estimate_good, (ratio * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}


@Composable
private fun CompareTable(rows: List<Triple<String, Float, Float>>, nowLabel: String, beforeLabel: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("", Modifier.weight(1.4f))
        Text(nowLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text(beforeLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
    }
    rows.forEach { (key, now, before) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(compareLabel(key), Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
            val fmt: (Float) -> String = { if (key == "focus") Ar.duration(it.toInt()) else it.toInt().toString() }
            Text(fmt(now) + arrow(now, before, key == "postponed"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(fmt(before), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


private fun arrow(now: Float, before: Float, lowerIsBetter: Boolean): String = when {
    now == before -> ""
    (now > before) != lowerIsBetter -> " ▲"
    else -> " ▼"
}

@Composable
fun MemoryScreen(nav: NavController) {
    val vm = dfViewModel { InsightsViewModel(it.assistantService, it.settingsRepository) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.productivity_memory), onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader(stringResource(R.string.q_done_today))
            DfCard {
                if (s.doneToday.isEmpty()) Text(stringResource(R.string.nothing_yet), style = MaterialTheme.typography.bodySmall)
                s.doneToday.forEach { Text("✓ ${it.taskTitle}" + (it.actualMinutes?.let { m -> " — ${Ar.duration(m)}" } ?: "")) }
            }
            SectionHeader(stringResource(R.string.q_done_week))
            DfCard {
                Text(stringResource(R.string.done_count_week, s.doneWeek.size), style = MaterialTheme.typography.titleSmall)
                s.doneWeek.groupBy { it.at.toLocalDate() }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
                    Text(Ar.date(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { Text("• ${it.taskTitle}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            SectionHeader(stringResource(R.string.q_top_area))
            DfCard {
                if (s.topAreasMonth.isEmpty()) Text(stringResource(R.string.nothing_yet), style = MaterialTheme.typography.bodySmall)
                else HorizontalBars(s.topAreasMonth.map { (a, m) -> HBarDatum(Ar.area(a), m.toFloat(), Ar.duration(m), AppColors.area(a)) })
            }
            SectionHeader(stringResource(R.string.q_most_postponed))
            DfCard {
                if (s.mostPostponed.isEmpty()) Text(stringResource(R.string.no_postponed), style = MaterialTheme.typography.bodySmall)
                s.mostPostponed.forEach { (t, n) -> Text("• $t — ${stringResource(R.string.times_x, n)}") }
            }
            SectionHeader(stringResource(R.string.q_month_trend))
            DfCard {
                LineChart(
                    s.last30.map { it.toFloat() }, description = s.last30.joinToString("، "),
                    startLabel = stringResource(R.string.days_ago_30), endLabel = stringResource(R.string.today),
                )
                Text(stringResource(R.string.month_summary, s.postponedMonth, s.cancelledMonth, s.habitsDoneMonth), style = MaterialTheme.typography.bodySmall)
            }
            SectionHeader(stringResource(R.string.q_best_days))
            DfCard {
                if (s.weekdays.isEmpty()) Text(stringResource(R.string.nothing_yet), style = MaterialTheme.typography.bodySmall)
                else BarChart(s.weekdays.map { BarDatum(Ar.shortDayName(it.first), it.second.toFloat()) })
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
fun BalanceScreen(nav: NavController) {
    val vm = dfViewModel { InsightsViewModel(it.assistantService, it.settingsRepository) }
    val s by vm.state.collectAsStateWithLifecycle()
    var editArea by remember { mutableStateOf<LifeArea?>(null) }
    val areas = listOf(
        LifeArea.WORK, LifeArea.STUDY, LifeArea.FAMILY, LifeArea.HEALTH, LifeArea.WORSHIP,
        LifeArea.REST, LifeArea.PERSONAL_GROWTH, LifeArea.SOCIAL,
    )
    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.life_balance), onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.balance_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !vm.balanceMonthly, onClick = { vm.setBalanceMonthly(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.this_week)) }
                SegmentedButton(selected = vm.balanceMonthly, onClick = { vm.setBalanceMonthly(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.this_month)) }
            }
            SectionHeader(stringResource(R.string.time_spent))
            DfCard {
                val weeks = if (vm.balanceMonthly) 4.3f else 1f
                HorizontalBars(
                    areas.map { a ->
                        val m = s.balanceDone[a] ?: 0
                        HBarDatum(Ar.area(a), m.toFloat(), Ar.duration(m), AppColors.area(a), s.goals[a]?.let { it * weeks })
                    },
                )
                Text(stringResource(R.string.balance_goal_legend), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            }
            SectionHeader(stringResource(R.string.planned_this_week))
            DfCard {
                HorizontalBars(areas.map { a -> val m = s.balancePlanned[a] ?: 0; HBarDatum(Ar.area(a), m.toFloat(), Ar.duration(m), AppColors.area(a).copy(alpha = 0.6f)) })
            }
            SectionHeader(stringResource(R.string.my_goals))
            areas.forEach { a ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(Ar.area(a), Modifier.weight(1f))
                    Text(s.goals[a]?.let { stringResource(R.string.goal_per_week, Ar.duration(it)) } ?: stringResource(R.string.no_goal), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { editArea = a }) { Text(stringResource(R.string.edit)) }
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    editArea?.let { area ->
        var hours by remember(area) { mutableStateOf(s.goals[area]?.let { (it / 60.0).toString().removeSuffix(".0") } ?: "") }
        AlertDialog(
            onDismissRequest = { editArea = null },
            title = { Text(stringResource(R.string.goal_for, Ar.area(area))) },
            text = {
                OutlinedTextField(
                    hours, { hours = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text(stringResource(R.string.hours_per_week)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setGoal(area, hours.toDoubleOrNull()?.let { (it * 60).toInt() }); editArea = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.setGoal(area, null); editArea = null }) { Text(stringResource(R.string.remove_goal)) }
            },
        )
    }
}
