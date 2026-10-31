package com.dayflow.app.ui.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.DayMask
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.db.HabitEntity
import com.dayflow.app.data.toDomain
import com.dayflow.app.data.weekStart
import com.dayflow.app.notification.ReminderScheduler
import com.dayflow.app.ui.components.ConfirmDialog
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DotsRow
import com.dayflow.app.ui.components.EmptyState
import com.dayflow.app.ui.components.TimePickerDialogDf
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.Habit
import com.dayflow.core.stats.HabitStats
import com.dayflow.core.text.Ar
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class HabitCard(
    val entity: HabitEntity,
    val habit: Habit,
    val doneToday: Boolean,
    val streak: Int,
    val best: Int,
    val consistency30: Double,
    val weekCount: Int,
    val last7: List<Boolean>,
    val doneDates: Set<LocalDate>,
)

data class HabitsState(val cards: List<HabitCard> = emptyList(), val today: LocalDate = LocalDate.now(), val loaded: Boolean = false)

class HabitsViewModel(private val repo: HabitRepository, settings: SettingsRepository, private val scheduler: ReminderScheduler) : ViewModel() {
    val state: StateFlow<HabitsState> = combine(repo.habitEntities, repo.logs, settings.settings) { habits, logs, s ->
        val today = LocalDate.now()
        val weekStart = LocalTimes.startOfWeek(today, s.weekStart)
        HabitsState(
            cards = habits.filter { !it.archived }.map { e ->
                val h = e.toDomain()
                val dates = logs.filter { it.habitId == e.id }.map { it.date }.toSet()
                HabitCard(
                    e, h, today in dates,
                    HabitStats.currentStreak(h, dates, today),
                    HabitStats.bestStreak(h, dates, today),
                    HabitStats.consistency(h, dates, today.minusDays(29), today, today),
                    HabitStats.completionsInRange(dates, weekStart, weekStart.plusDays(6)),
                    (6L downTo 0L).map { today.minusDays(it) in dates },
                    dates,
                )
            },
            today = today,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun setDone(id: Long, date: LocalDate, done: Boolean) = viewModelScope.launch { repo.setDone(id, date, done) }

    fun save(h: HabitEntity) = viewModelScope.launch {
        repo.save(h)
        scheduler.rescheduleHabits()
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id)
        scheduler.rescheduleHabits()
    }

    /** Optional examples - only the ones the user ticks are created. */
    fun addSamples(names: List<Pair<String, String>>) = viewModelScope.launch {
        names.forEachIndexed { i, (icon, name) ->
            repo.save(HabitEntity(name = name, icon = icon, createdOn = LocalDate.now().toEpochDay(), sortOrder = i))
        }
    }
}

@Composable
private fun sampleHabits(): List<Pair<String, String>> = listOf(
    "📖" to stringResource(R.string.habit_reading),
    "🏃" to stringResource(R.string.habit_exercise),
    "💧" to stringResource(R.string.habit_water),
    "🎓" to stringResource(R.string.habit_study),
    "🌙" to stringResource(R.string.habit_sleep_early),
    "🗓" to stringResource(R.string.habit_planning),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsScreen(@Suppress("UNUSED_PARAMETER") nav: NavController) {
    val vm = dfViewModel { HabitsViewModel(it.habitRepository, it.settingsRepository, it.reminderScheduler) }
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<HabitEntity?>(null) }
    var showSamples by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { Text(stringResource(R.string.nav_habits), style = MaterialTheme.typography.headlineSmall) }
            if (s.loaded && s.cards.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.Loop, stringResource(R.string.no_habits_title), stringResource(R.string.no_habits_msg)) {
                        OutlinedButton(onClick = { showSamples = true }) { Text(stringResource(R.string.add_samples)) }
                    }
                }
            }
            items(s.cards, key = { it.entity.id }) { c ->
                DfCard(onClick = { expanded = if (expanded == c.entity.id) null else c.entity.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.habit.icon, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(c.habit.name, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Text(
                                    stringResource(R.string.streak_days, c.streak) + " • " + stringResource(R.string.consistency_pct, (c.consistency30 * 100).toInt()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val scheduledToday = c.habit.isScheduledOn(s.today)
                        IconButton(onClick = { vm.setDone(c.entity.id, s.today, !c.doneToday) }) {
                            Icon(
                                if (c.doneToday) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                stringResource(if (c.doneToday) R.string.cd_habit_undo else R.string.cd_habit_done, c.habit.name),
                                tint = if (c.doneToday) extra.success else if (scheduledToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                        DotsRow(c.last7, extra.success)
                        Spacer(Modifier.weight(1f))
                        c.habit.time?.let { Text(Ar.time(it), style = MaterialTheme.typography.labelSmall) }
                    }
                    c.habit.weeklyTarget?.let { target ->
                        Text(stringResource(R.string.weekly_goal_progress, c.weekCount, target), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        LinearProgressIndicator(progress = { (c.weekCount.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                    if (expanded == c.entity.id) {
                        Text(stringResource(R.string.best_streak, c.best), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
                        MonthLog(c, s.today) { d, done -> vm.setDone(c.entity.id, d, done) }
                        Row {
                            TextButton(onClick = { editing = c.entity }) { Icon(Icons.Filled.Edit, null); Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { confirmDelete = c.entity }) { Icon(Icons.Filled.Delete, null); Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
            if (s.cards.isNotEmpty()) {
                item {
                    TextButton(onClick = { showSamples = true }) { Text(stringResource(R.string.add_samples)) }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = HabitEntity(name = "", createdOn = LocalDate.now().toEpochDay()) },
            containerColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.lg),
        ) { Icon(Icons.Filled.Add, stringResource(R.string.add_habit)) }
    }

    editing?.let { h -> HabitDialog(h, onDismiss = { editing = null }) { vm.save(it); editing = null } }
    confirmDelete?.let { h ->
        ConfirmDialog(
            stringResource(R.string.delete_habit_q), stringResource(R.string.delete_habit_msg, h.name), stringResource(R.string.delete),
            onConfirm = { vm.delete(h.id) }, onDismiss = { confirmDelete = null }, destructive = true,
        )
    }
    if (showSamples) {
        val samples = sampleHabits()
        var picked by remember { mutableStateOf(setOf<Int>()) }
        AlertDialog(
            onDismissRequest = { showSamples = false },
            title = { Text(stringResource(R.string.add_samples)) },
            text = {
                Column {
                    Text(stringResource(R.string.samples_hint), style = MaterialTheme.typography.bodySmall)
                    samples.forEachIndexed { i, (icon, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = i in picked, onCheckedChange = { picked = if (it) picked + i else picked - i })
                            Text("$icon $name")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.addSamples(picked.sorted().map { samples[it] }); showSamples = false }, enabled = picked.isNotEmpty()) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = { TextButton(onClick = { showSamples = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Monthly history grid; tap a past day to fix a missed check-in. */
@Composable
private fun MonthLog(c: HabitCard, today: LocalDate, onToggle: (LocalDate, Boolean) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val extra = LocalExtraColors.current
    Column(Modifier.padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text("${Ar.monthName(month.monthValue)} ${month.year}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { if (month < YearMonth.from(today)) month = month.plusMonths(1) }) { Text("›") }
        }
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        days.chunked(7).forEach { week ->
            Row {
                week.forEach { d ->
                    val done = d in c.doneDates
                    val scheduled = c.habit.isScheduledOn(d)
                    val future = d.isAfter(today)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    done -> extra.success
                                    scheduled && !future -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                },
                            )
                            .clickable(enabled = !future) { onToggle(d, !done) }
                            .semantics { contentDescription = "${Ar.date(d)}: ${if (done) "✓" else "—"}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                            color = if (done) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitDialog(initial: HabitEntity, onDismiss: () -> Unit, onSave: (HabitEntity) -> Unit) {
    var h by remember { mutableStateOf(initial) }
    var pickTime by remember { mutableStateOf(false) }
    val days = DayMask.fromMask(h.daysMask)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.add_habit else R.string.edit_habit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(h.icon, { h = h.copy(icon = it.take(2)) }, modifier = Modifier.width(64.dp), singleLine = true)
                    Spacer(Modifier.width(Spacing.sm))
                    OutlinedTextField(h.name, { h = h.copy(name = it) }, label = { Text(stringResource(R.string.habit_name)) }, singleLine = true)
                }
                Text(stringResource(R.string.habit_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).forEach { d ->
                        FilterChip(
                            selected = days.isEmpty() || d in days,
                            onClick = {
                                val base = days.ifEmpty { DayOfWeek.entries.toSet() }
                                val next = if (d in base) base - d else base + d
                                h = h.copy(daysMask = if (next.size == 7 || next.isEmpty()) 0 else DayMask.toMask(next))
                            },
                            label = { Text(Ar.shortDayName(d)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.timeMinute?.let { stringResource(R.string.habit_time_x, Ar.time(LocalTime.of(it / 60, it % 60))) } ?: stringResource(R.string.habit_any_time),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.set_time)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.habit_reminder), modifier = Modifier.weight(1f))
                    Switch(checked = h.reminderEnabled, onCheckedChange = { h = h.copy(reminderEnabled = it) }, enabled = h.timeMinute != null)
                }
                OutlinedTextField(
                    value = h.weeklyTarget?.toString() ?: "",
                    onValueChange = { v -> h = h.copy(weeklyTarget = v.filter(Char::isDigit).take(2).toIntOrNull()?.takeIf { it in 1..21 }) },
                    label = { Text(stringResource(R.string.weekly_goal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(h.copy(name = h.name.trim())) }, enabled = h.name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickTime) {
        TimePickerDialogDf(h.timeMinute?.let { LocalTime.of(it / 60, it % 60) }, { pickTime = false }) { t ->
            h = h.copy(timeMinute = t.hour * 60 + t.minute)
        }
    }
}
