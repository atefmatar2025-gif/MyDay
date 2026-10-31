@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myday.app.ui.quickadd

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.nlp.ArabicTaskParser
import com.myday.app.nlp.QuickTaskBuilder
import com.myday.app.ui.AppViewModel
import com.myday.app.util.Fmt
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private val EXAMPLES = listOf(
    "بكرا بعد العصر اتصل بأبو محمد",
    "قبل المغرب بنص ساعة اشتري خبز",
    "كل يوم الساعة 8 الصبح الدوا",
    "بعد ساعتين اطفي الفرن",
    "يوم الخميس الساعة 10 دكتور الأسنان مهم",
)

/**
 * إضافة مهمة بجملة عادية — كتابةً أو بالصوت — بالفصحى أو العامية.
 * الفهم يتم على الهاتف دون إنترنت؛ تحويل الصوت لنص تقوم به خدمة النظام.
 */
@Composable
fun QuickAddSheet(
    vm: AppViewModel,
    startWithVoice: Boolean,
    onDismiss: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenPrayerSettings: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prayerCfg by vm.prayerConfig.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(text) { delay(150); debounced = text }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) text = heard
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "قل مهمتك وموعدها…")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            vm.message("لا توجد خدمة تحويل كلام في الهاتف — اكتب المهمة بدلاً من ذلك")
        }
    }
    LaunchedEffect(Unit) { if (startWithVoice) listen() }

    val now = remember(debounced) { LocalDateTime.now() }
    val parsed = remember(debounced) { if (debounced.isBlank()) null else ArabicTaskParser.parse(debounced, now) }
    val due = remember(parsed, prayerCfg) { parsed?.let { QuickTaskBuilder.resolveDue(it, now, prayerCfg) } }
    val needsCity = parsed?.prayer != null && parsed.relative == null && prayerCfg == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("أضف مهمة بكلامك", style = MaterialTheme.typography.titleLarge)
            Text(
                "اكتب أو تكلّم كما تتحدث عادةً، بالفصحى أو العامية.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(200) },
                    placeholder = { Text("مثال: ${EXAMPLES.first()}") },
                    minLines = 2, maxLines = 4, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(onClick = { listen() }, modifier = Modifier.size(56.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.Mic, "تحدث", modifier = Modifier.size(28.dp))
                }
            }

            if (parsed == null) {
                Text("أمثلة:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                EXAMPLES.forEach { ex ->
                    TextButton(onClick = { text = ex }) { Text("«$ex»") }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فهمت:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(parsed.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(vertical = 4.dp))
                    when {
                        needsCity -> Text("مرتبطة بالصلاة — حدد مدينتك لحساب الوقت", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        due != null -> Text("🕒 ${Fmt.dateTime(due, settings.use24h)}", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (parsed.understood.isNotEmpty() || parsed.categoryName != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            parsed.understood.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                            parsed.categoryName?.let { AssistChip(onClick = {}, label = { Text("التصنيف: $it") }) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (needsCity) {
                    Button(onClick = { onDismiss(); onOpenPrayerSettings() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("تحديد المدينة")
                    }
                } else {
                    Button(
                        onClick = { if (vm.quickSave(parsed)) onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("إضافة المهمة") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.prepareDraft(parsed); onDismiss(); onOpenDraft() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("تعديل التفاصيل قبل الحفظ")
                }
            }
        }
    }
}
