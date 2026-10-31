package com.dayflow.app.ui.screens.tasks

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.TaskCategoryEntity
import com.dayflow.app.domain.AttachmentDraft
import com.dayflow.app.domain.ReminderDraft
import com.dayflow.app.domain.TaskDraft
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.domain.ValidationException
import com.dayflow.app.notification.ReminderScheduler
import com.dayflow.app.ui.components.DatePickerDialogDf
import com.dayflow.app.ui.components.DateTimeField
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.SoundPickerButtons
import com.dayflow.app.ui.components.fileName
import com.dayflow.app.ui.components.rememberSpeechInput
import com.dayflow.app.ui.components.soundName
import com.dayflow.app.ui.components.speechSupport
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.Frequency
import com.dayflow.core.model.Priority
import com.dayflow.core.model.RecurrenceRule
import com.dayflow.core.model.Task
import com.dayflow.core.text.Ar
import com.dayflow.core.text.VoiceTaskParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class EditorState(
    val draft: TaskDraft = TaskDraft(),
    val loading: Boolean = true,
    val categories: List<TaskCategoryEntity> = emptyList(),
    val otherTasks: List<Task> = emptyList(),
    val voiceNotes: List<String> = emptyList(),
    val error: String? = null,
    val savedId: Long? = null,
)

class TaskEditorViewModel(
    private val taskId: Long,
    epochDay: Long?,
    parentId: Long?,
    private val repo: TaskRepository,
    private val interactor: TaskInteractor,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.categories.collect { cats -> _state.update { it.copy(categories = cats) } }
        }
        viewModelScope.launch {
            repo.tasks.collect { all -> _state.update { it.copy(otherTasks = all.filter { t -> t.id != taskId && !t.archived && t.status.isOpen }) } }
        }
        viewModelScope.launch {
            val draft = if (taskId > 0) {
                interactor.loadDraft(taskId) ?: TaskDraft()
            } else {
                TaskDraft(
                    startAt = epochDay?.let { LocalDate.ofEpochDay(it).atTime(nextHour()) },
                    parentId = parentId,
                    categoryId = parentId?.let { repo.entity(it)?.categoryId },
                )
            }
            _state.update { it.copy(draft = draft, loading = false) }
        }
    }

    private fun nextHour(): LocalTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)

    fun edit(transform: (TaskDraft) -> TaskDraft) = _state.update { it.copy(draft = transform(it.draft), error = null) }

    /** Fills the form from an Arabic sentence. Nothing is saved until the user presses Save. */
    fun applySentence(text: String) {
        if (text.isBlank()) return
        val parsed = VoiceTaskParser.parse(text, LocalDateTime.now())
        viewModelScope.launch {
            val defaults = interactor.newDraftDefaults()
            _state.update { s ->
                val d = s.draft
                s.copy(
                    draft = d.copy(
                        title = parsed.title,
                        startAt = parsed.dateTime ?: parsed.date?.atTime(nextHour()) ?: d.startAt,
                        estimatedMinutes = parsed.durationMinutes ?: d.estimatedMinutes,
                        priority = parsed.priority ?: d.priority,
                        recurrence = parsed.recurrence ?: d.recurrence,
                        reminders = if (parsed.reminder && d.reminders.isEmpty()) listOf(defaults.copy(offsetMinutes = 0)) else d.reminders,
                    ),
                    voiceNotes = parsed.ambiguities,
                    error = null,
                )
            }
        }
    }

    fun addReminder() = viewModelScope.launch {
        val defaults = interactor.newDraftDefaults()
        edit { it.copy(reminders = it.reminders + defaults) }
    }

    fun save() = viewModelScope.launch {
        try {
            val id = interactor.save(_state.value.draft)
            _state.update { it.copy(savedId = id) }
        } catch (e: ValidationException) {
            _state.update { it.copy(error = e.message) }
        }
    }
}

