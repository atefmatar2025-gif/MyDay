package com.dayflow.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.TaskCategoryEntity
import com.dayflow.app.data.weekStart
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.domain.minuteTicker
import com.dayflow.app.ui.components.EmptyState
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.TaskRow
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.Priority
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.text.Ar
import com.dayflow.core.text.ArabicText
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class TaskView { LIST, TODAY, WEEK, MONTH, CATEGORY, PRIORITY }
enum class TaskSort { DUE, PRIORITY, CREATED, TITLE }
enum class StatusFilter { OPEN, DONE, ALL }

data class TaskFilters(
    val query: String = "",
    val view: TaskView = TaskView.LIST,
    val sort: TaskSort = TaskSort.DUE,
    val status: StatusFilter = StatusFilter.OPEN,
    val priorities: Set<Priority> = emptySet(),
    val categoryId: Long? = null,
    val tag: String? = null,
    val showArchived: Boolean = false,
)

data class TaskSection(val title: String, val tasks: List<Task>)

data class TasksState(
    val filters: TaskFilters = TaskFilters(),
    val sections: List<TaskSection> = emptyList(),
    val categories: List<TaskCategoryEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val total: Int = 0,
)

class TasksViewModel(
    private val repo: TaskRepository,
    settings: SettingsRepository,
    private val interactor: TaskInteractor,
) : ViewModel() {
    private val filters = MutableStateFlow(TaskFilters())

    val state: StateFlow<TasksState> = combine(repo.tasks, repo.categories, repo.tags, filters, combine(settings.settings, minuteTicker()) { s, n -> s to n }) { tasks, cats, tags, f, (s, now) ->
        val today = now.toLocalDate()
        val q = ArabicText.normalize(f.query)
        fun matches(t: Task): Boolean {
            if (t.archived != f.showArchived) return false
            val st = t.effectiveStatus(now)
            when (f.status) {
                StatusFilter.OPEN -> if (!st.isOpen) return false
                StatusFilter.DONE -> if (st != TaskStatus.COMPLETED) return false
                StatusFilter.ALL -> Unit
            }
            if (f.priorities.isNotEmpty() && t.priority !in f.priorities) return false
            if (f.categoryId != null && t.categoryId != f.categoryId) return false
            if (f.tag != null && f.tag !in t.tags) return false
            if (q.isNotEmpty()) {
                val hay = ArabicText.normalize(t.title + " " + t.description + " " + t.notes + " " + t.tags.joinToString(" "))
                if (!hay.contains(q)) return false
            }
            return true
        }
        val comparator: Comparator<Task> = when (f.sort) {
            TaskSort.DUE -> compareBy<Task> { it.dueAt ?: it.startAt ?: LocalDateTime.MAX }.thenByDescending { it.priority.weight }
            TaskSort.PRIORITY -> compareByDescending<Task> { it.priority.weight }.thenBy { it.dueAt ?: it.startAt ?: LocalDateTime.MAX }
            TaskSort.CREATED -> compareByDescending { it.createdAt }
            TaskSort.TITLE -> compareBy { it.title }
        }
        fun dayRange(from: java.time.LocalDate, to: java.time.LocalDate): List<TaskSection> {
            val out = mutableListOf<TaskSection>()
            var d = from
            while (!d.isAfter(to)) {
                val list = TaskOccurrences.forDate(tasks, d).filter(::matches).sortedWith(comparator)
                if (list.isNotEmpty()) out += TaskSection(if (d == today) "${Ar.dayName(d.dayOfWeek)} — ${Ar.shortDate(d)} ★" else Ar.date(d), list)
                d = d.plusDays(1)
            }
            return out
        }
        val filtered = tasks.filter(::matches).sortedWith(comparator)
        val sections = when (f.view) {
            TaskView.LIST -> {
                val overdue = filtered.filter { it.isOverdue(now) }
                val rest = filtered - overdue.toSet()
                listOfNotNull(
                    overdue.takeIf { it.isNotEmpty() }?.let { TaskSection("⚠ ${Ar.status(TaskStatus.OVERDUE)}", it) },
                    rest.takeIf { it.isNotEmpty() }?.let { TaskSection("", it) },
                )
            }
            TaskView.TODAY -> dayRange(today, today)
            TaskView.WEEK -> {
                val start = LocalTimes.startOfWeek(today, s.weekStart)
                dayRange(start, start.plusDays(6))
            }
            TaskView.MONTH -> dayRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            TaskView.CATEGORY -> {
                val byCat = filtered.groupBy { it.categoryId }
                cats.mapNotNull { c -> byCat[c.id]?.let { TaskSection(c.name, it) } } +
                    listOfNotNull(byCat[null]?.let { TaskSection("—", it) })
            }
            TaskView.PRIORITY -> Priority.entries.sortedByDescending { it.weight }
                .mapNotNull { p -> filtered.filter { it.priority == p }.takeIf { it.isNotEmpty() }?.let { TaskSection(Ar.priority(p), it) } }
        }
        TasksState(f, sections, cats, tags.map { it.name }, now, sections.sumOf { it.tasks.size })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun update(transform: (TaskFilters) -> TaskFilters) = filters.update(transform)

    fun toggle(task: Task) = viewModelScope.launch {
        if (task.status.isOpen) interactor.complete(task.id) else interactor.reopen(task.id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(nav: NavController) {
    val vm = dfViewModel { TasksViewModel(it.taskRepository, it.settingsRepository, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(stringResource(R.string.nav_tasks), style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = s.filters.query,
                        onValueChange = { q -> vm.update { it.copy(query = q) } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_tasks)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TaskSort.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sortLabel(opt)) + if (s.filters.sort == opt) " ✓" else "") },
                                    onClick = { vm.update { it.copy(sort = opt) }; sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, stringResource(R.string.filter)) }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(TaskView.entries) { v ->
                        FilterChip(
                            selected = s.filters.view == v,
                            onClick = { vm.update { it.copy(view = v) } },
                            label = { Text(stringResource(viewLabel(v))) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = false, onClick = { nav.navigate(Routes.timeline()) },
                            label = { Text(stringResource(R.string.view_timeline)) },
                            leadingIcon = { Icon(Icons.Filled.Timeline, null) },
                        )
                    }
                }
            }
            if (s.total == 0) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.List,
                        stringResource(if (s.filters.query.isBlank()) R.string.no_tasks_title else R.string.no_results_title),
                        stringResource(if (s.filters.query.isBlank()) R.string.no_tasks_msg else R.string.no_results_msg),
                    )
                }
            }
            s.sections.forEachIndexed { index, section ->
                if (section.title.isNotEmpty()) item(key = "h_$index") { SectionHeader(section.title + "  (${section.tasks.size})") }
                items(section.tasks, key = { "s${index}_${it.id}_${it.startAt}" }) { t ->
                    TaskRow(
                        t, s.now, onToggle = { vm.toggle(it) }, onClick = { nav.navigate(Routes.task(it.id)) },
                        showDate = s.filters.view !in setOf(TaskView.TODAY, TaskView.WEEK, TaskView.MONTH),
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SmallFloatingActionButton(onClick = { nav.navigate(Routes.editor(voice = true)) }) {
                Icon(Icons.Filled.Mic, stringResource(R.string.voice_to_task))
            }
            FloatingActionButton(onClick = { nav.navigate(Routes.editor()) }, containerColor = MaterialTheme.colorScheme.secondary) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add_task))
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.filter), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.status), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusFilter.entries.forEach { st ->
                        FilterChip(
                            selected = s.filters.status == st, onClick = { vm.update { it.copy(status = st) } },
                            label = {
                                Text(stringResource(when (st) { StatusFilter.OPEN -> R.string.filter_open; StatusFilter.DONE -> R.string.filter_done; StatusFilter.ALL -> R.string.filter_all }))
                            },
                        )
                    }
                }
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Priority.entries.sortedByDescending { it.weight }.forEach { p ->
                        FilterChip(
                            selected = p in s.filters.priorities,
                            onClick = { vm.update { f -> f.copy(priorities = if (p in f.priorities) f.priorities - p else f.priorities + p) } },
                            label = { Text(Ar.priority(p)) },
                        )
                    }
                }
                Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item {
                        FilterChip(selected = s.filters.categoryId == null, onClick = { vm.update { it.copy(categoryId = null) } }, label = { Text(stringResource(R.string.filter_all)) })
                    }
                    items(s.categories, key = { it.id }) { c ->
                        FilterChip(selected = s.filters.categoryId == c.id, onClick = { vm.update { it.copy(categoryId = c.id) } }, label = { Text(c.name) })
                    }
                }
                if (s.tags.isNotEmpty()) {
                    Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        item { FilterChip(selected = s.filters.tag == null, onClick = { vm.update { it.copy(tag = null) } }, label = { Text(stringResource(R.string.filter_all)) }) }
                        items(s.tags) { tag ->
                            FilterChip(selected = s.filters.tag == tag, onClick = { vm.update { it.copy(tag = tag) } }, label = { Text("#$tag") })
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.show_archived), modifier = Modifier.weight(1f))
                    Switch(checked = s.filters.showArchived, onCheckedChange = { v -> vm.update { it.copy(showArchived = v) } })
                }
                TextButton(onClick = { vm.update { TaskFilters(view = it.view) } }) { Text(stringResource(R.string.reset_filters)) }
            }
        }
    }
}

private fun sortLabel(s: TaskSort) = when (s) {
    TaskSort.DUE -> R.string.sort_due
    TaskSort.PRIORITY -> R.string.sort_priority
    TaskSort.CREATED -> R.string.sort_created
    TaskSort.TITLE -> R.string.sort_title
}

private fun viewLabel(v: TaskView) = when (v) {
    TaskView.LIST -> R.string.view_list
    TaskView.TODAY -> R.string.view_today
    TaskView.WEEK -> R.string.view_week
    TaskView.MONTH -> R.string.view_month
    TaskView.CATEGORY -> R.string.view_category
    TaskView.PRIORITY -> R.string.view_priority
}
