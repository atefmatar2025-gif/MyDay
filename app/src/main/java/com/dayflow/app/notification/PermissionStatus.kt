package com.dayflow.app.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Snapshot of everything that affects whether reminders ring on time. */
data class PermissionStatus(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val alarmChannelEnabled: Boolean,
) {
    val allGood get() = notificationsAllowed && exactAlarmsAllowed && fullScreenAllowed && alarmChannelEnabled

    companion object {
        fun read(context: Context): PermissionStatus {
            val nm = NotificationManagerCompat.from(context)
            val notifPermission = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val channel = nm.getNotificationChannel(Channels.ALARMS)
            return PermissionStatus(
                notificationsAllowed = notifPermission && nm.areNotificationsEnabled(),
                exactAlarmsAllowed = AlarmScheduler(context).canScheduleExact(),
                fullScreenAllowed = nm.canUseFullScreenIntent(),
                ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
                alarmChannelEnabled = channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE,
            )
        }

        fun appNotificationSettings(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        fun exactAlarmSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else appDetails(context)

        fun fullScreenSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            } else appNotificationSettings(context)

        /** Opens the system list (does not request the exemption directly - Play policy friendly). */
        fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        fun appDetails(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
}
