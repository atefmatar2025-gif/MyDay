package com.dayflow.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.ProgressRing
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.TaskRow
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.Brand
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.Recommendation
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.intelligence.TaskRanker
import com.dayflow.core.model.Habit
import com.dayflow.core.model.Task
import com.dayflow.core.scheduling.DayPlanner
import com.dayflow.core.text.Ar
import com.dayflow.core.time.HijriCalendar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

data class DashboardState(
    val now: LocalDateTime = LocalDateTime.now(),
    val name: String = "",
    val hijri: String? = null,
    val todayTotal: Int = 0,
    val todayDone: Int = 0,
    val recommendation: Recommendation? = null,
    val upcoming: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
    val habits: List<Pair<Habit, Boolean>> = emptyList(),
    val freeMinutes: Int = 0,
    val requiredMinutes: Int = 0,
    val nextReminder: String? = null,
    val insight: String? = null,
    val loaded: Boolean = false,
) {
    val progress: Float get() = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal
}

class DashboardViewModel(
    assistant: AssistantService,
    settings: SettingsRepository,
    tasks: TaskRepository,
    private val habits: HabitRepository,
    private val interactor: TaskInteractor,
) : ViewModel() {

    val state: StateFlow<DashboardState> = combine(
        assistant.context, settings.settings, tasks.observeNextReminder(System.currentTimeMillis()),
    ) { ctx, s, nextReminder ->
        val today = ctx.now.toLocalDate()
        val todays = TaskOccurrences.forDate(ctx.tasks, today).filter { it.status.name != "CANCELLED" }
        val open = todays.filter { it.status.isOpen }
        val overdue = ctx.tasks.filter { it.recurrence == null && !it.archived && it.isOverdue(ctx.now) }
            .sortedBy { it.dueAt ?: it.startAt }
        val plan = DayPlanner.plan(ctx.tasks, today, ctx.now, ctx.settings)
        val nextTask = nextReminder?.let { r -> ctx.tasks.firstOrNull { it.id == r.taskId } }
        DashboardState(
            now = ctx.now,
            name = s.displayName,
            hijri = if (s.showHijri) HijriCalendar.format(today, s.hijriAdjustment) else null,
            todayTotal = todays.size,
            todayDone = todays.count { !it.status.isOpen },
            recommendation = TaskRanker.whatNow(ctx.tasks, ctx.now, ctx.settings),
            upcoming = open.filter { it.startAt != null && !it.startAt!!.isBefore(ctx.now) }.sortedBy { it.startAt }.take(4),
            overdue = overdue.take(5),
            habits = ctx.habits.filter { !it.archived && it.isScheduledOn(today) }
                .map { h -> h to ctx.habitLogs.any { it.habitId == h.id && it.date == today } },
            freeMinutes = plan.freeMinutes,
            requiredMinutes = plan.requiredMinutes,
            nextReminder = if (nextReminder?.nextTriggerAt != null && nextTask != null) {
                val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextReminder.nextTriggerAt!!), ZoneId.systemDefault())
                "${Ar.dateTime(at, today)} — ${nextTask.title}"
            } else null,
            insight = assistant.engine.dailyInsight(ctx),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun toggle(task: Task) = viewModelScope.launch {
        if (task.status.isOpen) interactor.complete(task.id) else interactor.reopen(task.id)
    }

    fun toggleHabit(habit: Habit, done: Boolean) = viewModelScope.launch {
        habits.setDone(habit.id, java.time.LocalDate.now(), !done)
    }

    fun start(task: Task) = viewModelScope.launch { interactor.start(task.id) }
}

