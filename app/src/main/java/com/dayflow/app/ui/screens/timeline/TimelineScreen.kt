package com.dayflow.app.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.ui.components.ColorDot
import com.dayflow.app.ui.components.ConfirmDialog
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.TaskRow
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.ConflictDetector
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.MinuteRange
import com.dayflow.core.model.Task
import com.dayflow.core.text.Ar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A positioned block in the timeline. Minutes are relative to midnight of the shown day. */
data class TimelineBlock(val task: Task, val startMin: Int, val endMin: Int, val lane: Int, val lanes: Int, val conflicting: Boolean)
data class TimelineGap(val startMin: Int, val endMin: Int)

data class TimelineState(
    val date: LocalDate = LocalDate.now(),
    val fromMin: Int = 7 * 60,
    val toMin: Int = 23 * 60,
    val blocks: List<TimelineBlock> = emptyList(),
    val rest: List<MinuteRange> = emptyList(),
    val gaps: List<TimelineGap> = emptyList(),
    val unscheduled: List<Task> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val conflicts: Int = 0,
)

class TimelineViewModel(initial: LocalDate, assistant: AssistantService, private val interactor: TaskInteractor) : ViewModel() {
    private val date = MutableStateFlow(initial)

    val state: StateFlow<TimelineState> = combine(assistant.context, date) { ctx, d ->
        val dayStart = ctx.settings.dayStart.hour * 60 + ctx.settings.dayStart.minute
        val dayEnd = (ctx.settings.dayEnd.hour * 60 + ctx.settings.dayEnd.minute).coerceAtLeast(dayStart + 60)
        val occ = TaskOccurrences.forDate(ctx.tasks, d).filter { it.status.name != "CANCELLED" }
        val timed = occ.filter { it.startAt != null && it.startAt!!.toLocalDate() == d }
        val conflictIds = ConflictDetector.find(timed).flatMap { listOf(it.first.id, it.second.id) }.toSet()
        // Lane assignment: greedy, so overlapping blocks sit side by side.
        val sorted = timed.sortedBy { it.startAt }
        val laneEnds = mutableListOf<Int>()
        val placed = sorted.map { t ->
            val s = minuteOf(t.startAt!!, d)
            val e = (s + (t.estimatedMinutes ?: Task.DEFAULT_BLOCK_MINUTES)).coerceAtMost(24 * 60)
            var lane = laneEnds.indexOfFirst { it <= s }
            if (lane == -1) { laneEnds += e; lane = laneEnds.lastIndex } else laneEnds[lane] = e
            Triple(t, s to e, lane)
        }
        val blocks = placed.map { (t, range, lane) ->
            val overlapping = placed.filter { it.second.first < range.second && range.first < it.second.second }
            TimelineBlock(t, range.first, range.second, lane, (overlapping.maxOf { it.third } + 1).coerceAtLeast(1), t.id in conflictIds)
        }
        val from = minOf(dayStart, blocks.minOfOrNull { it.startMin } ?: dayStart)
        val to = maxOf(dayEnd, blocks.maxOfOrNull { it.endMin } ?: dayEnd)
        // Free gaps (>= 15 min) between busy intervals inside the waking day.
        val busy = (blocks.map { it.startMin to it.endMin } + ctx.settings.restPeriods.map { it.startMinute to it.endMinute }).sortedBy { it.first }
        val gaps = mutableListOf<TimelineGap>()
        var cursor = dayStart
        busy.forEach { (s, e) ->
            if (s - cursor >= 15 && s <= dayEnd) gaps += TimelineGap(cursor, minOf(s, dayEnd))
            cursor = maxOf(cursor, e)
        }
        if (dayEnd - cursor >= 15) gaps += TimelineGap(cursor, dayEnd)
        TimelineState(
            date = d, fromMin = (from / 60) * 60, toMin = ((to + 59) / 60) * 60, blocks = blocks,
            rest = ctx.settings.restPeriods, gaps = gaps,
            unscheduled = occ.filter { it.startAt == null || it.startAt!!.toLocalDate() != d },
            now = ctx.now, conflicts = conflictIds.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineState(date = initial))

    private fun minuteOf(dt: LocalDateTime, d: LocalDate) = ChronoUnit.MINUTES.between(d.atStartOfDay(), dt).toInt().coerceIn(0, 24 * 60)

    fun shift(days: Long) = date.update { it.plusDays(days) }
    fun move(task: Task, newStart: LocalDateTime) = viewModelScope.launch { interactor.moveTo(task.id, newStart) }
    fun toggle(t: Task) = viewModelScope.launch { if (t.status.isOpen) interactor.complete(t.id) else interactor.reopen(t.id) }
}

private const val DP_PER_MINUTE = 1.3f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(nav: NavController, epochDay: Long?) {
    val initial = epochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    val vm = dfViewModel(key = "timeline_$initial") { TimelineViewModel(initial, it.assistantService, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val pxPerMin = with(density) { DP_PER_MINUTE.dp.toPx() }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var pendingMove by remember { mutableStateOf<Pair<Task, LocalDateTime>?>(null) }
    val extra = LocalExtraColors.current

    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.my_day_timeline), onBack = { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate(Routes.saveMyDay(s.date.toEpochDay())) }) { Icon(Icons.Filled.Bolt, stringResource(R.string.save_my_day)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.sm)) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.previous)) }
            Text(Ar.date(s.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.next)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            // legend
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(LifeArea.WORK, LifeArea.STUDY, LifeArea.WORSHIP, LifeArea.FAMILY, LifeArea.HEALTH, LifeArea.APPOINTMENT, LifeArea.REST, LifeArea.PERSONAL).forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(AppColors.area(it), 8.dp); Spacer(Modifier.width(4.dp))
                        Text(Ar.area(it), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                stringResource(R.string.timeline_hint) + if (s.conflicts > 0) "  " + stringResource(R.string.timeline_conflicts, s.conflicts) else "",
                style = MaterialTheme.typography.bodySmall, color = if (s.conflicts > 0) extra.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            val totalMin = s.toMin - s.fromMin
            Row(Modifier.fillMaxWidth().height((totalMin * DP_PER_MINUTE).dp)) {
                // hour labels (right side in RTL)
                Box(Modifier.width(52.dp).fillMaxSize()) {
                    for (h in (s.fromMin / 60)..(s.toMin / 60)) {
                        Text(
                            Ar.time(java.time.LocalTime.of(h % 24, 0)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(y = ((h * 60 - s.fromMin) * DP_PER_MINUTE).dp - 6.dp),
                        )
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                    val laneWidth = maxWidth
                    for (h in (s.fromMin / 60)..(s.toMin / 60)) {
                        HorizontalDivider(Modifier.offset(y = ((h * 60 - s.fromMin) * DP_PER_MINUTE).dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    s.rest.forEach { r ->
                        Box(
                            Modifier.offset(y = ((r.startMinute - s.fromMin) * DP_PER_MINUTE).dp).fillMaxWidth()
                                .height((r.length * DP_PER_MINUTE).dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.rest_period), style = MaterialTheme.typography.labelSmall) }
                    }
                    s.gaps.forEach { g ->
                        Box(
                            Modifier.offset(y = ((g.startMin - s.fromMin) * DP_PER_MINUTE).dp).fillMaxWidth().height(((g.endMin - g.startMin) * DP_PER_MINUTE).dp)
                                .clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.free_gap, Ar.duration(g.endMin - g.startMin)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (s.date == s.now.toLocalDate()) {
                        val nowMin = s.now.hour * 60 + s.now.minute
                        if (nowMin in s.fromMin..s.toMin) {
                            Box(Modifier.offset(y = ((nowMin - s.fromMin) * DP_PER_MINUTE).dp).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.secondary))
                        }
                    }
                    s.blocks.forEach { b ->
                        val w = laneWidth / b.lanes
                        val isDragging = dragging == b.task.id
                        val color = AppColors.area(b.task.area)
                        val desc = "${b.task.title}، ${Ar.time(b.task.startAt!!.toLocalTime())}، ${Ar.duration(b.endMin - b.startMin)}"
                        Box(
                            Modifier
                                // offset {} mirrors x in RTL, so lane 0 sits at the start (right) edge.
                                .align(Alignment.TopStart)
                                .offset { IntOffset((w * b.lane).roundToPx(), ((b.startMin - s.fromMin) * pxPerMin + if (isDragging) dragPx else 0f).roundToInt()) }
                                .width(w - 2.dp)
                                .height(((b.endMin - b.startMin).coerceAtLeast(20) * DP_PER_MINUTE).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color.copy(alpha = if (isDragging) 0.95f else 0.85f))
                                .let { if (b.conflicting) it.border(2.dp, extra.danger, RoundedCornerShape(10.dp)) else it }
                                .semantics { contentDescription = desc }
                                .pointerInput(b.task.id, b.task.recurrence) {
                                    if (b.task.recurrence != null) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragging = b.task.id; dragPx = 0f },
                                        onDragEnd = {
                                            val deltaMin = ((dragPx / pxPerMin) / 15f).roundToInt() * 15
                                            if (deltaMin != 0) pendingMove = b.task to b.task.startAt!!.plusMinutes(deltaMin.toLong())
                                            dragging = null; dragPx = 0f
                                        },
                                        onDragCancel = { dragging = null; dragPx = 0f },
                                        onDrag = { change, amount -> change.consume(); dragPx += amount.y },
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Column {
                                Text(b.task.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${Ar.time(b.task.startAt!!.toLocalTime())} • ${Ar.duration(b.endMin - b.startMin)}",
                                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            if (s.unscheduled.isNotEmpty()) {
                SectionHeader(stringResource(R.string.unscheduled))
                s.unscheduled.forEach { t ->
                    TaskRow(t, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) }, modifier = Modifier.padding(bottom = Spacing.sm))
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    pendingMove?.let { (task, at) ->
        ConfirmDialog(
            title = stringResource(R.string.move_task_q),
            message = stringResource(R.string.move_task_msg, task.title, Ar.time(at.toLocalTime())),
            confirm = stringResource(R.string.move),
            onConfirm = { vm.move(task, at) },
            onDismiss = { pendingMove = null },
        )
    }
}
