package com.myday.app.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import com.myday.app.settings.AppSettings
import com.myday.app.settings.CareContact
import com.myday.app.util.Fmt

data class SimInfo(val subId: Int, val label: String)

/**
 * وضع الرعاية: إبلاغ أفراد العائلة برسالة SMS تلقائية عندما لا تُؤكَّد مهمة رعاية
 * (مثل دواء) بعد انتهاء كل محاولات الرنين. يعمل دون إنترنت، ويدعم اختيار الشريحة.
 */
object CareNotifier {
    private const val TAG = "CareNotifier"

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun deviceCanSendSms(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    /** الشرائح النشطة في الهاتف (تتطلب إذن حالة الهاتف). */
    @SuppressLint("MissingPermission")
    fun activeSims(context: Context): List<SimInfo> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        return try {
            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            sm.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map { info ->
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.displayName?.toString().orEmpty()
                SimInfo(info.subscriptionId, "الشريحة ${info.simSlotIndex + 1}" + if (carrier.isNotBlank()) " – $carrier" else "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list SIMs", e)
            emptyList()
        }
    }

    fun buildMessage(template: String, userName: String, taskTitle: String, time: Long, use24h: Boolean): String =
        template
            .replace("{الاسم}", userName.ifBlank { "صاحب الهاتف" })
            .replace("{المهمة}", taskTitle)
            .replace("{الوقت}", Fmt.time(time, use24h))

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subId: Int): SmsManager? {
        val validSub = subId >= 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (validSub) base?.createForSubscriptionId(subId) else base
        } else {
            if (validSub) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }

    /** يرسل رسالة؛ يرجع true عند تسليمها لنظام الرسائل. */
    fun send(context: Context, phone: String, text: String, subId: Int = -1): Boolean {
        if (!hasSmsPermission(context)) return false
        return try {
            val sm = smsManager(context, subId) ?: return false
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(phone.trim(), null, parts, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed to $phone", e)
            // إن فشلت الشريحة المختارة (أُزيلت مثلاً) نحاول الشريحة الافتراضية
            if (subId >= 0) send(context, phone, text, -1) else false
        }
    }

    /** الإبلاغ عند عدم الاستجابة. يرجع أسماء من أُبلغوا فعلاً. */
    fun escalate(context: Context, task: com.myday.app.data.TaskEntity, settings: AppSettings): List<CareContact> {
        if (settings.careContacts.isEmpty()) return emptyList()
        val text = buildMessage(settings.careMessage, settings.userName, task.title, task.dueAt, DateFormat.is24HourFormat(context))
        return settings.careContacts.filter { send(context, it.phone, text, settings.careSimSubId) }
    }
}
