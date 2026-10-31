@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.myday.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myday.app.alarm.CareNotifier
import com.myday.app.alarm.SimInfo
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.RadioButton
import com.myday.app.settings.AppSettings
import com.myday.app.settings.CareContact
import com.myday.app.settings.DEFAULT_CARE_MESSAGE
import com.myday.app.ui.AppViewModel

/** قسم «الرعاية والعائلة» في الإعدادات. */
@Composable
fun CareSection(vm: AppViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Pair<Int, CareContact>?>(null) }
    var editMessage by remember { mutableStateOf(false) }
    var hasSms by remember { mutableStateOf(CareNotifier.hasSmsPermission(context)) }
    var sims by remember { mutableStateOf<List<SimInfo>?>(null) }

    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) sims = CareNotifier.activeSims(context)
        else vm.message("بدون هذا الإذن تُرسل الرسالة من الشريحة الافتراضية في الهاتف")
    }
    fun openSimPicker() {
        if (CareNotifier.hasPhoneStatePermission(context)) sims = CareNotifier.activeSims(context)
        else phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasSms = ok
        if (!ok) vm.message("بدون إذن الرسائل لن يُرسل التطبيق تنبيهاً للعائلة")
    }
    fun ensureSms() { if (!hasSms) smsLauncher.launch(Manifest.permission.SEND_SMS) }

    // اختيار رقم من جهات الاتصال دون طلب صلاحية قراءة كل الجهات
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) editing = -1 to CareContact(c.getString(0) ?: "", c.getString(1) ?: "")
                }
            } catch (e: Exception) {
                vm.message("تعذر قراءة جهة الاتصال")
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatSize, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text("حجم الخط", style = MaterialTheme.typography.bodyLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf(1f to "عادي", 1.15f to "كبير", 1.3f to "كبير جداً").forEach { (v, label) ->
                FilterChip(selected = settings.textScale == v, onClick = { vm.setTextScale(v) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "عند عدم تأكيد «مهمة رعاية» (مثل الدواء) بعد كل محاولات الرنين، تُرسل رسالة SMS تلقائية لهؤلاء (حتى 3 أشخاص):",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        settings.careContacts.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name.ifBlank { c.phone }, style = MaterialTheme.typography.bodyLarge)
                    Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = i to c }) { Icon(Icons.Rounded.Edit, "تعديل") }
                IconButton(onClick = { vm.setCareContacts(settings.careContacts.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Rounded.Close, "حذف")
                }
            }
        }
        if (settings.careContacts.size < 3) {
            FlowRow(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = {
                    ensureSms()
                    pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }) {
                    Icon(Icons.Rounded.Contacts, null); Spacer(Modifier.width(6.dp)); Text("من جهات الاتصال")
                }
                TextButton(onClick = { ensureSms(); editing = -1 to CareContact("", "") }) { Text("رقم يدوي") }
            }
        }
        if (settings.careContacts.isNotEmpty()) {
            if (!hasSms) {
                TextButton(onClick = { ensureSms() }) { Text("⚠️ منح إذن إرسال الرسائل (مطلوب)", color = MaterialTheme.colorScheme.error) }
            }
            Row(
                Modifier.fillMaxWidth().clickable { openSimPicker() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SimCard, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("شريحة الإرسال", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.careSimSubId >= 0 && settings.careSimLabel.isNotBlank()) settings.careSimLabel
                        else "الافتراضية في إعدادات الهاتف",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                    )
                }
                Text("تغيير", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            FlowRow {
                TextButton(onClick = { editMessage = true }) { Text("نص الرسالة") }
                TextButton(onClick = { if (hasSms) vm.sendCareTest() else ensureSms() }) {
                    Icon(Icons.Rounded.Sms, null); Spacer(Modifier.width(6.dp)); Text("إرسال رسالة تجريبية")
                }
            }
        }
        if (!CareNotifier.deviceCanSendSms(context)) {
            Text("هذا الجهاز لا يدعم إرسال الرسائل النصية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    editing?.let { (index, contact) ->
        var name by remember(contact) { mutableStateOf(contact.name) }
        var phone by remember(contact) { mutableStateOf(contact.phone) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (index < 0) "جهة اتصال للرعاية" else "تعديل جهة الاتصال") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text("الاسم (مثلاً: ابني أحمد)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(20) },
                        label = { Text("رقم الهاتف") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = phone.count { it.isDigit() } >= 6, onClick = {
                    val c = CareContact(name.trim(), phone.trim())
                    val list = settings.careContacts.toMutableList()
                    if (index < 0) list.add(c) else list[index] = c
                    vm.setCareContacts(list)
                    editing = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } }
        )
    }

    sims?.let { list ->
        AlertDialog(
            onDismissRequest = { sims = null },
            title = { Text("الإرسال من أي شريحة؟") },
            text = {
                Column {
                    SimOption("الافتراضية في إعدادات الهاتف", settings.careSimSubId < 0) {
                        vm.setCareSim(-1, ""); sims = null
                    }
                    list.forEach { sim ->
                        SimOption(sim.label, settings.careSimSubId == sim.subId) {
                            vm.setCareSim(sim.subId, sim.label); sims = null
                        }
                    }
                    if (list.isEmpty()) {
                        Text("لم يُعثر على شرائح نشطة.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sims = null }) { Text("إغلاق") } }
        )
    }

    if (editMessage) {
        var text by remember { mutableStateOf(settings.careMessage) }
        AlertDialog(
            onDismissRequest = { editMessage = false },
            title = { Text("نص رسالة التنبيه") },
            text = {
                Column {
                    Text("يمكنك استخدام: {الاسم} {المهمة} {الوقت}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.take(300) }, minLines = 3)
                    TextButton(onClick = { text = DEFAULT_CARE_MESSAGE }) { Text("استعادة النص الافتراضي") }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setCareMessage(text); editMessage = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editMessage = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SimOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
