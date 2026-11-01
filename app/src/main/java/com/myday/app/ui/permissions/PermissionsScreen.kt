@file:OptIn(ExperimentalMaterial3Api::class)

package com.myday.app.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel

/**
 * شاشة الترحيب (أول تشغيل) + إدارة الصلاحيات.
 * نطلب فقط ما يلزم فعلاً لعمل المنبه — ولا شيء غيره.
 */
@Composable
fun PermissionsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onboarding: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val status by rememberPermissionStatus { vm.rescheduleAll() }
    var name by rememberSaveable { mutableStateOf(settings.userName) }

    var askedNotif by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // إذا رُفضت سابقاً لا يعرض النظام الحوار مجدداً، فنفتح الإعدادات مباشرة
        if (!granted && askedNotif) SystemSettings.openNotificationSettings(context)
        askedNotif = true
    }

    Scaffold(
        topBar = {
            if (!onboarding) {
                TopAppBar(
                    title = { Text("الصلاحيات والمنبه") },
                    navigationIcon = {
                        IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            if (onboarding) {
                Spacer(Modifier.height(32.dp))
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Alarm, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp)) }
                Spacer(Modifier.height(16.dp))
                Text("أهلاً بك في يومي", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "نظّم مهامك وتلقَّ منبهاً حقيقياً في الوقت المحدد، حتى والتطبيق مغلق.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("ما اسمك؟ (اختياري)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text("لكي يعمل المنبه بدقة نحتاج:", style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    "هذه الصلاحيات تضمن أن يرن المنبه في وقته تماماً ويظهر فوق شاشة القفل.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = "الإشعارات",
                body = "لعرض المنبه وأزرار الإيقاف والغفوة والتذكيرات.",
                granted = status.notifications,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else SystemSettings.openNotificationSettings(context)
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow(
                    icon = Icons.Rounded.Alarm,
                    title = "المنبهات الدقيقة",
                    body = "ليرن المنبه في الدقيقة المحددة بالضبط، لا متأخراً.",
                    granted = status.exactAlarms,
                    onGrant = { SystemSettings.openExactAlarmSettings(context) }
                )
            }
            if (Build.VERSION.SDK_INT >= 34) {
                PermissionRow(
                    icon = Icons.Rounded.Fullscreen,
                    title = "شاشة المنبه الكاملة",
                    body = "لإظهار شاشة المنبه فوق شاشة القفل عند الرنين.",
                    granted = status.fullScreen,
                    onGrant = { SystemSettings.openFullScreenSettings(context) }
                )
            }
            if (SystemSettings.hasOemRestrictions) {
                ManualRow(
                    title = if (SystemSettings.isXiaomi) "العرض على شاشة القفل (شاومي)" else "العرض فوق التطبيقات وشاشة القفل",
                    body = if (SystemSettings.isXiaomi)
                        "افتح «أذونات أخرى» وفعّل: «العرض على شاشة القفل» و«عرض النوافذ المنبثقة أثناء العمل في الخلفية» و«التشغيل التلقائي». بدونها قد يرن المنبه دون أن تظهر شاشته."
                    else "في إعدادات التطبيق فعّل العرض فوق التطبيقات/على شاشة القفل والتشغيل التلقائي إن وُجدت.",
                    onOpen = {
                        if (SystemSettings.isXiaomi) SystemSettings.openLockScreenPermissions(context)
                        else SystemSettings.openAppDetails(context)
                    }
                )
            }
            PermissionRow(
                icon = Icons.Rounded.BatteryChargingFull,
                title = "استثناء من توفير البطارية (اختياري)",
                body = "موصى به في هواتف شاومي/هواوي/أوبو وغيرها التي توقف التطبيقات بقوة. التطبيق لا يستهلك البطارية في الخلفية.",
                granted = status.batteryUnrestricted,
                onGrant = { SystemSettings.openBatterySettings(context) }
            )

            Spacer(Modifier.height(24.dp))
            if (onboarding) {
                Button(
                    onClick = {
                        vm.setUserName(name)
                        vm.finishOnboarding()
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (status.alarmsReady) "ابدأ الآن" else "متابعة (يمكن منح الصلاحيات لاحقاً)") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(if (granted) cs.primaryContainer else cs.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (granted) cs.onPrimaryContainer else cs.onErrorContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, "ممنوحة", tint = cs.primary)
        } else {
            FilledTonalButton(onClick = onGrant) { Text("منح") }
        }
    }
}

/** إعداد لا يمكن التحقق منه برمجياً (قيود الشركات المصنّعة) — زر يفتح الصفحة الصحيحة. */
@Composable
private fun ManualRow(title: String, body: String, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(cs.secondaryContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Lock, null, tint = cs.onSecondary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onOpen) { Text("فتح") }
    }
}
