package com.myday.app.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** تنفيذ أفعال المهام: رسالة واتساب جاهزة، فتح الاتصال. (الـ SMS عبر CareNotifier) */
object ActionExecutor {

    /** رقم دولي بلا «+» كما يحتاجه واتساب (مثل 9725xxxxxxxx). */
    fun internationalDigits(context: Context, phone: String): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "US"
        val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(phone, iso) }.getOrNull()
        val digits = (e164 ?: phone).filter { it.isDigit() }
        return if (e164 == null && digits.startsWith("00")) digits.drop(2) else digits
    }

    fun whatsappIntent(context: Context, phone: String, text: String?): Intent {
        val uri = Uri.parse(
            "https://api.whatsapp.com/send?phone=${internationalDigits(context, phone)}" +
                (if (!text.isNullOrBlank()) "&text=${Uri.encode(text)}" else "")
        )
        val pm = context.packageManager
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { p ->
            runCatching { pm.getPackageInfo(p, 0); true }.getOrDefault(false)
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (pkg != null) setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** يفتح شاشة الاتصال والرقم جاهز (لا يحتاج صلاحية الاتصال المباشر). */
    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