@Composable
fun DashboardScreen(nav: NavController) {
    val vm = dfViewModel { DashboardViewModel(it.assistantService, it.settingsRepository, it.taskRepository, it.habitRepository, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ---- greeting & date ----
        item {
            val hour = s.now.hour
            val greeting = stringResource(
                when (hour) {
                    in 4..11 -> R.string.greeting_morning
                    in 12..16 -> R.string.greeting_afternoon
                    else -> R.string.greeting_evening
                },
            )
            Column(Modifier.padding(top = Spacing.sm)) {
                Text(
                    if (s.name.isBlank()) greeting else stringResource(R.string.greeting_with_name, greeting, s.name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Ar.date(s.now.toLocalDate()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(Ar.time(s.now.toLocalTime()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                s.hijri?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        // ---- progress summary ----
        item {
            DfCard(container = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        s.progress, size = 92.dp, color = Brand.WarmOrange,
                        track = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    ) {
                        Text("${(s.progress * 100).toInt()}٪", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.today_progress), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.done_of_total, s.todayDone, s.todayTotal),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.free_vs_required, Ar.duration(s.freeMinutes), Ar.duration(s.requiredMinutes)),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // ---- WHAT SHOULD I DO NOW? ----
        item {
            val rec = s.recommendation
            DfCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text("WHAT SHOULD I DO NOW?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(stringResource(R.string.what_now), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.size(Spacing.sm))
                if (rec == null) {
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(rec.headline, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    rec.reasons.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    val task = rec.task
                    if (task != null && rec.kind == Recommendation.Kind.TASK) {
                        Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(
                                onClick = { vm.start(task); nav.navigate(Routes.focus(task.id)) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.start_now))
                            }
                            OutlinedButton(onClick = { nav.navigate(Routes.task(task.id)) }) { Text(stringResource(R.string.details)) }
                        }
                    } else if (task != null) {
                        OutlinedButton(onClick = { nav.navigate(Routes.task(task.id)) }, modifier = Modifier.padding(top = Spacing.sm)) {
                            Text(stringResource(R.string.details))
                        }
                    }
                }
            }
        }

        // ---- quick actions ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { nav.navigate(Routes.editor()) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_short))
                }
                FilledTonalButton(onClick = { nav.navigate(Routes.editor(voice = true)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.voice_short))
                }
                FilledTonalButton(onClick = { nav.navigate(Routes.saveMyDay()) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Bolt, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.save_my_day))
                }
            }
        }

        // ---- assistant insight & next reminder ----
        if (s.insight != null || s.nextReminder != null) {
            item {
                DfCard(onClick = { nav.navigate(Routes.ASSISTANT) }) {
                    s.insight?.let {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    s.nextReminder?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (s.insight != null) Spacing.sm else 0.dp)) {
                            Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(R.string.next_reminder, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ---- habits ----
        if (s.habits.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.todays_habits), action = stringResource(R.string.view_all)) { nav.navigate(Routes.HABITS) } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(s.habits, key = { it.first.id }) { (h, done) ->
                        FilterChip(
                            selected = done,
                            onClick = { vm.toggleHabit(h, done) },
                            label = { Text("${h.icon} ${h.name}") },
                            leadingIcon = if (done) ({ Icon(Icons.Filled.CheckCircle, null, tint = extra.success) }) else null,
                        )
                    }
                }
            }
        }

        // ---- overdue ----
        if (s.overdue.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.overdue_tasks), action = stringResource(R.string.save_my_day)) { nav.navigate(Routes.saveMyDay()) } }
            items(s.overdue, key = { "o${it.id}" }) { t ->
                TaskRow(t, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) })
            }
        }

        // ---- upcoming ----
        item {
            SectionHeader(stringResource(R.string.upcoming), action = stringResource(R.string.my_day_timeline)) { nav.navigate(Routes.timeline()) }
        }
        if (s.upcoming.isEmpty() && s.loaded) {
            item {
                DfCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timeline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.no_upcoming), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(s.upcoming, key = { "u${it.id}" }) { t ->
            TaskRow(t, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) }, showDate = false)
        }
        item { Spacer(Modifier.size(Spacing.xl)) }
    }
}
