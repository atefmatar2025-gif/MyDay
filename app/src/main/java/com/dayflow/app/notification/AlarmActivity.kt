package com.dayflow.app.notification

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayflow.app.MainActivity
import com.dayflow.app.R
import com.dayflow.app.ui.theme.Brand
import com.dayflow.app.ui.theme.DayflowTheme
import java.util.Locale

/** Full-screen alarm UI (shown over the lock screen when Android allows full-screen intents). */
class AlarmActivity : ComponentActivity() {
    private var seenAlarm = false

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag("ar-u-nu-latn"))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val alarm by AlarmRingingService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(alarm) {
                if (alarm != null) seenAlarm = true
                // Close when the alarm was handled elsewhere (notification button, timeout).
                if (alarm == null && seenAlarm) finish()
            }
            DayflowTheme(themeMode = "DARK") {
                Column(
                    Modifier.fillMaxSize().background(Brand.DeepNavy).safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val a = alarm
                    Icon(Icons.Filled.Alarm, null, tint = Brand.WarmOrange, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(a?.timeText ?: "", style = MaterialTheme.typography.displaySmall, color = Brand.CreamWhite)
                    Spacer(Modifier.height(8.dp))
                    Text(a?.title ?: stringResource(R.string.alarm_ringing), style = MaterialTheme.typography.headlineSmall, color = Brand.CreamWhite, textAlign = TextAlign.Center)
                    if (!a?.description.isNullOrBlank()) {
                        Text(a!!.description, style = MaterialTheme.typography.bodyLarge, color = Brand.CreamWhite.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(40.dp))
                    Button(
                        onClick = { send(IntentKeys.ACTION_DISMISS_ALARM); finish() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.WarmOrange, contentColor = Brand.DeepNavy),
                    ) { Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.snooze_for), color = Brand.CreamWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(5, 10, 15, 30).forEach { m ->
                            FilledTonalButton(onClick = { send(IntentKeys.ACTION_SNOOZE, m); finish() }) {
                                Text(stringResource(R.string.minutes_short, m))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        val taskId = a?.taskId ?: 0
                        send(IntentKeys.ACTION_DISMISS_ALARM)
                        openTask(taskId)
                    }) { Text(stringResource(R.string.open_task), color = Color(0xFFA9C3EE)) }
                }
            }
        }
    }

    private fun send(action: String, minutes: Int = 0) {
        runCatching { startService(AlarmRingingService.command(this, action, minutes)) }
    }

    private fun openTask(taskId: Long) {
        val km = getSystemService(KeyguardManager::class.java)
        val open = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(IntentKeys.EXTRA_DESTINATION, IntentKeys.DEST_TASK)
                    .putExtra(IntentKeys.EXTRA_TASK_ID, taskId),
            )
            finish()
        }
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
                override fun onDismissCancelled() = finish()
            })
        } else open()
    }
}
