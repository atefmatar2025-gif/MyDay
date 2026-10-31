package com.myday.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

object Fmt {
    val AR: Locale = Locale.forLanguageTag("ar")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val time12 = DateTimeFormatter.ofPattern("h:mm a", AR)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", AR)
    private val timeSec12 = DateTimeFormatter.ofPattern("h:mm:ss a", AR)
    private val timeSec24 = DateTimeFormatter.ofPattern("HH:mm:ss", AR)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", AR)
    private val shortDate = DateTimeFormatter.ofPattern("EEEE d MMMM", AR)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", AR)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", AR)
    private val dayName = DateTimeFormatter.ofPattern("EEEE", AR)

    fun toLdt(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun toDate(millis: Long): LocalDate = toLdt(millis).toLocalDate()
    fun toMillis(ldt: LocalDateTime): Long = ldt.atZone(zone).toInstant().toEpochMilli()
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDay(date: LocalDate): Long = startOfDay(date.plusDays(1))

    fun time(millis: Long, use24h: Boolean): String = toLdt(millis).format(if (use24h) time24 else time12)
    fun timeWithSeconds(ldt: LocalDateTime, use24h: Boolean): String = ldt.format(if (use24h) timeSec24 else timeSec12)
    fun fullDate(date: LocalDate): String = date.format(fullDate)
    fun monthYear(date: LocalDate): String = date.format(monthYear)
    fun dayName(date: LocalDate): String = date.format(dayName)
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** "اليوم" / "غداً" / "أمس" أو التاريخ المختصر */
    fun relativeDay(millis: Long): String {
        val d = toDate(millis)
        val today = LocalDate.now(zone)
        return when (d) {
            today -> "اليوم"
            today.plusDays(1) -> "غداً"
            today.minusDays(1) -> "أمس"
            else -> d.format(shortDate)
        }
    }

    fun dateTime(millis: Long, use24h: Boolean): String = "${relativeDay(millis)} • ${time(millis, use24h)}"

    fun hijri(date: LocalDate): String = try {
        val h = HijrahDate.from(date)
        val months = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )
        val m = h.get(ChronoField.MONTH_OF_YEAR)
        "${h.get(ChronoField.DAY_OF_MONTH)} ${months[(m - 1).coerceIn(0, 11)]} ${h.get(ChronoField.YEAR)} هـ"
    } catch (e: Exception) {
        ""
    }

    fun minutesLabel(min: Int): String = when {
        min <= 0 -> "بدون"
        min < 60 -> "$min دقيقة"
        min == 60 -> "ساعة"
        min < 1440 -> "${min / 60} ساعات"
        min == 1440 -> "يوم"
        else -> "${min / 1440} أيام"
    }

    fun secondsLabel(sec: Int): String = when {
        sec < 60 -> "$sec ثانية"
        sec % 60 == 0 -> if (sec == 60) "دقيقة" else "${sec / 60} دقائق"
        else -> "${sec / 60}:${(sec % 60).toString().padStart(2, '0')} دقيقة"
    }

    /** أيام الأسبوع تبدأ بالسبت (الشائع في الدول العربية) */
    val weekStart: DayOfWeek = DayOfWeek.SATURDAY
    val weekDaysShort = listOf("سبت", "أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")

    fun startOfWeek(date: LocalDate): LocalDate {
        var d = date
        while (d.dayOfWeek != weekStart) d = d.minusDays(1)
        return d
    }

    fun countdown(target: Long, now: Long): String {
        val diff = target - now
        if (diff <= 0) return "الآن"
        val mins = diff / 60_000
        return when {
            mins < 1 -> "أقل من دقيقة"
            mins < 60 -> "بعد $mins دقيقة"
            mins < 1440 -> "بعد ${mins / 60} س ${mins % 60} د"
            else -> "بعد ${mins / 1440} يوم"
        }
    }
}
