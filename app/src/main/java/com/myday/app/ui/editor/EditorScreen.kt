@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myday.app.ui.editor

import android.app.Activity
import android.media.Ringtone
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myday.app.data.CategoryEntity
import com.myday.app.data.Priority
import com.myday.app.data.RepeatType
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.components.CategoryIcons
import com.myday.app.ui.components.DatePickerModal
import com.myday.app.ui.components.TimePickerModal
import com.myday.app.ui.components.ActionEditor
import com.myday.app.ui.components.color
import com.myday.app.ui.theme.PriorityColors
import com.myday.app.ui.theme.isAppInDarkTheme
import com.myday.app.util.Fmt
import com.myday.app.util.SoundUtils
import com.myday.app.util.TaskTime
import com.myday.app.prayer.Prayer
import com.myday.app.prayer.PrayerConfig
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 30, 60, 1440)

@Composable
fun EditorScreen(
    appVm: AppViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenPrayerSettings: () -> Unit,
) {
    val prayerCfg by appVm.prayerConfig.collectAsStateWithLifecycle()
    val vm: EditorViewModel = viewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val categories by appVm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = isAppInDarkTheme(settings.themeMode)

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPastDialog by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Ringtone?>(null) }

    fun stopPreview() { runCatching { preview?.stop() }; preview = null }
    DisposableEffect(Unit) { onDispose { runCatching { preview?.stop() } } }
    LaunchedEffect(preview) { if (preview != null) { delay(6_000); stopPreview() } }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = SoundUtils.pickedUri(r.data)
            if (SoundUtils.isDefault(uri)) vm.update { it.copy(soundUri = null, soundName = null) }
            else if (uri != null) vm.update { it.copy(soundUri = uri.toString(), soundName = SoundUtils.title(context, uri)) }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (SoundUtils.persist(context, uri)) {
                vm.update { it.copy(soundUri = uri.toString(), soundName = SoundUtils.displayName(context, uri) ?: "ملف صوتي") }
            } else appVm.message("تعذر الوصول الدائم لهذا الملف، اختر ملفاً آخر")
        }
    }

    fun trySave(force: Boolean) {
        stopPreview()
        when (vm.save(force) { id -> onSaved(id) }) {
            SaveResult.NEED_TITLE -> appVm.message("اكتب عنوان المهمة أولاً")
            SaveResult.PAST_TIME -> showPastDialog = true
            SaveResult.NEED_LOCATION -> appVm.message("حدد مدينتك أولاً لحساب أوقات الصلاة")
            SaveResult.NEED_CONTACT -> appVm.message("اختر الشخص الذي تُرسل له أو تتصل به")
            SaveResult.NEED_MESSAGE -> appVm.message("اكتب نص الرسالة")
            SaveResult.SAVED -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "تعديل المهمة" else "مهمة جديدة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                actions = { TextButton(onClick = { trySave(false) }, enabled = s.loaded && !s.saving) { Text("حفظ") } }
            )
        }
    ) { padding ->
        if (!s.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = s.title, onValueChange = { v -> vm.update { it.copy(title = v.take(120)) } },
                label = { Text("عنوان المهمة *") }, singleLine = true, isError = s.titleError,
                supportingText = if (s.titleError) ({ Text("العنوان مطلوب") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.description, onValueChange = { v -> vm.update { it.copy(description = v.take(1000)) } },
                label = { Text("الوصف (اختياري)") }, minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Section("الموعد")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SegmentedButton(
                    selected = !s.prayerMode, onClick = { vm.update { it.copy(prayerMode = false) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("وقت محدد") }
                SegmentedButton(
                    selected = s.prayerMode, onClick = { vm.update { it.copy(prayerMode = true) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("مرتبط بالصلاة") }
            }
            if (!s.prayerMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerTile(Icons.Rounded.CalendarMonth, "التاريخ", Fmt.relativeDay(s.dueAt), Modifier.weight(1f)) { showDate = true }
                    PickerTile(Icons.Rounded.Schedule, "الوقت", Fmt.time(s.dueAt, settings.use24h), Modifier.weight(1f)) { showTime = true }
                }
            } else {
                PrayerTimePicker(s, prayerCfg, settings.use24h, onDate = { showDate = true }, onOpenPrayerSettings = onOpenPrayerSettings) { f ->
                    vm.update(f)
                }
            }

            Section("الأولوية")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val order = listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW)
                order.forEachIndexed { i, p ->
                    val c = PriorityColors.of(p, dark)
                    SegmentedButton(
                        selected = s.priority == p,
                        onClick = { vm.update { it.copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, order.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.copy(alpha = 0.2f), activeContentColor = c),
                    ) { Text(p.label) }
                }
            }

            Section("التصنيف")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.categoryId == null, onClick = { vm.update { it.copy(categoryId = null) } }, label = { Text("بلا تصنيف") })
                categories.forEach { c -> CategoryChip(c, s.categoryId == c.id) { vm.update { it.copy(categoryId = c.id) } } }
                FilterChip(selected = false, onClick = { showNewCategory = true }, label = { Text("جديد") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
            }

            Section("التكرار")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatType.entries.forEach { r ->
                    FilterChip(
                        selected = s.repeatType == r, onClick = { vm.update { it.copy(repeatType = r) } },
                        label = { Text(r.label) },
                        leadingIcon = if (r != RepeatType.NONE && s.repeatType == r) ({ Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp)) }) else null
                    )
                }
            }

            SwitchRow(Icons.Rounded.Star, "مهمة مهمة (مفضلة)", "تظهر في قائمة المهام المهمة", s.isFavorite) { v -> vm.update { it.copy(isFavorite = v) } }
            SwitchRow(
                Icons.Rounded.Favorite, "مهمة رعاية (دواء أو أمر حرج)",
                "تأكيد إلزامي بزر «تمّ»، وإبلاغ العائلة برسالة إن لم تُؤكَّد", s.isCare
            ) { v -> vm.update { it.copy(isCare = v, alarmEnabled = if (v) true else it.alarmEnabled, repeatIfNoResponse = if (v) true else it.repeatIfNoResponse) } }
            if (s.isCare && settings.careContacts.isEmpty()) {
                Text(
                    "لم تُضف جهة اتصال للعائلة بعد — أضفها من الإعدادات ◂ الرعاية والعائلة ليتم إبلاغها.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Section("المنبه")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 4.dp)
            ) {
                SwitchRow(Icons.Rounded.Alarm, "منبه صوتي في الموعد", "يرن بصوت واضح بملء الشاشة حتى لو كان التطبيق مغلقاً", s.alarmEnabled) { v ->
                    vm.update { it.copy(alarmEnabled = v) }
                }
                if (s.alarmEnabled) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("النغمة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(s.soundName ?: "الافتراضية (${settings.defaultSoundName})", style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = {
                                if (preview != null) stopPreview()
                                else preview = SoundUtils.preview(context, s.soundUri ?: settings.defaultSoundUri)
                            }) { Icon(if (preview != null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "تجربة النغمة") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(onClick = { stopPreview(); ringtoneLauncher.launch(SoundUtils.ringtonePickerIntent(s.soundUri)) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("نغمات الهاتف")
                            }
                            OutlinedButton(onClick = { stopPreview(); fileLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("من الملفات")
                            }
                        }
                        if (s.soundUri != null) {
                            TextButton(onClick = { vm.update { it.copy(soundUri = null, soundName = null) } }) { Text("العودة للنغمة الافتراضية") }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("مدة الرنين: ${Fmt.secondsLabel(s.soundDurationSec)}", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = s.soundDurationSec.toFloat(),
                            onValueChange = { v -> vm.update { it.copy(soundDurationSec = (v / 15f).roundToInt() * 15) } },
                            valueRange = 15f..300f, steps = 18
                        )
                    }
                    SwitchRow(Icons.Rounded.Repeat, "إعادة الرنين عند عدم الاستجابة", "إذا انتهت مدة الرنين ولم تضغط شيئاً", s.repeatIfNoResponse) { v ->
                        vm.update { it.copy(repeatIfNoResponse = v) }
                    }
                    if (s.repeatIfNoResponse) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text("كل", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2, 5, 10, 15).forEach { m ->
                                    FilterChip(selected = s.repeatIntervalMin == m, onClick = { vm.update { it.copy(repeatIntervalMin = m) } }, label = { Text("$m د") })
                                }
                            }
                            Text("عدد المرات", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3, 5).forEach { n ->
                                    FilterChip(selected = s.maxRepeats == n, onClick = { vm.update { it.copy(maxRepeats = n) } }, label = { Text("$n") })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Section("منبه بإثبات (اختياري)")
            ProofSection(
                title = s.title, code = s.proofCode, label = s.proofLabel,
                onCode = { c, l -> vm.update { it.copy(proofCode = c, proofLabel = l, alarmEnabled = if (c != null) true else it.alarmEnabled) } },
                onLabel = { l -> vm.update { it.copy(proofLabel = l) } },
            )

            Section("مهمة تُنجز نفسها (اختياري)")
            Text(
                "في الموعد تُرسل رسالة أو تفتح واتساب أو الاتصال بمن تختار.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            ActionEditor(
                type = s.actionType, onType = { t -> vm.update { it.copy(actionType = t) } },
                contactName = s.actionContactName, phone = s.actionPhone,
                onContact = { n, p -> vm.update { it.copy(actionContactName = n, actionPhone = p) } },
                message = s.actionMessage, onMessage = { m -> vm.update { it.copy(actionMessage = m) } },
                autoSend = s.actionAutoSend, onAutoSend = { a -> vm.update { it.copy(actionAutoSend = a) } },
                phrases = settings.phrases,
            )

            Section("تذكير قبل الموعد")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إشعار مسبق يذكّرك بالاستعداد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_OPTIONS.forEach { m ->
                    FilterChip(
                        selected = s.reminderMin == m, onClick = { vm.update { it.copy(reminderMin = m) } },
                        label = { Text(if (m == 0) "بدون" else "قبل ${Fmt.minutesLabel(m)}") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { trySave(false) }, enabled = !s.saving,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (s.isEdit) "حفظ التعديلات" else "إضافة المهمة") }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showDate) DatePickerModal(s.date, onDismiss = { showDate = false }) { d -> vm.update { it.copy(date = d) } }
    if (showTime) TimePickerModal(s.time, settings.use24h, onDismiss = { showTime = false }) { t -> vm.update { it.copy(time = t) } }
    if (showPastDialog) {
        AlertDialog(
            onDismissRequest = { showPastDialog = false },
            title = { Text("الموعد في الماضي") },
            text = { Text("الوقت المحدد قد مضى، لذلك لن يرن المنبه. هل تريد الحفظ على أي حال؟") },
            confirmButton = { TextButton(onClick = { showPastDialog = false; trySave(true) }) { Text("حفظ") } },
            dismissButton = {
                TextButton(onClick = { showPastDialog = false; if (s.prayerMode) showDate = true else showTime = true }) {
                    Text(if (s.prayerMode) "تغيير اليوم" else "تغيير الوقت")
                }
            }
        )
    }
    if (showNewCategory) {
        QuickCategoryDialog(onDismiss = { showNewCategory = false }) { name, color ->
            appVm.saveCategory(CategoryEntity(name = name, colorArgb = color, iconKey = "star"))
        }
    }
}

private val OFFSETS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

/** اختيار موعد نسبي لصلاة: «بعد العصر بـ 30 د». */
@Composable
private fun PrayerTimePicker(
    s: EditorState,
    cfg: PrayerConfig?,
    use24h: Boolean,
    onDate: () -> Unit,
    onOpenPrayerSettings: () -> Unit,
    update: ((EditorState) -> EditorState) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    if (cfg == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.tertiaryContainer).padding(14.dp)
        ) {
            Text("حدد مدينتك أولاً", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer)
            Text("نحتاجها لحساب أوقات الصلاة على هاتفك دون إنترنت.", style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
            TextButton(onClick = onOpenPrayerSettings) { Text("اختيار المدينة") }
        }
        return
    }
    PickerTile(Icons.Rounded.CalendarMonth, "اليوم", Fmt.relativeDay(s.dueAt), Modifier.fillMaxWidth(), onDate)
    Spacer(Modifier.height(10.dp))
    Text("الصلاة", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Prayer.entries.forEach { p ->
            FilterChip(selected = s.prayer == p, onClick = { update { it.copy(prayer = p) } }, label = { Text(p.label) })
        }
    }
    Text("التوقيت", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1 to "قبل", 0 to "عند الأذان", 1 to "بعد").forEach { (v, label) ->
            FilterChip(selected = s.relation == v, onClick = { update { it.copy(relation = v) } }, label = { Text(label) })
        }
    }
    if (s.relation != 0) {
        Text("بمقدار", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OFFSETS.forEach { m ->
                FilterChip(
                    selected = s.offsetAbs == m, onClick = { update { it.copy(offsetAbs = m) } },
                    label = { Text(if (m < 60) "$m د" else if (m == 60) "ساعة" else if (m == 90) "ساعة ونصف" else "ساعتان") }
                )
            }
        }
    }
    val due = s.effectiveDueAt(cfg)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp))
            .background(cs.primaryContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = cs.onPrimaryContainer)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(TaskTime.anchorLabel(s.prayer, s.offsetMin), style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            Text(
                if (due != null) {
                    "الموعد: ${Fmt.dateTime(due, use24h)}" +
                        (if (s.repeatType != RepeatType.NONE) " ثم يتغير تلقائياً مع وقت الصلاة" else "")
                } else "تعذر حساب الوقت لهذا الموقع",
                style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun PickerTile(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CategoryChip(c: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val col = c.color()
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(c.name) },
        leadingIcon = { Icon(CategoryIcons.get(c.iconKey), null, tint = col, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = col.copy(alpha = 0.18f))
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun QuickCategoryDialog(onDismiss: () -> Unit, onCreate: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CategoryIcons.palette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصنيف جديد") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("الاسم") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.palette.forEach { c ->
                        com.myday.app.ui.components.SelectableColorDot(androidx.compose.ui.graphics.Color(c), color == c) { color = c }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { onCreate(name, color); onDismiss() } }, enabled = name.isNotBlank()) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
