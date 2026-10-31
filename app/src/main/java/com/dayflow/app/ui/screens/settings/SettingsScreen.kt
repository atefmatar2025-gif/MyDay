package com.dayflow.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.BuildConfig
import com.dayflow.app.R
import com.dayflow.app.data.DayMask
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.db.RestPeriodEntity
import com.dayflow.app.data.db.UserSettingsEntity
import com.dayflow.app.data.minuteToTime
import com.dayflow.app.domain.DataManager
import com.dayflow.app.notification.DailySummaryWorker
import com.dayflow.app.notification.ReminderScheduler
import com.dayflow.app.ui.components.ConfirmDialog
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.ListRow
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.SoundPickerButtons
import com.dayflow.app.ui.components.TimePickerDialogDf
import com.dayflow.app.ui.components.soundName
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.backup.BackupFormatException
import com.dayflow.core.backup.WrongPasswordException
import com.dayflow.core.text.Ar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class SettingsUi(val s: UserSettingsEntity? = null, val rest: List<RestPeriodEntity> = emptyList(), val lastBackup: Long = 0)

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val data: DataManager,
    private val appContext: android.content.Context,
    lastBackup: kotlinx.coroutines.flow.Flow<Long>,
) : ViewModel() {
    val state: StateFlow<SettingsUi> = combine(repo.settings, repo.restPeriods, lastBackup) { s, r, b -> SettingsUi(s, r, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    /** Settings that change planning or alarms re-run the schedulers. */
    fun update(reschedule: Boolean = false, transform: (UserSettingsEntity) -> UserSettingsEntity) = viewModelScope.launch {
        repo.update(transform)
        val s = repo.current()
        DailySummaryWorker.schedule(appContext, s)
        if (reschedule) scheduler.rescheduleAll()
    }

    fun addRest(label: String, start: Int, end: Int) = viewModelScope.launch { repo.addRestPeriod(label, start, end) }
    fun deleteRest(id: Long) = viewModelScope.launch { repo.deleteRestPeriod(id) }

    suspend fun backup(uri: Uri, pw: CharArray?) = data.writeBackup(uri, pw)
    suspend fun export(uri: Uri) = data.writeExport(uri)
    suspend fun read(uri: Uri) = data.read(uri)
    fun encrypted(bytes: ByteArray) = data.isEncrypted(bytes)
    suspend fun restore(bytes: ByteArray, pw: CharArray?) = data.restore(bytes, pw)
    fun deleteAll() = viewModelScope.launch { data.deleteEverything() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val vm = dfViewModel {
        SettingsViewModel(it.settingsRepository, it.reminderScheduler, it.dataManager, it.appContext, it.appState.lastBackupAt)
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    val s = ui.s ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var timeEdit by remember { mutableStateOf<String?>(null) }
    var addRest by remember { mutableStateOf(false) }
    var restStart by remember { mutableStateOf<Int?>(null) }
    var backupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestore by remember { mutableStateOf<ByteArray?>(null) }
    var askRestorePassword by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val msgDone = stringResource(R.string.done)
    val msgWrongPw = stringResource(R.string.wrong_password)
    val msgFailed = stringResource(R.string.operation_failed)

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.backup(uri, backupPassword) }.onSuccess { snackbar.showSnackbar(msgDone) }.onFailure { snackbar.showSnackbar(msgFailed) }
            backupPassword = null
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.export(uri) }.onSuccess { snackbar.showSnackbar(msgDone) }.onFailure { snackbar.showSnackbar(msgFailed) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.read(uri) }.onSuccess { bytes ->
                pendingRestore = bytes
                askRestorePassword = runCatching { vm.encrypted(bytes) }.getOrDefault(false)
            }.onFailure { snackbar.showSnackbar(msgFailed) }
        }
    }

    fun doRestore(pw: CharArray?) {
        val bytes = pendingRestore ?: return
        pendingRestore = null
        scope.launch {
            try {
                vm.restore(bytes, pw)
                snackbar.showSnackbar(msgDone)
            } catch (e: WrongPasswordException) {
                snackbar.showSnackbar(msgWrongPw)
            } catch (e: BackupFormatException) {
                snackbar.showSnackbar(e.message ?: msgFailed)
            } catch (e: Exception) {
                snackbar.showSnackbar(msgFailed)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.settings), onBack = { nav.popBackStack() })
        SnackbarHost(snackbar)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---------------- Profile & appearance ----------------
            SectionHeader(stringResource(R.string.appearance))
            DfCard {
                OutlinedTextField(
                    s.displayName, { v -> vm.update { it.copy(displayName = v.take(30)) } },
                    label = { Text(stringResource(R.string.your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("SYSTEM" to R.string.theme_system, "LIGHT" to R.string.theme_light, "DARK" to R.string.theme_dark).forEach { (k, l) ->
                        FilterChip(selected = s.themeMode == k, onClick = { vm.update { it.copy(themeMode = k) } }, label = { Text(stringResource(l)) })
                    }
                }
                Text(stringResource(R.string.font_size_x, (s.fontScale * 100).toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                Slider(value = s.fontScale, onValueChange = { v -> vm.update { it.copy(fontScale = (v * 20).toInt() / 20f) } }, valueRange = 0.85f..1.5f, steps = 12)
                ListRow(stringResource(R.string.language), stringResource(R.string.language_value))
            }

            // ---------------- My day ----------------
            SectionHeader(stringResource(R.string.my_day))
            DfCard {
                TimeRow(stringResource(R.string.day_start), s.dayStartMinute) { timeEdit = "dayStart" }
                TimeRow(stringResource(R.string.sleep_time), s.sleepMinute) { timeEdit = "sleep" }
                Text(stringResource(R.string.work_days), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                val work = DayMask.fromMask(s.workDaysMask)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekOrder.forEach { d ->
                        FilterChip(
                            selected = d in work,
                            onClick = { vm.update { it.copy(workDaysMask = DayMask.toMask(if (d in work) work - d else work + d)) } },
                            label = { Text(Ar.shortDayName(d)) },
                        )
                    }
                }
                Text(stringResource(R.string.week_starts), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY).forEach { d ->
                        FilterChip(selected = s.weekStartDay == d.value, onClick = { vm.update { it.copy(weekStartDay = d.value) } }, label = { Text(Ar.dayName(d)) })
                    }
                }
                StepRow(stringResource(R.string.buffer_minutes), s.bufferMinutes, 0, 30, 5) { v -> vm.update { it.copy(bufferMinutes = v) } }
                StepRow(stringResource(R.string.max_chunk), s.maxChunkMinutes, 30, 180, 15) { v -> vm.update { it.copy(maxChunkMinutes = v) } }
            }
            SectionHeader(stringResource(R.string.rest_settings), action = stringResource(R.string.add)) { addRest = true }
            DfCard {
                if (ui.rest.isEmpty()) Text(stringResource(R.string.no_rest_periods), style = MaterialTheme.typography.bodySmall)
                ui.rest.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.label}: ${Ar.time(minuteToTime(r.startMinute))} – ${Ar.time(minuteToTime(r.endMinute))}", Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteRest(r.id) }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }

            // ---------------- Alarms ----------------
            SectionHeader(stringResource(R.string.alarm_settings))
            DfCard {
                ListRow(stringResource(R.string.alarm_permissions), stringResource(R.string.alarm_permissions_sub), Icons.Filled.NotificationsActive, { nav.navigate(Routes.PERMISSIONS) })
                ListRow(stringResource(R.string.alarm_log), null, Icons.Filled.History, { nav.navigate(Routes.ALARM_LOG) })
                HorizontalDivider()
                StepRow(stringResource(R.string.default_offset), s.defaultReminderOffset, 0, 120, 5) { v -> vm.update { it.copy(defaultReminderOffset = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = s.defaultReminderMode == "ALARM", onClick = { vm.update { it.copy(defaultReminderMode = "ALARM") } }, label = { Text(stringResource(R.string.mode_alarm)) })
                    FilterChip(selected = s.defaultReminderMode == "NOTIFICATION", onClick = { vm.update { it.copy(defaultReminderMode = "NOTIFICATION") } }, label = { Text(stringResource(R.string.mode_notification)) })
                }
                Text(stringResource(R.string.sound_x, soundName(context, s.defaultSoundUri)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                SoundPickerButtons(s.defaultSoundUri) { u -> vm.update { it.copy(defaultSoundUri = u) } }
                Text(stringResource(R.string.alarm_volume_x, s.alarmVolumePercent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                Slider(value = s.alarmVolumePercent.toFloat(), onValueChange = { v -> vm.update { it.copy(alarmVolumePercent = v.toInt()) } }, valueRange = 10f..100f, steps = 8)
                Text(stringResource(R.string.volume_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.vibration_pattern), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("NONE" to R.string.vib_none, "SHORT" to R.string.vib_short, "LONG" to R.string.vib_long, "HEARTBEAT" to R.string.vib_heartbeat).forEach { (k, l) ->
                        FilterChip(selected = s.vibrationPattern == k, onClick = { vm.update { it.copy(vibrationPattern = k) } }, label = { Text(stringResource(l)) })
                    }
                }
                StepRow(stringResource(R.string.ring_duration_sec), s.ringSeconds, 15, 600, 15) { v -> vm.update { it.copy(ringSeconds = v) } }
                StepRow(stringResource(R.string.repeat_unanswered), s.repeatCount, 0, 5, 1) { v -> vm.update { it.copy(repeatCount = v) } }
                StepRow(stringResource(R.string.repeat_every), s.repeatIntervalMinutes, 1, 30, 1) { v -> vm.update { it.copy(repeatIntervalMinutes = v) } }
                Text(stringResource(R.string.snooze_default), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterChip(selected = s.defaultSnoozeMinutes == m, onClick = { vm.update { it.copy(defaultSnoozeMinutes = m) } }, label = { Text(stringResource(R.string.minutes_short, m)) })
                    }
                }
                Text(stringResource(R.string.reminder_defaults_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---------------- Notifications ----------------
            SectionHeader(stringResource(R.string.notification_settings))
            DfCard {
                SwitchRow(stringResource(R.string.daily_summary), s.dailySummaryEnabled) { v -> vm.update { it.copy(dailySummaryEnabled = v) } }
                if (s.dailySummaryEnabled) TimeRow(stringResource(R.string.daily_summary_time), s.dailySummaryMinute) { timeEdit = "summary" }
                SwitchRow(stringResource(R.string.habit_notifications), s.habitRemindersEnabled) { v -> vm.update(reschedule = true) { it.copy(habitRemindersEnabled = v) } }
                SwitchRow(stringResource(R.string.completion_notifications), s.completionNotifications) { v -> vm.update { it.copy(completionNotifications = v) } }
            }

            // ---------------- Calendar & focus ----------------
            SectionHeader(stringResource(R.string.calendar_settings))
            DfCard {
                SwitchRow(stringResource(R.string.show_hijri), s.showHijri) { v -> vm.update { it.copy(showHijri = v) } }
                if (s.showHijri) {
                    StepRow(stringResource(R.string.hijri_adjust), s.hijriAdjustment, -2, 2, 1) { v -> vm.update { it.copy(hijriAdjustment = v) } }
                    Text(stringResource(R.string.hijri_note, com.dayflow.core.time.HijriCalendar.format(LocalDate.now(), s.hijriAdjustment) ?: "—"), style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionHeader(stringResource(R.string.focus_mode))
            DfCard {
                StepRow(stringResource(R.string.focus_length), s.focusMinutes, 5, 120, 5) { v -> vm.update { it.copy(focusMinutes = v) } }
                StepRow(stringResource(R.string.short_break), s.shortBreakMinutes, 1, 30, 1) { v -> vm.update { it.copy(shortBreakMinutes = v) } }
                StepRow(stringResource(R.string.long_break), s.longBreakMinutes, 5, 60, 5) { v -> vm.update { it.copy(longBreakMinutes = v) } }
                StepRow(stringResource(R.string.cycles_before_long), s.cyclesBeforeLongBreak, 2, 8, 1) { v -> vm.update { it.copy(cyclesBeforeLongBreak = v) } }
            }

            // ---------------- Data & privacy ----------------
            SectionHeader(stringResource(R.string.data_privacy))
            DfCard(contentPadding = PaddingValues(vertical = Spacing.sm)) {
                ListRow(
                    stringResource(R.string.backup_now),
                    if (ui.lastBackup > 0) stringResource(R.string.last_backup_x, Ar.dateTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ui.lastBackup), java.time.ZoneId.systemDefault()), LocalDate.now())) else stringResource(R.string.never_backed_up),
                    Icons.Filled.Backup, { backupDialog = true },
                )
                ListRow(stringResource(R.string.restore_backup), stringResource(R.string.restore_sub), Icons.Filled.Restore, { runCatching { restoreLauncher.launch(arrayOf("*/*")) } })
                ListRow(stringResource(R.string.export_json), stringResource(R.string.export_sub), Icons.Filled.FileDownload, {
                    runCatching { exportLauncher.launch("dayflow-export-${LocalDate.now()}.json") }
                })
                ListRow(stringResource(R.string.import_json), stringResource(R.string.import_sub), Icons.Filled.FileUpload, { runCatching { restoreLauncher.launch(arrayOf("application/json", "*/*")) } })
                ListRow(stringResource(R.string.delete_all), stringResource(R.string.delete_all_sub), Icons.Filled.DeleteForever, { confirmDeleteAll = true })
            }
            DfCard {
                Row(verticalAlignment = Alignment.Top) {
                    androidx.compose.material3.Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------------- About ----------------
            SectionHeader(stringResource(R.string.about))
            DfCard {
                Row(verticalAlignment = Alignment.Top) {
                    androidx.compose.material3.Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(4.dp))
                    Column {
                        Text("DAYFLOW ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    // ---------------- dialogs ----------------
    timeEdit?.let { which ->
        val current = when (which) { "dayStart" -> s.dayStartMinute; "sleep" -> s.sleepMinute; else -> s.dailySummaryMinute }
        TimePickerDialogDf(minuteToTime(current), { timeEdit = null }) { t ->
            val m = t.hour * 60 + t.minute
            when (which) {
                "dayStart" -> vm.update(reschedule = true) { it.copy(dayStartMinute = m) }
                "sleep" -> vm.update(reschedule = true) { it.copy(sleepMinute = if (m == 0) 24 * 60 - 1 else m) }
                else -> vm.update { it.copy(dailySummaryMinute = m) }
            }
        }
    }
    if (addRest) {
        TimePickerDialogDf(LocalTime.of(13, 0), { addRest = false }) { t -> restStart = t.hour * 60 + t.minute }
    }
    restStart?.let { start ->
        val label = stringResource(R.string.rest_period)
        TimePickerDialogDf(minuteToTime((start + 60).coerceAtMost(24 * 60 - 1)), { restStart = null }) { t ->
            val end = t.hour * 60 + t.minute
            if (end > start) vm.addRest(label, start, end)
        }
    }
    if (backupDialog) {
        var encrypt by remember { mutableStateOf(true) }
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { backupDialog = false },
            title = { Text(stringResource(R.string.backup_now)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SwitchRow(stringResource(R.string.encrypt_backup), encrypt) { encrypt = it }
                    if (encrypt) {
                        OutlinedTextField(pw, { pw = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        OutlinedTextField(pw2, { pw2 = it }, label = { Text(stringResource(R.string.password_confirm)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text(stringResource(R.string.password_warning), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !encrypt || (pw.length >= 6 && pw == pw2),
                    onClick = {
                        backupPassword = if (encrypt) pw.toCharArray() else null
                        backupDialog = false
                        runCatching { backupLauncher.launch("dayflow-backup-${LocalDate.now()}.dflw") }
                    },
                ) { Text(stringResource(R.string.choose_location)) }
            },
            dismissButton = { TextButton(onClick = { backupDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pendingRestore != null) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_q)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.restore_warning))
                    if (askRestorePassword) {
                        OutlinedTextField(pw, { pw = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { doRestore(if (askRestorePassword) pw.toCharArray() else null) }, enabled = !askRestorePassword || pw.isNotEmpty()) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            stringResource(R.string.delete_all_q), stringResource(R.string.delete_all_msg), stringResource(R.string.delete_all),
            onConfirm = { vm.deleteAll() }, onDismiss = { confirmDeleteAll = false }, destructive = true,
        )
    }
}

private val weekOrder = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

@Composable
private fun TimeRow(label: String, minute: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(Ar.time(minuteToTime(minute))) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepRow(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text(value.toString(), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}
