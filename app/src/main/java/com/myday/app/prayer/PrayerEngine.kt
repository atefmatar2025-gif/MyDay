package com.myday.app.prayer

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val label: String) {
    FAJR("الفجر"), SUNRISE("الشروق"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء");

    companion object {
        fun fromName(n: String?): Prayer? = entries.firstOrNull { it.name == n }
    }
}

/** طرق الحساب المعتمدة. ishaMinutes > 0 تعني العشاء بعد المغرب بعدد ثابت من الدقائق. */
enum class CalcMethod(val label: String, val fajrAngle: Double, val ishaAngle: Double, val ishaMinutes: Int = 0) {
    UMM_AL_QURA("أم القرى (السعودية)", 18.5, 0.0, 90),
    EGYPT("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    MWL("رابطة العالم الإسلامي", 18.0, 17.0),
    KARACHI("جامعة العلوم الإسلامية – كراتشي", 18.0, 18.0),
    ISNA("أمريكا الشمالية (ISNA)", 15.0, 15.0),
    DUBAI("دبي (الإمارات)", 18.2, 18.2),
    KUWAIT("الكويت", 18.0, 17.5),
    QATAR("قطر", 18.0, 0.0, 90),
    JORDAN("الأردن", 18.0, 18.0),
    TURKEY("رئاسة الشؤون الدينية – تركيا", 18.0, 17.0),
    MOROCCO("المغرب", 19.0, 17.0),
    ALGERIA("الجزائر", 18.0, 17.0),
    TUNISIA("تونس", 18.0, 18.0);

    companion object {
        fun fromName(n: String?): CalcMethod = entries.firstOrNull { it.name == n } ?: MWL
    }
}

data class PrayerConfig(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val method: CalcMethod,
    val hanafiAsr: Boolean = false,
    /** تعديل يدوي بالدقائق لكل صلاة (بترتيب Prayer) لمطابقة التقويم المحلي */
    val adjustments: List<Int> = List(6) { 0 },
)

/** أوقات يوم واحد بالمللي ثانية (UTC epoch). */
data class DayPrayerTimes(val date: LocalDate, val times: Map<Prayer, Long>) {
    operator fun get(p: Prayer): Long? = times[p]
}

/**
 * حساب فلكي لأوقات الصلاة يعمل دون إنترنت (خوارزمية PrayTimes المعروفة).
 * تم التحقق من نتائجه مقابل تقويم أم القرى لمكة المكرمة.
 */
object PrayerEngine {

    private const val D2R = PI / 180.0
    private fun dsin(d: Double) = sin(d * D2R)
    private fun dcos(d: Double) = cos(d * D2R)
    private fun dtan(d: Double) = tan(d * D2R)
    private fun dasin(x: Double) = asin(x) / D2R
    private fun dacos(x: Double) = acos(x) / D2R
    private fun datan2(y: Double, x: Double) = atan2(y, x) / D2R
    private fun dacot(x: Double) = atan(1.0 / x) / D2R
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** يرجع (الميل، معادلة الزمن). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = datan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fix(ra, 24.0)
        val decl = dasin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun isRamadan(date: LocalDate): Boolean = try {
        HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) == 9
    } catch (e: Exception) {
        false
    }

    fun compute(date: LocalDate, cfg: PrayerConfig): DayPrayerTimes {
        val lat = cfg.latitude
        val lng = cfg.longitude
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        fun midDay(t: Double) = fix(12 - sunPosition(jd + t).second, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jd + t).first
            val noon = midDay(t)
            val arg = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            if (arg < -1.0 || arg > 1.0) return Double.NaN
            val tt = dacos(arg) / 15.0
            return if (ccw) noon - tt else noon + tt
        }
        fun asrTime(factor: Double, t: Double): Double {
            val decl = sunPosition(jd + t).first
            val angle = -dacot(factor + dtan(abs(lat - decl)))
            return angleTime(angle, t, false)
        }

        val m = cfg.method
        val sunrise = angleTime(0.833, 6.0 / 24, true)
        val sunset = angleTime(0.833, 18.0 / 24, false)
        var fajr = angleTime(m.fajrAngle, 5.0 / 24, true)
        val dhuhr = midDay(12.0 / 24)
        val asr = asrTime(if (cfg.hanafiAsr) 2.0 else 1.0, 13.0 / 24)
        val maghrib = sunset
        var isha = if (m.ishaMinutes > 0) {
            val mins = if (m == CalcMethod.UMM_AL_QURA && isRamadan(date)) 120 else m.ishaMinutes
            maghrib + mins / 60.0
        } else angleTime(m.ishaAngle, 18.0 / 24, false)

        // خطوط العرض العالية: قاعدة الزاوية (Angle-based) عند تعذر الحساب
        val night = fix(sunrise - sunset, 24.0)
        if (!sunrise.isNaN() && !sunset.isNaN()) {
            val fajrPortion = m.fajrAngle / 60.0 * night
            if (fajr.isNaN() || fix(sunrise - fajr, 24.0) > fajrPortion) fajr = sunrise - fajrPortion
            if (m.ishaMinutes == 0) {
                val ishaPortion = m.ishaAngle / 60.0 * night
                if (isha.isNaN() || fix(isha - sunset, 24.0) > ishaPortion) isha = sunset + ishaPortion
            }
        }

        val raw = listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
        val dayStartUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val map = LinkedHashMap<Prayer, Long>()
        Prayer.entries.forEachIndexed { i, p ->
            val h = raw[i]
            if (!h.isNaN()) {
                val utcHours = h - lng / 15.0
                var ms = dayStartUtc + (utcHours * 3_600_000.0).toLong()
                ms += cfg.adjustments.getOrElse(i) { 0 } * 60_000L
                // تقريب لأقرب دقيقة
                ms = ((ms + 30_000L) / 60_000L) * 60_000L
                map[p] = ms
            }
        }
        return DayPrayerTimes(date, map)
    }
}