private val durationPresets = listOf(15, 25, 30, 45, 60, 90, 120)
private val offsetPresets = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(nav: NavController, taskId: Long, epochDay: Long?, startWithVoice: Boolean, parentId: Long?) {
    val vm = dfViewModel(key = "editor_$taskId") { TaskEditorViewModel(taskId, epochDay, parentId, it.taskRepository, it.taskInteractor) }
    val s by vm.state.collectAsStateWithLifecycle()
    val d = s.draft
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sentence by remember { mutableStateOf("") }
    val support = remember { speechSupport(context) }
    val speechUnavailable = stringResource(R.string.voice_unavailable)
    val listen = rememberSpeechInput(
        onText = { sentence = it; vm.applySentence(it) },
        onUnavailable = { scope.launch { snackbar.showSnackbar(speechUnavailable) } },
    )
    var voiceStarted by remember { mutableStateOf(false) }
    LaunchedEffect(startWithVoice, s.loading) {
        if (startWithVoice && !s.loading && !voiceStarted) { voiceStarted = true; if (support.available) listen() }
    }
    LaunchedEffect(s.savedId) {
        val id = s.savedId ?: return@LaunchedEffect
        nav.popBackStack()
        if (taskId == 0L) nav.navigate(Routes.task(id))
    }
    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it) } }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.edit {
                it.copy(attachments = it.attachments + AttachmentDraft(uri = uri.toString(), displayName = fileName(context, uri) ?: uri.lastPathSegment ?: "file", mimeType = context.contentResolver.getType(uri)))
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        DfTopBar(stringResource(if (taskId > 0) R.string.edit_task else R.string.new_task), onBack = { nav.popBackStack() }) {
            TextButton(onClick = { vm.save() }, enabled = !s.loading) { Text(stringResource(R.string.save)) }
        }
        SnackbarHost(snackbar)
        if (s.loading) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---------------- Voice / sentence ----------------
            DfCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.voice_to_task_title), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = sentence, onValueChange = { sentence = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.voice_example)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.applySentence(sentence) }),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    FilledTonalIconButton(onClick = { if (support.available) listen() else scope.launch { snackbar.showSnackbar(speechUnavailable) } }) {
                        Icon(Icons.Filled.Mic, stringResource(R.string.voice_to_task))
                    }
                    FilledTonalIconButton(onClick = { vm.applySentence(sentence) }) {
                        Icon(Icons.Filled.AutoAwesome, stringResource(R.string.analyze))
                    }
                }
                Text(
                    stringResource(
                        when {
                            !support.available -> R.string.voice_note_unavailable
                            support.onDevice == true -> R.string.voice_note_offline
                            else -> R.string.voice_note_maybe_online
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (s.voiceNotes.isNotEmpty()) {
                    Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = LocalExtraColors.current.warning)
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(stringResource(R.string.voice_check), style = MaterialTheme.typography.labelLarge)
                            s.voiceNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            // ---------------- Basics ----------------
            OutlinedTextField(
                value = d.title, onValueChange = { v -> vm.edit { it.copy(title = v) } },
                label = { Text(stringResource(R.string.task_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = d.description, onValueChange = { v -> vm.edit { it.copy(description = v) } },
                label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            SectionHeader(stringResource(R.string.priority))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val order = Priority.entries.sortedByDescending { it.weight }
                order.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = d.priority == p, onClick = { vm.edit { it.copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, order.size),
                    ) { Text(Ar.priority(p), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }

            SectionHeader(stringResource(R.string.category), action = stringResource(R.string.manage)) { nav.navigate(Routes.CATEGORIES) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(s.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = d.categoryId == c.id,
                        onClick = { vm.edit { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) } },
                        label = { Text(c.name) },
                        leadingIcon = { com.dayflow.app.ui.components.ColorDot(AppColors.parse(c.colorHex)) },
                    )
                }
            }

            // ---------------- Time ----------------
            SectionHeader(stringResource(R.string.time_section))
            DateTimeField(stringResource(R.string.start_at), d.startAt, { v -> vm.edit { it.copy(startAt = v) } })
            DateTimeField(stringResource(R.string.due_at), d.dueAt, { v -> vm.edit { it.copy(dueAt = v) } })
            Text(stringResource(R.string.estimated_duration), style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(durationPresets) { m ->
                    FilterChip(
                        selected = d.estimatedMinutes == m,
                        onClick = { vm.edit { it.copy(estimatedMinutes = if (it.estimatedMinutes == m) null else m) } },
                        label = { Text(Ar.duration(m)) },
                    )
                }
            }
            OutlinedTextField(
                value = d.estimatedMinutes?.toString() ?: "",
                onValueChange = { v -> vm.edit { it.copy(estimatedMinutes = v.filter(Char::isDigit).take(4).toIntOrNull()) } },
                label = { Text(stringResource(R.string.duration_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fixed_time))
                    Text(stringResource(R.string.fixed_time_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = d.fixedTime, onCheckedChange = { v -> vm.edit { it.copy(fixedTime = v) } })
            }

            // ---------------- Recurrence ----------------
            RecurrenceEditor(d.recurrence, d.startAt?.toLocalDate() ?: d.dueAt?.toLocalDate()) { r -> vm.edit { it.copy(recurrence = r) } }

            // ---------------- Reminders ----------------
            SectionHeader(stringResource(R.string.reminders), action = stringResource(R.string.add)) { vm.addReminder() }
            if (d.reminders.isEmpty()) {
                Text(stringResource(R.string.no_reminders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.reminders.forEachIndexed { index, r ->
                ReminderEditor(
                    r,
                    onChange = { nr -> vm.edit { it.copy(reminders = it.reminders.toMutableList().also { l -> l[index] = nr }) } },
                    onRemove = { vm.edit { it.copy(reminders = it.reminders.toMutableList().also { l -> l.removeAt(index) }) } },
                )
            }

            // ---------------- Tags ----------------
            SectionHeader(stringResource(R.string.tags))
            var tagText by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tagText, onValueChange = { tagText = it.replace("#", "") }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(stringResource(R.string.add_tag)) },
                    keyboardActions = KeyboardActions(onDone = {
                        if (tagText.isNotBlank()) { vm.edit { it.copy(tags = (it.tags + tagText.trim()).distinct()) }; tagText = "" }
                    }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                IconButton(onClick = {
                    if (tagText.isNotBlank()) { vm.edit { it.copy(tags = (it.tags + tagText.trim()).distinct()) }; tagText = "" }
                }) { Icon(Icons.Filled.Add, stringResource(R.string.add_tag)) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(d.tags) { tag ->
                    InputChip(
                        selected = false, onClick = { vm.edit { it.copy(tags = it.tags - tag) } },
                        label = { Text("#$tag") }, trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear)) },
                    )
                }
            }

            // ---------------- Relations ----------------
            SectionHeader(stringResource(R.string.relations))
            RelationPicker(
                label = stringResource(R.string.parent_task),
                options = s.otherTasks.filter { it.parentId != d.id || d.id == 0L },
                selected = listOfNotNull(d.parentId),
                multi = false,
            ) { ids -> vm.edit { it.copy(parentId = ids.firstOrNull()) } }
            RelationPicker(
                label = stringResource(R.string.depends_on),
                options = s.otherTasks,
                selected = d.dependsOn,
                multi = true,
            ) { ids -> vm.edit { it.copy(dependsOn = ids) } }

            // ---------------- Notes & attachments ----------------
            OutlinedTextField(
                value = d.notes, onValueChange = { v -> vm.edit { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            SectionHeader(stringResource(R.string.attachments), action = stringResource(R.string.add)) { runCatching { attachLauncher.launch(arrayOf("*/*")) } }
            d.attachments.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AttachFile, null)
                    Text(a.displayName, modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm), maxLines = 1)
                    IconButton(onClick = { vm.edit { it.copy(attachments = it.attachments - a) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
            Text(stringResource(R.string.attachments_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) {
                Text(stringResource(R.string.save_task))
            }
        }
    }
}

@Composable
private fun RecurrenceEditor(rule: RecurrenceRule?, anchor: LocalDate?, onChange: (RecurrenceRule?) -> Unit) {
    var pickUntil by remember { mutableStateOf(false) }
    var pickException by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.recurrence))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item { FilterChip(selected = rule == null, onClick = { onChange(null) }, label = { Text(stringResource(R.string.none)) }) }
        items(Frequency.entries) { f ->
            FilterChip(
                selected = rule?.frequency == f,
                onClick = { onChange((rule ?: RecurrenceRule(f)).copy(frequency = f, daysOfWeek = if (f == Frequency.WEEKLY) rule?.daysOfWeek ?: emptySet() else emptySet())) },
                label = { Text(stringResource(freqLabel(f))) },
            )
        }
    }
    if (rule == null) return
    if (rule.frequency == Frequency.WEEKLY) {
        val order = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(order) { day ->
                val selected = day in rule.daysOfWeek || (rule.daysOfWeek.isEmpty() && anchor?.dayOfWeek == day)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val base = rule.daysOfWeek.ifEmpty { setOfNotNull(anchor?.dayOfWeek) }
                        onChange(rule.copy(daysOfWeek = if (day in base) base - day else base + day))
                    },
                    label = { Text(Ar.dayName(day)) },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.every_n, rule.interval), modifier = Modifier.weight(1f))
        IconButton(onClick = { if (rule.interval > 1) onChange(rule.copy(interval = rule.interval - 1)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        IconButton(onClick = { if (rule.interval < 30) onChange(rule.copy(interval = rule.interval + 1)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            rule.until?.let { stringResource(R.string.until_date, Ar.shortDate(it)) } ?: stringResource(R.string.no_end_date),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { pickUntil = true }) { Text(stringResource(R.string.set_end)) }
        if (rule.until != null) IconButton(onClick = { onChange(rule.copy(until = null)) }) { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.exceptions), modifier = Modifier.weight(1f))
        TextButton(onClick = { pickException = true }) { Text(stringResource(R.string.add_exception)) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(rule.exceptions.sorted()) { ex ->
            InputChip(
                selected = false, onClick = { onChange(rule.copy(exceptions = rule.exceptions - ex)) },
                label = { Text(Ar.shortDate(ex)) }, trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear)) },
            )
        }
    }
    if (pickUntil) DatePickerDialogDf(rule.until ?: anchor, { pickUntil = false }) { onChange(rule.copy(until = it)) }
    if (pickException) DatePickerDialogDf(anchor, { pickException = false }) { onChange(rule.copy(exceptions = rule.exceptions + it)) }
}

private fun freqLabel(f: Frequency) = when (f) {
    Frequency.DAILY -> R.string.freq_daily
    Frequency.WEEKLY -> R.string.freq_weekly
    Frequency.MONTHLY -> R.string.freq_monthly
    Frequency.YEARLY -> R.string.freq_yearly
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditor(r: ReminderDraft, onChange: (ReminderDraft) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var offsetMenu by remember { mutableStateOf(false) }
    DfCard(container = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                TextButton(onClick = { offsetMenu = true }) {
                    Text(if (r.offsetMinutes == 0) stringResource(R.string.at_time) else stringResource(R.string.before_x, Ar.duration(r.offsetMinutes)))
                }
                DropdownMenu(expanded = offsetMenu, onDismissRequest = { offsetMenu = false }) {
                    offsetPresets.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == 0) stringResource(R.string.at_time) else stringResource(R.string.before_x, Ar.duration(m))) },
                            onClick = { onChange(r.copy(offsetMinutes = m)); offsetMenu = false },
                        )
                    }
                }
            }
            Switch(checked = r.enabled, onCheckedChange = { onChange(r.copy(enabled = it)) })
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ReminderScheduler.Mode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = r.mode == mode, onClick = { onChange(r.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(i, ReminderScheduler.Mode.entries.size),
                ) { Text(stringResource(if (mode == ReminderScheduler.Mode.ALARM) R.string.mode_alarm else R.string.mode_notification)) }
            }
        }
        if (r.mode == ReminderScheduler.Mode.ALARM) {
            Text(stringResource(R.string.sound_x, soundName(context, r.soundUri)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            SoundPickerButtons(r.soundUri) { onChange(r.copy(soundUri = it)) }
            Text(stringResource(R.string.volume_x, r.volumePercent), style = MaterialTheme.typography.bodySmall)
            Slider(value = r.volumePercent.toFloat(), onValueChange = { onChange(r.copy(volumePercent = it.toInt())) }, valueRange = 10f..100f, steps = 8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vibrate), modifier = Modifier.weight(1f))
                Switch(checked = r.vibrate, onCheckedChange = { onChange(r.copy(vibrate = it)) })
            }
            ChoiceRow(stringResource(R.string.ring_duration), listOf(30, 60, 120, 300), r.ringSeconds, { "${it / 60.0}".removeSuffix(".0") + stringResource(R.string.minute_abbr) }) {
                onChange(r.copy(ringSeconds = it))
            }
            ChoiceRow(stringResource(R.string.repeat_unanswered), listOf(0, 1, 2, 3, 5), r.repeatCount, { if (it == 0) stringResource(R.string.none) else "×$it" }) {
                onChange(r.copy(repeatCount = it))
            }
            if (r.repeatCount > 0) {
                ChoiceRow(stringResource(R.string.repeat_every), listOf(5, 10, 15), r.repeatIntervalMinutes, { stringResource(R.string.minutes_short, it) }) {
                    onChange(r.copy(repeatIntervalMinutes = it))
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<Int>, selected: Int, text: @Composable (Int) -> String, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) }) }
    }
}

/** Chooses one (parent) or several (dependencies) other tasks. */
@Composable
private fun RelationPicker(label: String, options: List<Task>, selected: List<Long>, multi: Boolean, onChange: (List<Long>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = options.filter { it.id in selected }.joinToString("، ") { it.title }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(names.ifEmpty { stringResource(R.string.none) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty()) { Text(stringResource(R.string.choose)) }
    }
    if (open) {
        var current by remember { mutableStateOf(selected.toSet()) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn {
                    if (!multi) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = current.isEmpty(), onCheckedChange = { current = emptySet() })
                                Text(stringResource(R.string.none))
                            }
                        }
                    }
                    items(options, key = { it.id }) { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.id in current,
                                onCheckedChange = { c -> current = if (multi) { if (c) current + t.id else current - t.id } else if (c) setOf(t.id) else emptySet() },
                            )
                            Text(t.title, maxLines = 2)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onChange(current.toList()); open = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
