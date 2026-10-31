package com.myday.app.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import com.myday.app.data.TaskEntity
import com.myday.app.settings.AppSettings
import com.myday.app.settings.CareContact
import com.myday.app.util.Fmt

/**
 * وضع الرعاية: إبلاغ أفراد العائلة برسالة SMS تلقائية عندما لا تُؤكَّد مهمة رعاية
 * (مثل دواء) بعد انتهاء كل محاولات الرنين. يعمل دون إنترنت.
 */
object CareNotifier {
    private const val TAG = "CareNotifier"

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun deviceCanSendSms(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)

    fun buildMessage(template: String, userName: String, task: TaskEntity, use24h: Boolean): String =
        template
            .replace("{الاسم}", userName.ifBlank { "صاحب الهاتف" })
            .replace("{المهمة}", task.title)
            .replace("{الوقت}", Fmt.time(task.dueAt, use24h))

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    /** يرسل رسالة واحدة؛ يرجع true عند تسليمها لنظام الرسائل. */
    fun send(context: Context, phone: String, text: String): Boolean {
        if (!hasSmsPermission(context)) return false
        return try {
            val sm = smsManager(context) ?: return false
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(phone.trim(), null, parts, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed to $phone", e)
            false
        }
    }

    /** الإبلاغ عند عدم الاستجابة. يرجع أسماء من أُبلغوا فعلاً. */
    fun escalate(context: Context, task: TaskEntity, settings: AppSettings): List<CareContact> {
        if (settings.careContacts.isEmpty()) return emptyList()
        val text = buildMessage(settings.careMessage, settings.userName, task, DateFormat.is24HourFormat(context))
        return settings.careContacts.filter { send(context, it.phone, text) }
    }
}
