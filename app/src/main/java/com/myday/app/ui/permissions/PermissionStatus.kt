package com.myday.app.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.myday.app.alarm.NotificationHelper
import com.myday.app.container

data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** ما يلزم حتماً لكي يرن المنبه في وقته الدقيق ويظهر. */
    val alarmsReady: Boolean get() = notifications && exactAlarms && fullScreen

    companion object {
        fun check(context: Context): PermissionStatus {
            val pm = context.getSystemService(PowerManager::class.java)
            return PermissionStatus(
                notifications = NotificationHelper.canPost(context),
                exactAlarms = context.container.scheduler.canScheduleExact(),
                fullScreen = NotificationHelper.canUseFullScreen(context),
                batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
            )
        }
    }
}

/** حالة الصلاحيات، تُحدَّث تلقائياً عند العودة للتطبيق من شاشة الإعدادات. */
@Composable
fun rememberPermissionStatus(onChanged: (PermissionStatus) -> Unit = {}): State<PermissionStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(PermissionStatus.check(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val fresh = PermissionStatus.check(context)
        if (fresh != state.value) {
            state.value = fresh
            onChanged(fresh)
        }
    }
    return state
}

object SystemSettings {
    private fun Context.tryStart(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun openNotificationSettings(context: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.tryStart(i, appDetails(context))
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.tryStart(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                appDetails(context)
            )
        }
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            context.tryStart(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                appDetails(context)
            )
        }
    }

    fun openBatterySettings(context: Context) {
        context.tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context))
    }
}
