package com.dayflow.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.data.minuteToTime
import com.dayflow.app.notification.PermissionStatus
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.TimePickerDialogDf
import com.dayflow.app.ui.theme.Brand
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.text.Ar
import kotlinx.coroutines.launch

/** First run: a few preferences and a clear explanation of the permissions alarms need. Nothing is mandatory. */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val container = context.appContainer()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var dayStart by remember { mutableIntStateOf(7 * 60) }
    var sleep by remember { mutableIntStateOf(23 * 60) }
    var lunch by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(PermissionStatus.read(context)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = PermissionStatus.read(context) }
    val lunchLabel = stringResource(R.string.lunch_break)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brand.DeepNavy), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp))
        }
        Text("DAYFLOW", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

        DfCard {
            OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.your_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.day_start), Modifier.weight(1f))
                TextButton(onClick = { editing = "start" }) { Text(Ar.time(minuteToTime(dayStart))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sleep_time), Modifier.weight(1f))
                TextButton(onClick = { editing = "sleep" }) { Text(Ar.time(minuteToTime(sleep))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = lunch, onCheckedChange = { lunch = it })
                Text(stringResource(R.string.add_lunch_break))
            }
        }

        DfCard {
            Text(stringResource(R.string.onboarding_permissions), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.onboarding_permissions_desc), style = MaterialTheme.typography.bodySmall)
            if (!status.notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.allow_notifications))
                }
            }
            if (!status.exactAlarmsAllowed) {
                OutlinedButton(onClick = { runCatching { context.startActivity(PermissionStatus.exactAlarmSettings(context)) } }) {
                    Text(stringResource(R.string.allow_exact_alarms))
                }
            }
            if (status.notificationsAllowed && status.exactAlarmsAllowed) {
                Text(stringResource(R.string.perm_all_good), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Button(
            onClick = {
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(displayName = name.trim(), dayStartMinute = dayStart, sleepMinute = sleep, onboardingDone = true)
                    }
                    if (lunch) container.settingsRepository.addRestPeriod(lunchLabel, 13 * 60, 14 * 60)
                    container.reminderScheduler.rescheduleAll()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.get_started)) }
        Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    editing?.let { which ->
        TimePickerDialogDf(minuteToTime(if (which == "start") dayStart else sleep), { editing = null }) { t ->
            val m = t.hour * 60 + t.minute
            if (which == "start") dayStart = m else sleep = if (m == 0) 24 * 60 - 1 else m
        }
    }
}
