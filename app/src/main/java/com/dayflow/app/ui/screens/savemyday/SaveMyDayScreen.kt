package com.dayflow.app.ui.screens.savemyday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.ui.components.ColorDot
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.scheduling.DayPlan
import com.dayflow.core.scheduling.DayPlanner
import com.dayflow.core.text.Ar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class SaveMyDayState(
    val plan: DayPlan? = null,
    val acceptedPostpones: Set<Long> = emptySet(),
    val applying: Boolean = false,
    val applied: Boolean = false,
)

/**
 * The plan is computed once from a snapshot and frozen while the user reviews it.
 * Nothing changes in the database until "Apply" is pressed.
 */
class SaveMyDayViewModel(private val date: LocalDate, private val assistant: AssistantService, private val interactor: TaskInteractor) : ViewModel() {
    private val _state = MutableStateFlow(SaveMyDayState())
    val state: StateFlow<SaveMyDayState> = _state.asStateFlow()

    init { recompute() }

    fun recompute() = viewModelScope.launch {
        val ctx = assistant.snapshot()
        val plan = DayPlanner.plan(ctx.tasks, date, LocalDateTime.now(), ctx.settings)
        _state.update { it.copy(plan = plan, acceptedPostpones = plan.suggestPostpone.map { t -> t.id }.toSet(), applied = false) }
    }

    fun togglePostpone(id: Long) = _state.update {
        it.copy(acceptedPostpones = if (id in it.acceptedPostpones) it.acceptedPostpones - id else it.acceptedPostpones + id)
    }

    fun apply() = viewModelScope.launch {
        val plan = _state.value.plan ?: return@launch
        _state.update { it.copy(applying = true) }
        interactor.applyPlan(plan, _state.value.acceptedPostpones)
        _state.update { it.copy(applying = false, applied = true) }
    }
}

@Composable
fun SaveMyDayScreen(nav: NavController, epochDay: Long?) {
    val date = epochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    val vm = dfViewModel(key = "smd_$date") { SaveMyDayViewModel(date, it.assistantService, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current
    LaunchedEffect(s.applied) { if (s.applied) nav.popBackStack() }

    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.save_my_day), onBack = { nav.popBackStack() }) {
            IconButton(onClick = { vm.recompute() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.recompute)) }
        }
        val plan = s.plan
        if (plan == null) {
            CircularProgressIndicator(Modifier.padding(Spacing.xl))
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DfCard(container = if (plan.fitsEverything) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (plan.fitsEverything) Icons.Filled.Bolt else Icons.Filled.Warning, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(if (plan.date == LocalDate.now()) R.string.plan_for_today else R.string.plan_for_date, Ar.date(plan.date)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(plan.summary(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                Text(stringResource(R.string.plan_preview_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            }

            if (plan.blocks.isEmpty() && plan.fixed.isEmpty()) {
                Text(stringResource(R.string.plan_nothing), style = MaterialTheme.typography.bodyMedium)
            }

            if (plan.blocks.isNotEmpty() || plan.fixed.isNotEmpty()) {
                SectionHeader(stringResource(R.string.proposed_schedule))
                val rows = (plan.fixed.map { Triple(it.startAt!!, it.plannedEnd!!, it) }.map { Row4(it.first, it.second, it.third.title, it.third.area, true, "") } +
                    plan.blocks.map {
                        Row4(it.start, it.end, it.task.title, it.task.area, false, if (it.totalParts > 1) stringResource(R.string.part_x_of_y, it.part, it.totalParts) else "")
                    }).sortedBy { it.start }
                rows.forEach { r ->
                    DfCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(AppColors.area(r.area))
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(r.title + if (r.part.isNotEmpty()) " (${r.part})" else "", style = MaterialTheme.typography.bodyLarge)
                                Text("${Ar.time(r.start.toLocalTime())} – ${Ar.time(r.end.toLocalTime())}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (r.fixed) Icon(Icons.Filled.Lock, stringResource(R.string.fixed_time), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (plan.suggestPostpone.isNotEmpty()) {
                SectionHeader(stringResource(R.string.suggest_postpone))
                Text(stringResource(R.string.suggest_postpone_hint), style = MaterialTheme.typography.bodySmall)
                plan.suggestPostpone.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.id in s.acceptedPostpones, onCheckedChange = { vm.togglePostpone(t.id) })
                        Text(t.title, modifier = Modifier.weight(1f))
                        Text(Ar.priority(t.priority), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (plan.atRisk.isNotEmpty()) {
                SectionHeader(stringResource(R.string.at_risk))
                Text(stringResource(R.string.at_risk_hint), style = MaterialTheme.typography.bodySmall, color = extra.danger)
                plan.atRisk.forEach { t ->
                    Text("• ${t.title}" + (t.dueAt?.let { " — ${stringResource(R.string.due_at)} ${Ar.time(it.toLocalTime())}" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (plan.blocked.isNotEmpty()) {
                SectionHeader(stringResource(R.string.blocked_tasks))
                plan.blocked.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyMedium) }
            }
            if (plan.conflicts.isNotEmpty()) {
                SectionHeader(stringResource(R.string.conflicts))
                plan.conflicts.forEach { c ->
                    Text("• «${c.first.title}» ↔ «${c.second.title}» (${Ar.duration(c.overlapMinutes.toInt())})", color = extra.danger, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.plan_recurring_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.lg)) {
                Button(
                    onClick = { vm.apply() },
                    enabled = !s.applying && (plan.blocks.isNotEmpty() || s.acceptedPostpones.isNotEmpty()),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply_plan)) }
                OutlinedButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.cancel)) }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private data class Row4(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val title: String,
    val area: com.dayflow.core.model.LifeArea,
    val fixed: Boolean,
    val part: String,
)
