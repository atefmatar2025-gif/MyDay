package com.dayflow.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.TaskReminderEntity
import com.dayflow.app.data.weekStart
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.ui.components.ColorDot
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.TaskRow
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.AssistantContext
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.Habit
import com.dayflow.core.model.Task
import com.dayflow.core.text.Ar
import com.dayflow.core.time.HijriCalendar
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class CalendarMode { MONTH, WEEK, DAY }

data class DayAgenda(
    val date: LocalDate,
    val tasks: List<Task>,
    val habits: List<Pair<Habit, Boolean>>,
    val reminders: List<Pair<LocalDateTime, String>>,
)

data class CalendarState(
    val mode: CalendarMode = CalendarMode.MONTH,
    val selected: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val weekStart: DayOfWeek = DayOfWeek.SATURDAY,
    val counts: Map<LocalDate, Int> = emptyMap(),
    val agenda: List<DayAgenda> = emptyList(),
    val showHijri: Boolean = false,
    val hijriAdjust: Int = 0,
    val now: LocalDateTime = LocalDateTime.now(),
)

private data class CalUi(val mode: CalendarMode, val selected: LocalDate, val month: YearMonth)

class CalendarViewModel(
    assistant: AssistantService,
    settings: SettingsRepository,
    tasks: TaskRepository,
    private val habits: HabitRepository,
    private val interactor: TaskInteractor,
) : ViewModel() {
    private val ui = MutableStateFlow(CalUi(CalendarMode.MONTH, LocalDate.now(), YearMonth.now()))

    val state: StateFlow<CalendarState> = combine(assistant.context, settings.settings, tasks.allRemindersFlow, ui) { ctx, s, reminders, u ->
        val ws = s.weekStart
        val gridStart = LocalTimes.startOfWeek(u.month.atDay(1), ws)
        val counts = (0L until 42L).associate { off ->
            val d = gridStart.plusDays(off)
            d to TaskOccurrences.forDate(ctx.tasks, d).count { it.status.name != "CANCELLED" }
        }
        val days = when (u.mode) {
            CalendarMode.MONTH, CalendarMode.DAY -> listOf(u.selected)
            CalendarMode.WEEK -> {
                val start = LocalTimes.startOfWeek(u.selected, ws)
                (0L..6L).map { start.plusDays(it) }
            }
        }
        CalendarState(
            mode = u.mode, selected = u.selected, month = u.month, weekStart = ws, counts = counts,
            agenda = days.map { agendaFor(ctx, reminders, it) },
            showHijri = s.showHijri, hijriAdjust = s.hijriAdjustment, now = ctx.now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    private fun agendaFor(ctx: AssistantContext, reminders: List<TaskReminderEntity>, date: LocalDate): DayAgenda {
        val tasks = TaskOccurrences.forDate(ctx.tasks, date).sortedBy { it.startAt ?: it.dueAt }
        val byTask = reminders.filter { it.enabled }.groupBy { it.taskId }
        val rem = tasks.flatMap { t ->
            val base = t.startAt ?: t.dueAt
            if (base == null || !t.status.isOpen) emptyList()
            else byTask[t.id].orEmpty().map { base.minusMinutes(it.offsetMinutes.toLong()) to t.title }
        }.filter { it.first.toLocalDate() == date }.sortedBy { it.first }
        val habits = ctx.habits.filter { !it.archived && it.isScheduledOn(date) && !date.isBefore(it.createdOn) }
            .map { h -> h to ctx.habitLogs.any { it.habitId == h.id && it.date == date } }
        return DayAgenda(date, tasks, habits, rem)
    }

    fun select(d: LocalDate) = ui.update { it.copy(selected = d, month = YearMonth.from(d)) }
    fun setMode(m: CalendarMode) = ui.update { it.copy(mode = m) }
    fun shift(forward: Boolean) = ui.update {
        val sign = if (forward) 1L else -1L
        when (it.mode) {
            CalendarMode.MONTH -> it.copy(month = it.month.plusMonths(sign), selected = it.month.plusMonths(sign).atDay(1))
            CalendarMode.WEEK -> it.copy(selected = it.selected.plusWeeks(sign), month = YearMonth.from(it.selected.plusWeeks(sign)))
            CalendarMode.DAY -> it.copy(selected = it.selected.plusDays(sign), month = YearMonth.from(it.selected.plusDays(sign)))
        }
    }
    fun today() = select(LocalDate.now())

    fun toggle(t: Task) = viewModelScope.launch { if (t.status.isOpen) interactor.complete(t.id) else interactor.reopen(t.id) }
    fun toggleHabit(h: Habit, date: LocalDate, done: Boolean) = viewModelScope.launch { habits.setDone(h.id, date, !done) }
}

@Composable
fun CalendarScreen(nav: NavController) {
    val vm = dfViewModel { CalendarViewModel(it.assistantService, it.settingsRepository, it.taskRepository, it.habitRepository, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_calendar), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.today() }) { Text(stringResource(R.string.today)) }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CalendarMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.mode == m, onClick = { vm.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size),
                    ) {
                        Text(stringResource(when (m) { CalendarMode.MONTH -> R.string.view_month; CalendarMode.WEEK -> R.string.view_week; CalendarMode.DAY -> R.string.day }))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // RTL: "previous" sits on the right.
                IconButton(onClick = { vm.shift(false) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.previous)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val title = when (s.mode) {
                        CalendarMode.MONTH -> "${Ar.monthName(s.month.monthValue)} ${s.month.year}"
                        CalendarMode.WEEK -> {
                            val st = LocalTimes.startOfWeek(s.selected, s.weekStart)
                            "${Ar.shortDate(st)} – ${Ar.shortDate(st.plusDays(6))}"
                        }
                        CalendarMode.DAY -> Ar.date(s.selected)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (s.showHijri) {
                        HijriCalendar.format(if (s.mode == CalendarMode.MONTH) s.month.atDay(15) else s.selected, s.hijriAdjust)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = { vm.shift(true) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.next)) }
            }
        }
        if (s.mode == CalendarMode.MONTH) {
            item { MonthGrid(s) { vm.select(it) } }
        }
        s.agenda.forEach { day ->
            item(key = "head_${day.date}") {
                SectionHeader(
                    if (s.mode == CalendarMode.WEEK) Ar.date(day.date) else stringResource(R.string.agenda_of, Ar.shortDate(day.date)),
                    action = stringResource(R.string.my_day_timeline),
                ) { nav.navigate(Routes.timeline(day.date.toEpochDay())) }
            }
            if (day.tasks.isEmpty() && day.habits.isEmpty()) {
                item(key = "empty_${day.date}") {
                    Text(stringResource(R.string.nothing_this_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(day.tasks, key = { "t_${day.date}_${it.id}" }) { t ->
                TaskRow(t, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) }, showDate = false)
            }
            if (day.reminders.isNotEmpty()) {
                item(key = "rem_${day.date}") {
                    DfCard(container = MaterialTheme.colorScheme.surfaceContainer) {
                        day.reminders.forEach { (at, title) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.sm))
                                Text("${Ar.time(at.toLocalTime())} — $title", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (day.habits.isNotEmpty()) {
                item(key = "hab_${day.date}") {
                    DfCard(container = MaterialTheme.colorScheme.surfaceContainer) {
                        day.habits.forEach { (h, done) ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !day.date.isAfter(LocalDate.now())) { vm.toggleHabit(h, day.date, done) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (done) extra.success else MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(Spacing.sm))
                                Text("${h.icon} ${h.name}")
                            }
                        }
                    }
                }
            }
            item(key = "add_${day.date}") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { nav.navigate(Routes.editor(date = day.date.toEpochDay())) }) {
                        Icon(Icons.Filled.Add, null); Text(stringResource(R.string.add_on_day))
                    }
                    if (s.mode != CalendarMode.WEEK) {
                        TextButton(onClick = { nav.navigate(Routes.timeline(day.date.toEpochDay())) }) {
                            Icon(Icons.Filled.Timeline, null); Text(stringResource(R.string.my_day_timeline))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarState, onSelect: (LocalDate) -> Unit) {
    val gridStart = LocalTimes.startOfWeek(s.month.atDay(1), s.weekStart)
    val today = LocalDate.now()
    Column {
        Row {
            (0L..6L).forEach { i ->
                Text(
                    Ar.shortDayName(s.weekStart.plus(i)), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val weeks = if (gridStart.plusDays(35).month == s.month.month) 6 else 5
        for (w in 0 until weeks) {
            Row {
                for (dIdx in 0 until 7) {
                    val date = gridStart.plusDays((w * 7 + dIdx).toLong())
                    val inMonth = date.month == s.month.month
                    val selected = date == s.selected
                    val count = s.counts[date] ?: 0
                    val desc = "${Ar.date(date)}، ${Ar.tasks(count)}"
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                            .let { if (date == today && !selected) it.border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape) else it }
                            .clickable { onSelect(date) }
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                },
                            )
                            if (s.showHijri && inMonth) {
                                HijriCalendar.fromGregorian(date, s.hijriAdjust)?.let {
                                    Text(it.day.toString(), style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (count > 0) ColorDot(if (selected) MaterialTheme.colorScheme.onPrimary else AppColors.parse("#F39B42"), 5.dp)
                        }
                    }
                }
            }
        }
    }
}
