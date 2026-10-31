package com.myday.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myday.app.proof.CodeScanner
import com.myday.app.proof.QrTools

/**
 * «منبه بإثبات»: لا يتوقف إلا بمسح رمز في مكان محدد —
 * باركود علبة الدواء نفسها، أو رمز QR يطبعه التطبيق ويُلصق حيث تريد.
 */
@Composable
fun ProofSection(
    title: String,
    code: String?,
    label: String,
    onCode: (code: String?, label: String) -> Unit,
    onLabel: (String) -> Unit,
) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerLow).padding(14.dp)) {
        if (code == null) {
            Text(
                "اجعل المنبه لا يتوقف إلا بمسح رمز — مثل باركود علبة الدواء نفسها، أو رمز QR تلصقه في المطبخ أو الحمام فتضطر للنهوض.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scanning = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("مسح باركود")
                }
                OutlinedButton(onClick = { onCode(QrTools.newCode(), "رمز QR مطبوع") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("إنشاء QR")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QrCode2, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("لا يتوقف إلا بمسح الرمز ✓", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (code.startsWith("MYDAY-")) "رمز QR خاص بهذه المهمة — اطبعه أو احفظه" else "باركود مسجّل: $code",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = label, onValueChange = { onLabel(it.take(40)) },
                label = { Text("أين الرمز؟ (مثل: علبة الدواء، باب الثلاجة)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row {
                if (code.startsWith("MYDAY-")) {
                    TextButton(onClick = { QrTools.share(context, code, title.ifBlank { "مهمة" }) }) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("طباعة / حفظ الرمز")
                    }
                }
                TextButton(onClick = { onCode(null, "") }) { Text("إزالة", color = cs.error) }
            }
        }
    }

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("امسح باركود العلبة أو أي رمز", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    CodeScanner(Modifier.fillMaxWidth().height(340.dp)) { text ->
                        onCode(text.trim(), "")
                        scanning = false
                        true
                    }
                    TextButton(onClick = { scanning = false }, modifier = Modifier.align(Alignment.End)) { Text("إلغاء") }
                }
            }
        }
    }
}
