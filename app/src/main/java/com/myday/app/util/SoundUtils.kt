package com.myday.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings

object SoundUtils {

    /** نية منتقي نغمات النظام (مجموعة أصوات المنبه في الهاتف). */
    fun ringtonePickerIntent(current: String?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة المنبه")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                current?.let { Uri.parse(it) } ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            )
        }

    @Suppress("DEPRECATION")
    fun pickedUri(data: Intent?): Uri? =
        data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

    fun isDefault(uri: Uri?): Boolean = uri == null || uri == Settings.System.DEFAULT_ALARM_ALERT_URI

    fun title(context: Context, uri: Uri): String = try {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: displayName(context, uri) ?: "نغمة مخصصة"
    } catch (e: Exception) {
        displayName(context, uri) ?: "نغمة مخصصة"
    }

    /** اسم ملف صوتي اختاره المستخدم من ملفات الهاتف. */
    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    } catch (e: Exception) {
        null
    }

    /** الاحتفاظ بصلاحية القراءة الدائمة للملف المختار حتى يعمل بعد إعادة التشغيل. */
    fun persist(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    /** تجربة النغمة لثوانٍ. يعيد كائن Ringtone لإيقافه. */
    fun preview(context: Context, uriString: String?): Ringtone? = try {
        val uri = uriString?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    } catch (e: Exception) {
        null
    }
}
