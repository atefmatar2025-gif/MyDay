@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.myday.app.ui.settings

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myday.app.settings.AppSettings
import com.myday.app.settings.ThemeMode
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.components.ConfirmDialog
import com.myday.app.util.Fmt
import com.myday.app.util.SoundUtils
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onOpenCategories: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = SoundUtils.pickedUri(r.data)
            if (SoundUtils.isDefault(uri)) vm.setDefaultSound(null, "نغمة المنبه الافتراضية")
            else if (uri != null) vm.setDefaultSound(uri.toString(), SoundUtils.title(context, uri))
        }
    }
    val soundFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && SoundUtils.persist(context, uri)) {
            vm.setDefaultSound(uri.toString(), SoundUtils.displayName(context, uri) ?: "ملف صوتي")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            Group("المظهر") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("السمة", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.themeMode == m, onClick = { vm.setThemeMode(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)
                            ) { Text(m.label) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(Icons.Rounded.ColorLens, "ألوان من خلفية الهاتف", "Material You", settings.dynamicColor, vm::setDynamicColor)
                }
                SwitchItem(Icons.Rounded.Schedule, "نظام 24 ساعة", if (settings.use24h) "14:30" else "2:30 م", settings.use24h, vm::setUse24h)
                ClickItem(Icons.Rounded.Person, "اسمك في الترحيب", settings.userName.ifBlank { "لم يُحدد" }) { editName = true }
            }

            Group("المنبه الافتراضي للمهام الجديدة") {
                ClickItem(Icons.Rounded.MusicNote, "النغمة الافتراضية", settings.defaultSoundName) {
                    soundLauncher.launch(SoundUtils.ringtonePickerIntent(settings.defaultSoundUri))
                }
                ClickItem(Icons.Rounded.MusicNote, "اختيار نغمة من ملفات الهاتف", "mp3 أو أي ملف صوتي") {
                    soundFileLauncher.launch(arrayOf("audio/*"))
                }
                SliderItem(Icons.Rounded.Timer, "مدة الرنين", settings.ringDurationSec, 15, 300, 15, Fmt::secondsLabel, vm::setRingDuration)
                SwitchItem(Icons.Rounded.Vibration, "الاهتزاز", "مع صوت المنبه", settings.vibrate, vm::setVibrate)
                SwitchItem(Icons.Rounded.GraphicEq, "رفع الصوت تدريجياً", "يبدأ هادئاً ثم يرتفع", settings.gradualVolume, vm::setGradualVolume)
                SwitchItem(Icons.Rounded.Repeat, "إعادة الرنين عند عدم الاستجابة", "كل ${settings.repeatIntervalMin} د حتى ${settings.maxRepeats} مرات",
                    settings.repeatIfNoResponse, vm::setRepeatIfNoResponse)
                if (settings.repeatIfNoResponse) {
                    SliderItem(Icons.Rounded.Repeat, "الفاصل بين المرات", settings.repeatIntervalMin, 1, 30, 1, { "$it د" }, vm::setRepeatInterval)
                    SliderItem(Icons.Rounded.Repeat, "أقصى عدد للإعادة", settings.maxRepeats, 1, 10, 1, { "$it" }, vm::setMaxRepeats)
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text("التذكير المسبق الافتراضي", style = MaterialTheme.typography.bodyLarge)
                    }
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(selected = settings.defaultReminderMin == m, onClick = { vm.setDefaultReminder(m) },
                                label = { Text(if (m == 0) "بدون" else Fmt.minutesLabel(m)) })
                        }
                    }
                }
            }

            Group("اختبار وضبط المنبه") {
                ClickItem(Icons.Rounded.AlarmAdd, "تجربة المنبه بعد دقيقة", "ينشئ مهمة اختبار؛ أغلق التطبيق واقفل الشاشة وانتظر") {
                    vm.createTestAlarm(60)
                }
                ClickItem(Icons.Rounded.Security, "الصلاحيات", "الإشعارات، المنبهات الدقيقة، شاشة القفل، البطارية", onOpenPermissions)
                ClickItem(Icons.Rounded.Alarm, "إعادة جدولة كل المنبهات", "استخدمه إذا شككت أن منبهاً لم يُضبط") {
                    vm.rescheduleAll(); vm.message("تمت إعادة جدولة كل المنبهات")
                }
            }

            Group("البيانات") {
                ClickItem(Icons.Rounded.Category, "التصنيفات", "إضافة وتعديل وحذف التصنيفات", onOpenCategories)
                ClickItem(Icons.Rounded.Backup, "تصدير نسخة احتياطية", "حفظ كل المهام والتصنيفات في ملف") {
                    exportLauncher.launch("MyDay-backup-${LocalDate.now()}.json")
                }
                ClickItem(Icons.Rounded.Restore, "استعادة نسخة احتياطية", "استبدال البيانات الحالية بمحتوى ملف") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            Group("حول") {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                ClickItem(Icons.Rounded.Info, "يومي – MyDay", "الإصدار $version • يعمل دون إنترنت • بلا إعلانات") {}
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(settings.userName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("اسمك") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("الاسم") }) },
            confirmButton = { TextButton(onClick = { vm.setUserName(name); editName = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("إلغاء") } }
        )
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            title = "استعادة النسخة؟",
            text = "ستُستبدل كل المهام والتصنيفات الحالية بمحتوى الملف. لا يمكن التراجع.",
            confirmLabel = "استعادة", destructive = true,
            onConfirm = { vm.importBackup(uri) },
            onDismiss = { confirmImport = null }
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        content()
    }
}

@Composable
private fun ClickItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** شريط تمرير يحفظ القيمة عند رفع الإصبع فقط (لتقليل الكتابة على القرص). */
@Composable
private fun SliderItem(
    icon: ImageVector, title: String, value: Int, min: Int, max: Int, step: Int,
    label: (Int) -> String, onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val snapped = (((local - min) / step).roundToInt() * step + min).coerceIn(min, max)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(snapped), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local, onValueChange = { local = it },
            onValueChangeFinished = { onCommit(snapped) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step - 1).coerceAtLeast(0)
        )
    }
}
