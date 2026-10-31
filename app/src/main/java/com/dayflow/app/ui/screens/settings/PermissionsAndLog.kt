package com.dayflow.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.AlarmLogEntity
import com.dayflow.app.data.db.TaskCategoryEntity
import com.dayflow.app.notification.PermissionStatus
import com.dayflow.app.ui.components.ColorDot
import com.dayflow.app.ui.components.ConfirmDialog
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.AppColors
import com.dayflow.app.ui.theme.LocalExtraColors
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.LifeArea
import com.dayflow.core.text.Ar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Clear explanation of every permission that affects alarms, with a direct fix button for each. */
@Composable
fun PermissionsScreen(nav: NavController) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.read(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                status = PermissionStatus.read(context)
                context.appContainer().appScope.launch { context.appContainer().reminderScheduler.rescheduleAll() }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = PermissionStatus.read(context) }

    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.alarm_permissions), onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DfCard(container = if (status.allGood) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(if (status.allGood) R.string.perm_all_good else R.string.perm_needs_attention), style = MaterialTheme.typography.titleMedium)
            }
            PermissionCard(
                stringResource(R.string.perm_notifications), stringResource(R.string.perm_notifications_desc), status.notificationsAllowed,
            ) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { context.startActivity(PermissionStatus.appNotificationSettings(context)) }
            }
            PermissionCard(
                stringResource(R.string.perm_exact), stringResource(R.string.perm_exact_desc), status.exactAlarmsAllowed,
            ) { runCatching { context.startActivity(PermissionStatus.exactAlarmSettings(context)) } }
            PermissionCard(
                stringResource(R.string.perm_fullscreen), stringResource(R.string.perm_fullscreen_desc), status.fullScreenAllowed,
            ) { runCatching { context.startActivity(PermissionStatus.fullScreenSettings(context)) } }
            PermissionCard(
                stringResource(R.string.perm_channel), stringResource(R.string.perm_channel_desc), status.alarmChannelEnabled,
            ) { runCatching { context.startActivity(PermissionStatus.appNotificationSettings(context)) } }
            PermissionCard(
                stringResource(R.string.perm_battery), stringResource(R.string.perm_battery_desc), status.ignoringBatteryOptimizations, optional = true,
            ) { runCatching { context.startActivity(PermissionStatus.batterySettings()) } }
            DfCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.perm_how_alarms_work), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.padding(Spacing.lg))
        }
    }
}

@Composable
private fun PermissionCard(title: String, desc: String, ok: Boolean, optional: Boolean = false, onFix: () -> Unit) {
    val extra = LocalExtraColors.current
    DfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else if (optional) Icons.Filled.Info else Icons.Filled.Error, null,
                tint = if (ok) extra.success else if (optional) MaterialTheme.colorScheme.outline else extra.danger,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(if (ok) R.string.perm_granted else if (optional) R.string.perm_optional else R.string.perm_denied), style = MaterialTheme.typography.labelMedium)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (!ok) FilledTonalButton(onClick = onFix, modifier = Modifier.padding(top = Spacing.sm)) { Text(stringResource(R.string.perm_fix)) }
    }
}

// ---------------------------------------------------------------- alarm log

class AlarmLogViewModel(dao: com.dayflow.app.data.db.AlarmLogDao) : ViewModel() {
    val log: StateFlow<List<AlarmLogEntity>> = dao.observeRecent(300).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AlarmLogScreen(nav: NavController) {
    val vm = dfViewModel { AlarmLogViewModel(it.alarmLogDao) }
    val log by vm.log.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.alarm_log), onBack = { nav.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (log.isEmpty()) item { Text(stringResource(R.string.alarm_log_empty), style = MaterialTheme.typography.bodyMedium) }
            items(log, key = { it.id }) { e ->
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at), ZoneId.systemDefault())
                Column(Modifier.fillMaxWidth()) {
                    Row {
                        Text(actionLabel(e.action), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(Ar.dateTime(at, LocalDate.now()), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(e.taskTitle, style = MaterialTheme.typography.bodySmall)
                    e.scheduledFor?.let {
                        Text(
                            stringResource(R.string.log_for, Ar.dateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()), LocalDate.now())) +
                                if (!e.exact) " • " + stringResource(R.string.inexact) else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabel(a: String): String = stringResource(
    when (a) {
        "SCHEDULED" -> R.string.log_scheduled
        "INEXACT_FALLBACK" -> R.string.log_inexact
        "FIRED" -> R.string.log_fired
        "DISMISSED" -> R.string.log_dismissed
        "SNOOZED" -> R.string.log_snoozed
        "MISSED" -> R.string.log_missed
        "REPEATED" -> R.string.log_repeated
        "CANCELED" -> R.string.log_canceled
        "SKIPPED_DUPLICATE" -> R.string.log_duplicate
        else -> R.string.log_other
    },
)

// ---------------------------------------------------------------- categories

class CategoriesViewModel(private val repo: TaskRepository) : ViewModel() {
    val categories: StateFlow<List<TaskCategoryEntity>> = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(c: TaskCategoryEntity) = viewModelScope.launch { repo.saveCategory(c) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteCategory(id) }
}

private val palette = listOf("#294C80", "#5B6CC9", "#2E8C83", "#D9734E", "#38A878", "#8A94A6", "#9C6ADE", "#C2589A", "#F39B42", "#E0A526", "#6B7A90", "#D64541")

@Composable
fun CategoriesScreen(nav: NavController) {
    val vm = dfViewModel { CategoriesViewModel(it.taskRepository) }
    val cats by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskCategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<TaskCategoryEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.categories), onBack = { nav.popBackStack() }) {
            IconButton(onClick = { editing = TaskCategoryEntity(name = "", area = LifeArea.PERSONAL.name, colorHex = palette[0], sortOrder = cats.size + 1) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.add))
            }
        }
        LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { Text(stringResource(R.string.categories_hint), style = MaterialTheme.typography.bodySmall) }
            items(cats, key = { it.id }) { c ->
                DfCard(onClick = { editing = c }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(AppColors.parse(c.colorHex), 14.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(Ar.area(LifeArea.fromName(c.area)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var area by remember(c) { mutableStateOf(c.area) }
        var color by remember(c) { mutableStateOf(c.colorHex) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (c.id == 0L) R.string.new_category else R.string.edit_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true, keyboardOptions = KeyboardOptions.Default)
                    Text(stringResource(R.string.life_area), style = MaterialTheme.typography.labelLarge)
                    LifeArea.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            pair.forEach { a -> FilterChip(selected = area == a.name, onClick = { area = a.name }, label = { Text(Ar.area(a)) }) }
                        }
                    }
                    Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
                    palette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { hex ->
                                IconButton(onClick = { color = hex }) {
                                    ColorDot(AppColors.parse(hex), if (color == hex) 30.dp else 20.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.save(c.copy(name = name.trim(), area = area, colorHex = color)); editing = null }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            stringResource(R.string.delete_category_q), stringResource(R.string.delete_category_msg, c.name), stringResource(R.string.delete),
            onConfirm = { vm.delete(c.id) }, onDismiss = { deleting = null }, destructive = true,
        )
    }
}
