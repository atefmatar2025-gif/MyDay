package com.dayflow.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.dayflow.app.R

/** Human-readable name of a sound URI (null = default alarm sound). */
fun soundName(context: Context, uri: String?): String {
    if (uri == null) return context.getString(R.string.sound_default)
    return try {
        val u = Uri.parse(uri)
        RingtoneManager.getRingtone(context, u)?.getTitle(context)
            ?: fileName(context, u)
            ?: context.getString(R.string.sound_custom)
    } catch (e: Exception) {
        context.getString(R.string.sound_custom)
    }
}

fun fileName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: Exception) {
    null
}

/**
 * Two buttons: pick a system alarm tone, or pick any audio file on the device.
 * Files are referenced by URI with a persisted read permission (nothing is copied).
 */
@Composable
fun SoundPickerButtons(current: String?, onPicked: (String?) -> Unit) {
    val context = LocalContext.current
    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onPicked(uri?.toString())
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPicked(uri.toString())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let { Uri.parse(it) })
                runCatching { ringtone.launch(i) }
            },
            label = { androidx.compose.material3.Text(stringResource(R.string.pick_system_tone)) },
            leadingIcon = { Icon(Icons.Filled.MusicNote, null) },
        )
        AssistChip(
            onClick = { runCatching { file.launch(arrayOf("audio/*")) } },
            label = { androidx.compose.material3.Text(stringResource(R.string.pick_audio_file)) },
            leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
        )
    }
}

/** What the device offers for Arabic speech recognition. */
data class SpeechSupport(val available: Boolean, val onDevice: Boolean?)

fun speechSupport(context: Context): SpeechSupport {
    val available = SpeechRecognizer.isRecognitionAvailable(context) ||
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    val onDevice = if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.isOnDeviceRecognitionAvailable(context) else null
    return SpeechSupport(available, onDevice)
}

/**
 * Launches the device's speech recogniser for Arabic, preferring offline recognition. Whether it
 * really works offline depends on the installed recognition engine and its downloaded language
 * packs; the UI explains that and always offers text input.
 * Returns a function that starts listening; [onUnavailable] is called if no recogniser exists.
 */
@Composable
fun rememberSpeechInput(onText: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val prompt = stringResource(R.string.voice_prompt)
    val unavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                unavailable()
            }
        }
    }
}
