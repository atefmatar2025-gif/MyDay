package com.myday.app.prayer

import com.myday.app.prayer.CalcMethod.ALGERIA
import com.myday.app.prayer.CalcMethod.DUBAI
import com.myday.app.prayer.CalcMethod.EGYPT
import com.myday.app.prayer.CalcMethod.JORDAN
import com.myday.app.prayer.CalcMethod.KARACHI
import com.myday.app.prayer.CalcMethod.KUWAIT
import com.myday.app.prayer.CalcMethod.MOROCCO
import com.myday.app.prayer.CalcMethod.MWL
import com.myday.app.prayer.CalcMethod.QATAR
import com.myday.app.prayer.CalcMethod.TUNISIA
import com.myday.app.prayer.CalcMethod.TURKEY
import com.myday.app.prayer.CalcMethod.UMM_AL_QURA
import com.myday.app.prayer.CalcMethod.ISNA

data class City(val name: String, val country: String, val lat: Double, val lng: Double, val method: CalcMethod, val hanafi: Boolean = false)

/** مدن مدمجة لاختيار الموقع دون إنترنت ودون صلاحية الموقع. */
object Cities {
    val all: List<City> = listOf(
        // فلسطين
        City("القدس", "فلسطين", 31.7683, 35.2137, EGYPT),
        City("رام الله", "فلسطين", 31.9038, 35.2034, EGYPT),
        City("نابلس", "فلسطين", 32.2211, 35.2544, EGYPT),
        City("الخليل", "فلسطين", 31.5326, 35.0998, EGYPT),
        City("بيت لحم", "فلسطين", 31.7054, 35.2024, EGYPT),
        City("جنين", "فلسطين", 32.4610, 35.3000, EGYPT),
        City("طولكرم", "فلسطين", 32.3104, 35.0286, EGYPT),
        City("قلقيلية", "فلسطين", 32.1896, 34.9706, EGYPT),
        City("أريحا", "فلسطين", 31.8667, 35.4500, EGYPT),
        City("غزة", "فلسطين", 31.5017, 34.4668, EGYPT),
        City("خان يونس", "فلسطين", 31.3402, 34.3063, EGYPT),
        City("رفح", "فلسطين", 31.2969, 34.2455, EGYPT),
        City("الناصرة", "الداخل الفلسطيني", 32.6996, 35.3035, EGYPT),
        City("أم الفحم", "الداخل الفلسطيني", 32.5195, 35.1527, EGYPT),
        City("الطيبة", "الداخل الفلسطيني", 32.2667, 35.0103, EGYPT),
        City("الطيرة", "الداخل الفلسطيني", 32.2328, 34.9506, EGYPT),
        City("سخنين", "الداخل الفلسطيني", 32.8649, 35.2970, EGYPT),
        City("شفاعمرو", "الداخل الفلسطيني", 32.8056, 35.1694, EGYPT),
        City("حيفا", "الداخل الفلسطيني", 32.7940, 34.9896, EGYPT),
        City("عكا", "الداخل الفلسطيني", 32.9281, 35.0820, EGYPT),
        City("يافا", "الداخل الفلسطيني", 32.0504, 34.7522, EGYPT),
        City("اللد", "الداخل الفلسطيني", 31.9516, 34.8953, EGYPT),
        City("الرملة", "الداخل الفلسطيني", 31.9293, 34.8664, EGYPT),
        City("رهط", "الداخل الفلسطيني", 31.3925, 34.7544, EGYPT),
        City("بئر السبع", "الداخل الفلسطيني", 31.2518, 34.7913, EGYPT),
        City("كفر قاسم", "الداخل الفلسطيني", 32.1144, 34.9767, EGYPT),
        City("باقة الغربية", "الداخل الفلسطيني", 32.4183, 35.0428, EGYPT),
        // بلاد الشام
        City("عمّان", "الأردن", 31.9539, 35.9106, JORDAN),
        City("إربد", "الأردن", 32.5556, 35.8500, JORDAN),
        City("الزرقاء", "الأردن", 32.0728, 36.0880, JORDAN),
        City("العقبة", "الأردن", 29.5320, 35.0063, JORDAN),
        City("دمشق", "سوريا", 33.5138, 36.2765, EGYPT),
        City("حلب", "سوريا", 36.2021, 37.1343, EGYPT),
        City("حمص", "سوريا", 34.7324, 36.7137, EGYPT),
        City("بيروت", "لبنان", 33.8938, 35.5018, MWL),
        City("طرابلس (لبنان)", "لبنان", 34.4367, 35.8497, MWL),
        // الخليج والجزيرة
        City("مكة المكرمة", "السعودية", 21.4225, 39.8262, UMM_AL_QURA),
        City("المدينة المنورة", "السعودية", 24.4672, 39.6111, UMM_AL_QURA),
        City("الرياض", "السعودية", 24.7136, 46.6753, UMM_AL_QURA),
        City("جدة", "السعودية", 21.4858, 39.1925, UMM_AL_QURA),
        City("الدمام", "السعودية", 26.4207, 50.0888, UMM_AL_QURA),
        City("أبها", "السعودية", 18.2164, 42.5053, UMM_AL_QURA),
        City("تبوك", "السعودية", 28.3838, 36.5550, UMM_AL_QURA),
        City("دبي", "الإمارات", 25.2048, 55.2708, DUBAI),
        City("أبوظبي", "الإمارات", 24.4539, 54.3773, DUBAI),
        City("الشارقة", "الإمارات", 25.3463, 55.4209, DUBAI),
        City("الكويت", "الكويت", 29.3759, 47.9774, KUWAIT),
        City("الدوحة", "قطر", 25.2854, 51.5310, QATAR),
        City("المنامة", "البحرين", 26.2285, 50.5860, UMM_AL_QURA),
        City("مسقط", "عُمان", 23.5880, 58.3829, MWL),
        City("صنعاء", "اليمن", 15.3694, 44.1910, MWL),
        City("عدن", "اليمن", 12.7855, 45.0187, MWL),
        // العراق
        City("بغداد", "العراق", 33.3152, 44.3661, MWL),
        City("البصرة", "العراق", 30.5085, 47.7804, MWL),
        City("الموصل", "العراق", 36.3350, 43.1189, MWL),
        City("أربيل", "العراق", 36.1911, 44.0092, MWL),
        // مصر والسودان
        City("القاهرة", "مصر", 30.0444, 31.2357, EGYPT),
        City("الإسكندرية", "مصر", 31.2001, 29.9187, EGYPT),
        City("الجيزة", "مصر", 30.0131, 31.2089, EGYPT),
        City("أسوان", "مصر", 24.0889, 32.8998, EGYPT),
        City("الخرطوم", "السودان", 15.5007, 32.5599, EGYPT),
        // المغرب العربي
        City("طرابلس", "ليبيا", 32.8872, 13.1913, EGYPT),
        City("بنغازي", "ليبيا", 32.1167, 20.0667, EGYPT),
        City("تونس", "تونس", 36.8065, 10.1815, TUNISIA),
        City("صفاقس", "تونس", 34.7406, 10.7603, TUNISIA),
        City("الجزائر", "الجزائر", 36.7538, 3.0588, ALGERIA),
        City("وهران", "الجزائر", 35.6971, -0.6308, ALGERIA),
        City("قسنطينة", "الجزائر", 36.3650, 6.6147, ALGERIA),
        City("الرباط", "المغرب", 34.0209, -6.8416, MOROCCO),
        City("الدار البيضاء", "المغرب", 33.5731, -7.5898, MOROCCO),
        City("فاس", "المغرب", 34.0181, -5.0078, MOROCCO),
        City("مراكش", "المغرب", 31.6295, -7.9811, MOROCCO),
        City("نواكشوط", "موريتانيا", 18.0735, -15.9582, MWL),
        // أخرى
        City("إسطنبول", "تركيا", 41.0082, 28.9784, TURKEY),
        City("أنقرة", "تركيا", 39.9334, 32.8597, TURKEY),
        City("كراتشي", "باكستان", 24.8607, 67.0011, KARACHI, hanafi = true),
        City("جاكرتا", "إندونيسيا", -6.2088, 106.8456, MWL),
        City("كوالالمبور", "ماليزيا", 3.1390, 101.6869, MWL),
        City("لندن", "بريطانيا", 51.5074, -0.1278, MWL),
        City("برلين", "ألمانيا", 52.5200, 13.4050, MWL),
        City("باريس", "فرنسا", 48.8566, 2.3522, MWL),
        City("نيويورك", "الولايات المتحدة", 40.7128, -74.0060, ISNA),
        City("ديترويت", "الولايات المتحدة", 42.3314, -83.0458, ISNA),
    )

    fun search(q: String): List<City> {
        val t = q.trim()
        if (t.isEmpty()) return all
        return all.filter { it.name.contains(t) || it.country.contains(t) }
    }
}
