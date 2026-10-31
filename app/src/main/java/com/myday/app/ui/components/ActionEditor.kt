@file:OptIn(ExperimentalLayoutApi::class)

package com.myday.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myday.app.alarm.CareNotifier
import com.myday.app.data.TaskAction

/** زر اختيار شخص ورقمه من جهات الاتصال (لا يحتاج صلاحية قراءة كل الجهات). */
@Composable
fun rememberContactPicker(onPicked: (name: String, phone: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { c -> if (c.moveToFirst()) onPicked(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
            }
        }
    }
    return { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
}

/**
 * محرر «المهمة التي تُنجز نفسها»: نوع الفعل، الشخص، نص الرسالة، والإرسال التلقائي.
 */
@Composable
fun ActionEditor(
    type: TaskAction?,
    onType: (TaskAction?) -> Unit,
    contactName: String,
    phone: String,
    onContact: (name: String, phone: String) -> Unit,
    message: String,
    onMessage: (String) -> Unit,
    autoSend: Boolean,
    onAutoSend: (Boolean) -> Unit,
    phrases: List<String>,
    showTypeChips: Boolean = true,
) {
    val context = LocalContext.current
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pick = rememberContactPicker { n, p -> onContact(n, p) }

    Column {
        if (showTypeChips) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == null, onClick = { onType(null) }, label = { Text("بدون") })
                TaskAction.entries.forEach { a ->
                    FilterChip(
                        selected = type == a,
                        onClick = {
                            onType(a)
                            if (a == TaskAction.SMS && !CareNotifier.hasSmsPermission(context)) smsPermission.launch(Manifest.permission.SEND_SMS)
                        },
                        label = { Text(a.label) }
                    )
                }
            }
        }
        if (type == null) return@Column

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (phone.isBlank()) {
                    Text("لم يُحدَّد الشخص", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(contactName.ifBlank { phone }, style = MaterialTheme.typography.bodyLarge)
                    Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = pick) {
                Icon(Icons.Rounded.Contacts, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (phone.isBlank()) "اختيار" else "تغيير")
            }
        }

        if (type != TaskAction.CALL) {
            OutlinedTextField(
                value = message, onValueChange = { onMessage(it.take(500)) },
                label = { Text(if (type == TaskAction.SMS) "نص الرسالة *" else "نص الرسالة (اختياري)") },
                minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (phrases.isNotEmpty()) {
                Text("من دفتر العبارات:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    phrases.take(8).forEach { ph -> AssistChip(onClick = { onMessage(ph) }, label = { Text(ph, maxLines = 1) }) }
                }
            }
        }
        if (type == TaskAction.SMS) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("إرسال تلقائي", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (autoSend) "تُرسل وحدها بعد 30 ثانية من الرنين ما لم تُلغِها" else "تُرسل فقط عند ضغطك «أرسل الآن»",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoSend, onCheckedChange = onAutoSend)
            }
            if (!CareNotifier.hasSmsPermission(context)) {
                TextButton(onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) }) {
                    Text("⚠️ امنح إذن إرسال الرسائل", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (type == TaskAction.WHATSAPP) {
            Text(
                "واتساب لا يسمح بالإرسال التلقائي: في الموعد تُفتح المحادثة والرسالة جاهزة وتضغط إرسال.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
