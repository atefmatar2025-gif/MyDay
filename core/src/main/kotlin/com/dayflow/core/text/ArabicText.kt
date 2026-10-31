package com.dayflow.core.text

import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.Priority
import com.dayflow.core.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Arabic text normalisation used for matching user input (never for display). */
object ArabicText {
    private val diacritics = Regex("[\\u064B-\\u0652\\u0670\\u0640]")

    fun normalizeDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '،' -> ',' // Arabic comma
                    '؟' -> '?'
                    else -> c
                },
            )
        }
    }

    fun normalize(input: String): String {
        var s = normalizeDigits(input).lowercase()
        s = diacritics.replace(s, "")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        // Keep ':' and '/' so clock times (8:30) and dates (15/11) survive normalisation.
        s = s.replace(Regex("[\\p{Punct}&&[^:/]]|[،؛؟«»]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun tokens(input: String): List<String> = normalize(input).split(' ').filter { it.isNotBlank() }
}

/**
 * Arabic display strings for the domain. All user-facing text produced by the core module goes
 * through this object, so an English implementation can be added later without touching logic.
 */
object Ar {
    private val months = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        DayOfWeek.MONDAY -> "الاثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
    }

    fun shortDayName(day: DayOfWeek): String = dayName(day).removePrefix("ال").take(3)

    fun monthName(month: Int): String = months[month - 1]

    fun date(d: LocalDate): String = "${dayName(d.dayOfWeek)} ${d.dayOfMonth} ${monthName(d.monthValue)} ${d.year}"

    fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${monthName(d.monthValue)}"

    fun time(t: LocalTime): String {
        val h12 = when (val h = t.hour % 12) { 0 -> 12; else -> h }
        val suffix = if (t.hour < 12) "ص" else "م"
        return "%d:%02d %s".format(h12, t.minute, suffix)
    }

    fun dateTime(dt: LocalDateTime, today: LocalDate): String {
        val d = dt.toLocalDate()
        val dayLabel = when (d) {
            today -> "اليوم"
            today.plusDays(1) -> "غداً"
            today.minusDays(1) -> "أمس"
            else -> shortDate(d)
        }
        return "$dayLabel ${time(dt.toLocalTime())}"
    }

    fun duration(minutes: Int): String {
        if (minutes <= 0) return "0 دقيقة"
        val h = minutes / 60
        val m = minutes % 60
        val hPart = when (h) {
            0 -> ""
            1 -> "ساعة"
            2 -> "ساعتان"
            in 3..10 -> "$h ساعات"
            else -> "$h ساعة"
        }
        val mPart = if (m == 0) "" else "$m دقيقة"
        return listOf(hPart, mPart).filter { it.isNotEmpty() }.joinToString(" و")
    }

    fun count(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 0 -> "لا $many"
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "$n $few"
        else -> "$n $many"
    }

    fun tasks(n: Int) = count(n, "مهمة واحدة", "مهمتان", "مهام", "مهمة")

    fun times(n: Int) = count(n, "مرة واحدة", "مرتين", "مرات", "مرة")

    fun conflicts(n: Int) = count(n, "تعارضاً واحداً", "تعارضين", "تعارضات", "تعارضاً")

    fun priority(p: Priority) = when (p) {
        Priority.URGENT -> "عاجلة جداً"
        Priority.HIGH -> "مرتفعة"
        Priority.MEDIUM -> "متوسطة"
        Priority.LOW -> "منخفضة"
    }

    fun status(s: TaskStatus) = when (s) {
        TaskStatus.NEW -> "جديدة"
        TaskStatus.SCHEDULED -> "مجدولة"
        TaskStatus.IN_PROGRESS -> "قيد التنفيذ"
        TaskStatus.COMPLETED -> "مكتملة"
        TaskStatus.POSTPONED -> "مؤجلة"
        TaskStatus.OVERDUE -> "متأخرة"
        TaskStatus.CANCELLED -> "ملغاة"
    }

    fun area(a: LifeArea) = when (a) {
        LifeArea.WORK -> "العمل"
        LifeArea.STUDY -> "الدراسة"
        LifeArea.WORSHIP -> "العبادة"
        LifeArea.FAMILY -> "الأسرة"
        LifeArea.HEALTH -> "الصحة"
        LifeArea.REST -> "الراحة"
        LifeArea.PERSONAL_GROWTH -> "التطوير الشخصي"
        LifeArea.SOCIAL -> "الالتزامات الاجتماعية"
        LifeArea.APPOINTMENT -> "المواعيد"
        LifeArea.PERSONAL -> "مهام شخصية"
    }
}
