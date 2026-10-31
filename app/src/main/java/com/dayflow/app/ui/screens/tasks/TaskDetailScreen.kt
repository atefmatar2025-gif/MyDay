package com.dayflow.app.ui.screens.tasks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.HistoryRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.TaskAttachmentEntity
import com.dayflow.app.data.db.TaskCategoryEntity
import com.dayflow.app.data.db.TaskReminderEntity
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.domain.minuteTicker
import com.dayflow.app.ui.components.ConfirmDialog
import com.dayflow.app.ui.components.DatePickerDialogDf
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.PriorityBadge
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.SmallChip
import com.dayflow.app.ui.components.TaskRow
import com.dayflow.app.ui.components.TimePickerDialogDf
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.text.Ar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class TaskDetailState(
    val task: Task? = null,
    val category: TaskCategoryEntity? = null,
    val subtasks: List<Task> = emptyList(),
    val dependencies: List<Task> = emptyList(),
    val parent: Task? = null,
    val reminders: List<TaskReminderEntity> = emptyList(),
    val attachments: List<TaskAttachmentEntity> = emptyList(),
    val history: List<ProductivityRecord> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val loaded: Boolean = false,
)

class TaskDetailViewModel(
    private val id: Long,
    repo: TaskRepository,
    history: HistoryRepository,
    private val interactor: TaskInteractor,
) : ViewModel() {
    val state: StateFlow<TaskDetailState> = combine(
        repo.tasks, repo.categories, combine(repo.observeReminders(id), repo.observeAttachments(id)) { r, a -> r to a },
        history.records, minuteTicker(),
    ) { tasks, cats, (reminders, attachments), records, now ->
        val t = tasks.firstOrNull { it.id == id }
        TaskDetailState(
            task = t,
            category = cats.firstOrNull { it.id == t?.categoryId },
            subtasks = tasks.filter { it.parentId == id },
            dependencies = tasks.filter { it.id in (t?.dependsOn ?: emptyList()) },
            parent = t?.parentId?.let { p -> tasks.firstOrNull { it.id == p } },
            reminders = reminders,
            attachments = attachments,
            history = records.filter { it.taskId == id }.sortedByDescending { it.at },
            now = now,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailState())

    fun start() = viewModelScope.launch { interactor.start(id) }
    fun toggle(t: Task) = viewModelScope.launch { if (t.status.isOpen) interactor.complete(t.id) else interactor.reopen(t.id) }
    fun postpone(to: LocalDateTime) = viewModelScope.launch { interactor.postpone(id, to) }
    fun cancel() = viewModelScope.launch { interactor.cancel(id) }
    fun archive(archived: Boolean) = viewModelScope.launch { interactor.setArchived(id, archived) }
    fun delete(done: () -> Unit) = viewModelScope.launch { interactor.delete(id); done() }
    fun duplicate(open: (Long) -> Unit) = viewModelScope.launch { interactor.duplicate(id)?.let(open) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(nav: NavController, id: Long) {
    val vm = dfViewModel(key = "detail_$id") { TaskDetailViewModel(id, it.taskRepository, it.historyRepository, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var postponeDialog by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val extra = LocalExtraColors.current

    val task = s.task
    Column(Modifier.fillMaxSize()) {
        DfTopBar(task?.title ?: "", onBack = { nav.popBackStack() }) {
            if (task != null) {
                IconButton(onClick = { nav.navigate(Routes.editor(id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; vm.duplicate { nid -> nav.navigate(Routes.task(nid)) } })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (task.archived) R.string.unarchive else R.string.archive)) },
                            leadingIcon = { Icon(if (task.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, null) },
                            onClick = { menu = false; vm.archive(!task.archived) },
                        )
                        if (task.status.isOpen) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.cancel_task)) }, leadingIcon = { Icon(Icons.Filled.Block, null) },
                                onClick = { menu = false; confirmCancel = true })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
        if (task == null) {
            if (s.loaded) Text(stringResource(R.string.task_not_found), Modifier.padding(Spacing.screen))
            return@Column
        }
        val status = task.effectiveStatus(s.now)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallChip(Ar.status(status), if (status == TaskStatus.OVERDUE) extra.danger else MaterialTheme.colorScheme.primary)
                PriorityBadge(task.priority)
                s.category?.let { SmallChip(it.name, AppColors.parse(it.colorHex)) }
                if (task.fixedTime) SmallChip(stringResource(R.string.fixed_time))
                if (task.archived) SmallChip(stringResource(R.string.archived))
                task.tags.forEach { SmallChip("#$it") }
            }

            // ---- actions ----
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (task.status.isOpen) {
                    Button(onClick = { vm.toggle(task) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_complete))
                    }
                    FilledTonalButton(onClick = { vm.start(); nav.navigate(Routes.focus(task.id)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.start_focus))
                    }
                    OutlinedButton(onClick = { postponeDialog = true }) { Icon(Icons.Filled.Snooze, stringResource(R.string.postpone)) }
                } else {
                    OutlinedButton(onClick = { vm.toggle(task) }) { Text(stringResource(R.string.reopen)) }
                }
            }

            DfCard {
                InfoLine(stringResource(R.string.start_at), task.startAt?.let { Ar.dateTime(it, s.now.toLocalDate()) })
                InfoLine(stringResource(R.string.due_at), task.dueAt?.let { Ar.dateTime(it, s.now.toLocalDate()) })
                InfoLine(stringResource(R.string.estimated_duration), task.estimatedMinutes?.let { Ar.duration(it) })
                InfoLine(stringResource(R.string.actual_time), task.actualMinutes?.let { Ar.duration(it) })
                InfoLine(stringResource(R.string.recurrence), task.recurrence?.let { stringResource(R.string.recurring) })
                if (task.postponeCount > 0) InfoLine(stringResource(R.string.postponed_times), task.postponeCount.toString())
                InfoLine(
                    stringResource(R.string.created_at),
                    Ar.dateTime(LocalDateTime.ofInstant(task.createdAt, ZoneId.systemDefault()), s.now.toLocalDate()),
                )
            }
            if (task.description.isNotBlank()) {
                SectionHeader(stringResource(R.string.description))
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (task.notes.isNotBlank()) {
                SectionHeader(stringResource(R.string.notes))
                Text(task.notes, style = MaterialTheme.typography.bodyMedium)
            }

            // ---- reminders ----
            SectionHeader(stringResource(R.string.reminders))
            if (s.reminders.isEmpty()) Text(stringResource(R.string.no_reminders), style = MaterialTheme.typography.bodySmall)
            s.reminders.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (r.mode == "ALARM") Icons.Filled.Alarm else Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(if (r.offsetMinutes == 0) stringResource(R.string.at_time) else stringResource(R.string.before_x, Ar.duration(r.offsetMinutes)))
                        Text(
                            r.nextTriggerAt?.let {
                                stringResource(R.string.next_ring, Ar.dateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()), s.now.toLocalDate()))
                            } ?: stringResource(if (r.enabled) R.string.not_scheduled else R.string.disabled),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- relations ----
            s.parent?.let { p ->
                SectionHeader(stringResource(R.string.parent_task))
                TaskRow(p, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) })
            }
            SectionHeader(stringResource(R.string.subtasks), action = stringResource(R.string.add)) { nav.navigate(Routes.editor(parent = id)) }
            if (s.subtasks.isNotEmpty()) {
                val done = s.subtasks.count { !it.status.isOpen }
                Text(stringResource(R.string.done_of_total, done, s.subtasks.size), style = MaterialTheme.typography.bodySmall)
            }
            s.subtasks.forEach { st -> TaskRow(st, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) }) }
            if (s.dependencies.isNotEmpty()) {
                SectionHeader(stringResource(R.string.depends_on))
                s.dependencies.forEach { dep -> TaskRow(dep, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) }) }
            }

            // ---- attachments ----
            if (s.attachments.isNotEmpty()) {
                SectionHeader(stringResource(R.string.attachments))
                s.attachments.forEach { a ->
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(a.uri), a.mimeType ?: "*/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }
                    }) {
                        Icon(Icons.Filled.AttachFile, null); Spacer(Modifier.width(4.dp)); Text(a.displayName)
                    }
                }
            }

            // ---- history ----
            if (s.history.isNotEmpty()) {
                SectionHeader(stringResource(R.string.task_history))
                s.history.take(20).forEach { r ->
                    Row {
                        Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.sm))
                        Text("${eventLabel(r.event)} — ${Ar.dateTime(r.at, s.now.toLocalDate())}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.padding(Spacing.xl))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_task_q), stringResource(R.string.delete_task_msg), stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
    if (confirmCancel) {
        ConfirmDialog(
            stringResource(R.string.cancel_task_q), stringResource(R.string.cancel_task_msg), stringResource(R.string.cancel_task),
            onConfirm = { vm.cancel() }, onDismiss = { confirmCancel = false },
        )
    }
    if (postponeDialog && task != null) {
        val base = task.startAt ?: LocalDateTime.now()
        AlertDialog(
            onDismissRequest = { postponeDialog = false },
            title = { Text(stringResource(R.string.postpone)) },
            text = {
                Column {
                    val options = listOf(
                        stringResource(R.string.postpone_1h) to LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                        stringResource(R.string.postpone_evening) to LocalDateTime.now().toLocalDate().atTime(20, 0),
                        stringResource(R.string.postpone_tomorrow) to base.plusDays(1).let { if (it.isBefore(LocalDateTime.now())) LocalDateTime.now().plusDays(1) else it },
                        stringResource(R.string.postpone_week) to base.plusWeeks(1),
                    )
                    options.forEach { (label, at) ->
                        TextButton(onClick = { vm.postpone(at); postponeDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("$label — ${Ar.dateTime(at, LocalDateTime.now().toLocalDate())}")
                        }
                    }
                    TextButton(onClick = { pickDate = true; postponeDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.postpone_pick))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { postponeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickDate) {
        DatePickerDialogDf(task?.startAt?.toLocalDate(), { pickDate = false }) { pickedDate = it }
    }
    pickedDate?.let { date ->
        TimePickerDialogDf(task?.startAt?.toLocalTime(), { pickedDate = null }) { t -> vm.postpone(date.atTime(t)) }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun eventLabel(e: HistoryEvent): String = stringResource(
    when (e) {
        HistoryEvent.CREATED -> R.string.ev_created
        HistoryEvent.STARTED -> R.string.ev_started
        HistoryEvent.COMPLETED -> R.string.ev_completed
        HistoryEvent.POSTPONED -> R.string.ev_postponed
        HistoryEvent.CANCELLED -> R.string.ev_cancelled
        HistoryEvent.RESCHEDULED -> R.string.ev_rescheduled
        HistoryEvent.DELETED -> R.string.ev_deleted
        HistoryEvent.REOPENED -> R.string.ev_reopened
    },
)
