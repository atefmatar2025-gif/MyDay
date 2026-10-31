package com.dayflow.core.text

import com.dayflow.core.model.Frequency
import com.dayflow.core.model.Priority
import com.dayflow.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Result of turning a spoken/typed Arabic sentence into a task draft. */
data class ParsedTask(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val durationMinutes: Int?,
    val reminder: Boolean,
    val priority: Priority?,
    val recurrence: RecurrenceRule?,
    /** Human-readable notes about guesses the parser had to make. */
    val ambiguities: List<String>,
) {
    /** The UI must ask the user to confirm before saving when this is true. */
    val needsConfirmation: Boolean get() = ambiguities.isNotEmpty()
    val dateTime: LocalDateTime? get() = if (date != null && time != null) date.atTime(time) else null
}

/**
 * Deterministic, offline parser for Arabic task sentences such as
 * "ذكّرني غداً الساعة الثامنة صباحاً بمراجعة مادة البحث العلمي لمدة ساعة".
 *
 * It works on whitespace tokens: each token is normalised for matching, recognised tokens are
 * consumed, and the remaining original tokens (with their original spelling) form the title.
 * It is a rule-based parser, not a language model, and reports every guess in [ParsedTask.ambiguities].
 */
object VoiceTaskParser {

    private enum class Period { MORNING, NOON, AFTERNOON, EVENING }

    private val reminderVerbs = setOf("ذكرني", "ذكريني", "اذكرني", "نبهني", "نبهيني", "فكرني", "ذكرنا", "نبهنا")
    private val fillers = setOf("ان", "بان", "اني", "انني", "كي", "لكي", "اني")
    private val connectors = setOf("في", "عند", "على", "علي", "يوم", "بتاريخ", "تاريخ", "من")

    private val numberWords = mapOf(
        "واحد" to 1, "واحده" to 1, "الواحده" to 1,
        "اثنين" to 2, "اثنتين" to 2, "اثنان" to 2, "ثنتين" to 2, "الثانيه" to 2,
        "ثلاث" to 3, "ثلاثه" to 3, "الثالثه" to 3,
        "اربع" to 4, "اربعه" to 4, "الرابعه" to 4,
        "خمس" to 5, "خمسه" to 5, "الخامسه" to 5,
        "ست" to 6, "سته" to 6, "السادسه" to 6,
        "سبع" to 7, "سبعه" to 7, "السابعه" to 7,
        "ثمان" to 8, "ثماني" to 8, "ثمانيه" to 8, "الثامنه" to 8,
        "تسع" to 9, "تسعه" to 9, "التاسعه" to 9,
        "عشر" to 10, "عشره" to 10, "العاشره" to 10,
        "عشرين" to 20, "ثلاثين" to 30, "اربعين" to 40, "خمسين" to 50,
    )

    private val weekdays = mapOf(
        "السبت" to DayOfWeek.SATURDAY, "الاحد" to DayOfWeek.SUNDAY, "الاثنين" to DayOfWeek.MONDAY,
        "الثلاثاء" to DayOfWeek.TUESDAY, "الثلاثا" to DayOfWeek.TUESDAY, "الاربعاء" to DayOfWeek.WEDNESDAY,
        "الاربعا" to DayOfWeek.WEDNESDAY, "الخميس" to DayOfWeek.THURSDAY, "الجمعه" to DayOfWeek.FRIDAY,
    )

    private val periods = mapOf(
        "صباحا" to Period.MORNING, "صباح" to Period.MORNING, "الصبح" to Period.MORNING, "صبحا" to Period.MORNING,
        "فجرا" to Period.MORNING, "الفجر" to Period.MORNING,
        "ظهرا" to Period.NOON, "الظهر" to Period.NOON, "ظهر" to Period.NOON,
        "عصرا" to Period.AFTERNOON, "العصر" to Period.AFTERNOON,
        "مساء" to Period.EVENING, "مساءا" to Period.EVENING, "المساء" to Period.EVENING, "مسا" to Period.EVENING,
        "ليلا" to Period.EVENING, "الليل" to Period.EVENING, "بالليل" to Period.EVENING,
    )

    private class Match(val value: Int, val length: Int)

    fun parse(text: String, now: LocalDateTime): ParsedTask {
        val original = ArabicText.normalizeDigits(text).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val norm = original.map { ArabicText.normalize(it).replace(" ", "") }
        val used = BooleanArray(original.size)
        val today = now.toLocalDate()
        val ambiguities = mutableListOf<String>()

        var reminder = false
        var date: LocalDate? = null
        var hour: Int? = null
        var minute = 0
        var period: Period? = null
        var duration: Int? = null
        var priority: Priority? = null
        var relativeMinutes: Int? = null
        var recurrence: RecurrenceRule? = null

        fun mark(from: Int, toExclusive: Int) {
            for (k in from until toExclusive.coerceAtMost(original.size)) used[k] = true
            // A connector right before a recognised phrase ("في", "يوم") belongs to that phrase.
            if (from - 1 >= 0 && !used[from - 1] && norm[from - 1] in connectors) used[from - 1] = true
        }

        var i = 0
        while (i < original.size) {
            val n = norm[i]
            if (used[i] || n.isEmpty()) { i++; continue }

            // "ذكرني [أن]"
            if (n in reminderVerbs) {
                reminder = true
                mark(i, i + 1)
                if (i + 1 < norm.size && norm[i + 1] in fillers) used[i + 1] = true
                i++; continue
            }

            // "كل يوم / كل أسبوع / كل شهر / كل خميس"
            if (n == "كل" && i + 1 < norm.size) {
                val next = norm[i + 1]
                val rule = when {
                    next == "يوم" -> {
                        val wd = norm.getOrNull(i + 2)?.let { weekdays[it] }
                        if (wd != null) RecurrenceRule(Frequency.WEEKLY, daysOfWeek = setOf(wd)) else RecurrenceRule(Frequency.DAILY)
                    }
                    next in weekdays -> RecurrenceRule(Frequency.WEEKLY, daysOfWeek = setOf(weekdays.getValue(next)))
                    next == "اسبوع" -> RecurrenceRule(Frequency.WEEKLY)
                    next == "شهر" -> RecurrenceRule(Frequency.MONTHLY)
                    next == "سنه" || next == "عام" -> RecurrenceRule(Frequency.YEARLY)
                    else -> null
                }
                if (rule != null) {
                    recurrence = rule
                    val len = if (next == "يوم" && norm.getOrNull(i + 2)?.let { weekdays[it] } != null) 3 else 2
                    if (rule.frequency == Frequency.WEEKLY && rule.daysOfWeek.isNotEmpty() && date == null) {
                        date = nextWeekday(today, rule.daysOfWeek.first(), includeToday = true)
                    }
                    mark(i, i + len); i += len; continue
                }
            }

            // "بعد ساعتين" / "بعد 20 دقيقة" / "بعد غد"
            if (n == "بعد" && i + 1 < norm.size) {
                if (norm[i + 1] in setOf("غد", "غدا", "بكره", "بكرا")) {
                    date = today.plusDays(2); mark(i, i + 2); i += 2; continue
                }
                val d = matchDuration(norm, i + 1)
                if (d != null) {
                    relativeMinutes = d.value; mark(i, i + 1 + d.length); i += 1 + d.length; continue
                }
            }

            // "لمدة ساعة"
            if (n in setOf("لمده", "مده", "مدته", "مدتها", "لمدت")) {
                val d = matchDuration(norm, i + 1)
                if (d != null) {
                    duration = d.value; mark(i, i + 1 + d.length); i += 1 + d.length; continue
                }
            }

            // Day words
            when (n) {
                "اليوم" -> { date = today; mark(i, i + 1); i++; continue }
                "الليله" -> { date = today; if (period == null) period = Period.EVENING; mark(i, i + 1); i++; continue }
                "غدا", "غد", "بكره", "بكرا" -> { date = today.plusDays(1); mark(i, i + 1); i++; continue }
            }
            val wd = weekdays[n]
            if (wd != null) {
                // "الأسبوع القادم" handled below; "الخميس القادم" consumes the adjective too.
                var len = 1
                var d = nextWeekday(today, wd, includeToday = true)
                if (norm.getOrNull(i + 1) in setOf("القادم", "المقبل", "الجاي")) { len = 2; if (d == today) d = d.plusDays(7) }
                date = d; mark(i, i + len); i += len; continue
            }
            if (n == "الاسبوع" && norm.getOrNull(i + 1) in setOf("القادم", "المقبل", "الجاي")) {
                if (date == null) date = today.plusDays(7)
                mark(i, i + 2); i += 2; continue
            }
            val dateMatch = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$").find(n)
            if (dateMatch != null) {
                val m = dateMatch
                val day = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val yearRaw = m.groupValues[3]
                val parsed = runCatching {
                    var year = when {
                        yearRaw.isEmpty() -> today.year
                        yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                        else -> yearRaw.toInt()
                    }
                    var candidate = LocalDate.of(year, month, day)
                    if (yearRaw.isEmpty() && candidate.isBefore(today)) { year += 1; candidate = LocalDate.of(year, month, day) }
                    candidate
                }.getOrNull()
                if (parsed != null) { date = parsed; mark(i, i + 1); i++; continue }
            }

            // Clock: "الساعة الثامنة والنصف", "الساعة 8:30", or a bare "8:30"
            if (n == "الساعه" || n == "بالساعه") {
                val c = matchClock(norm, i + 1)
                if (c != null) {
                    hour = c.first; minute = c.second; mark(i, i + 1 + c.third); i += 1 + c.third; continue
                }
            }
            if (Regex("^\\d{1,2}:\\d{2}$").matches(n)) {
                val c = matchClock(norm, i)
                if (c != null) { hour = c.first; minute = c.second; mark(i, i + c.third); i += c.third; continue }
            }

            val p = periods[n]
            if (p != null) {
                period = p; mark(i, i + 1); i++; continue
            }

            // Priority words
            if (n in setOf("عاجل", "عاجله", "مستعجل", "ضروري", "ضروريه", "مهم")) {
                val very = norm.getOrNull(i + 1) == "جدا"
                priority = if (very || n.startsWith("عاجل")) {
                    if (very) Priority.URGENT else Priority.HIGH
                } else Priority.HIGH
                mark(i, i + if (very) 2 else 1); i += if (very) 2 else 1; continue
            }
            if (n == "اولويه" && norm.getOrNull(i + 1) in setOf("عاليه", "مرتفعه", "قصوى", "قصوي")) {
                priority = if (norm[i + 1].startsWith("قصو")) Priority.URGENT else Priority.HIGH
                mark(i, i + 2); i += 2; continue
            }
            i++
        }

        // ---- Resolve date & time ----
        var time: LocalTime? = null
        if (relativeMinutes != null) {
            val at = now.plusMinutes(relativeMinutes.toLong()).withSecond(0).withNano(0)
            date = at.toLocalDate(); time = at.toLocalTime()
        } else if (hour != null) {
            var h = hour
            var ambiguousHalf = false
            when (period) {
                Period.MORNING -> if (h == 12) h = 0
                Period.NOON -> if (h in 1..5) h += 12
                Period.AFTERNOON -> if (h < 12) h += 12
                Period.EVENING -> if (h in 1..11) h += 12 else if (h == 12) h = 0
                null -> when (h) {
                    in 13..23, 0 -> Unit
                    in 7..11 -> ambiguousHalf = true
                    12 -> ambiguousHalf = true
                    in 1..6 -> { h += 12; ambiguousHalf = true }
                }
            }
            var t = LocalTime.of(h % 24, minute.coerceIn(0, 59))
            if (date == null) {
                date = if (today.atTime(t).isAfter(now)) today else {
                    if (ambiguousHalf && t.hour < 12 && today.atTime(t.plusHours(12)).isAfter(now)) {
                        t = t.plusHours(12); today
                    } else today.plusDays(1)
                }
                if (period == null) ambiguities += "لم يُذكر اليوم، فافترضتُ ${if (date == today) "اليوم" else "الغد"}."
            } else if (ambiguousHalf && date == today && !today.atTime(t).isAfter(now) && t.hour < 12) {
                t = t.plusHours(12)
            }
            if (ambiguousHalf) ambiguities += "لم يتضح إن كان الوقت صباحاً أم مساءً، فاخترتُ ${Ar.time(t)}."
            time = t
        } else if (period != null && date != null) {
            // "غداً صباحاً" without a clock time: propose a sensible default and say so.
            time = when (period) {
                Period.MORNING -> LocalTime.of(9, 0)
                Period.NOON -> LocalTime.of(12, 30)
                Period.AFTERNOON -> LocalTime.of(16, 0)
                Period.EVENING -> LocalTime.of(20, 0)
            }
            ambiguities += "لم يُحدَّد وقت دقيق، فاقترحتُ ${Ar.time(time)}."
        }

        // ---- Title from the remaining original tokens ----
        val remaining = original.indices.filter { !used[it] }.toMutableList()
        while (remaining.isNotEmpty() && (norm[remaining.first()] in fillers || norm[remaining.first()] in connectors || norm[remaining.first()].isEmpty())) {
            remaining.removeAt(0)
        }
        while (remaining.isNotEmpty() && (norm[remaining.last()] in connectors || norm[remaining.last()].isEmpty())) {
            remaining.removeAt(remaining.lastIndex)
        }
        val words = remaining.map { original[it] }.toMutableList()
        if (reminder && words.isNotEmpty()) {
            val first = words[0]
            val firstNorm = ArabicText.normalize(first)
            // "ذكرني بمراجعة..." -> "مراجعة...", "ذكرني بالاتصال" -> "الاتصال"
            if (firstNorm.startsWith("ب") && firstNorm.length >= 4) words[0] = first.substring(1)
        }
        var title = words.joinToString(" ").trim().trim('.', '،', ',', '!', '؟', '?', '؛', ':', '-', ' ')
        if (title.isEmpty()) {
            title = "مهمة جديدة"
            ambiguities += "لم أتعرف على عنوان واضح للمهمة."
        }
        if (reminder && date == null && time == null) ambiguities += "طلبتَ تذكيراً دون وقت محدد."

        return ParsedTask(
            title = title,
            date = date,
            time = time,
            durationMinutes = duration,
            reminder = reminder || time != null,
            priority = priority,
            recurrence = recurrence,
            ambiguities = ambiguities,
        )
    }

    private fun nextWeekday(today: LocalDate, day: DayOfWeek, includeToday: Boolean): LocalDate =
        if (includeToday) today.with(TemporalAdjusters.nextOrSame(day)) else today.with(TemporalAdjusters.next(day))

    private fun number(token: String?): Int? {
        if (token == null) return null
        token.toIntOrNull()?.let { return it }
        return numberWords[token]
    }

    /** Parses "N دقيقة", "ساعة", "ساعتين", "نصف ساعة", "ساعة ونصف", "3 ساعات"... returns minutes. */
    private fun matchDuration(norm: List<String>, j: Int): Match? {
        val t0 = norm.getOrNull(j) ?: return null
        val t1 = norm.getOrNull(j + 1)
        fun halfAfter(base: Int, len: Int): Match {
            val extra = when (norm.getOrNull(j + len)) {
                "ونص", "ونصف", "والنصف" -> 30
                "وربع", "والربع" -> 15
                "وثلث", "والثلث" -> 20
                else -> 0
            }
            return Match(base + extra, if (extra > 0) len + 1 else len)
        }
        when (t0) {
            "ساعه" -> return halfAfter(60, 1)
            "ساعتين", "ساعتان" -> return halfAfter(120, 1)
            "نص", "نصف" -> if (t1 == "ساعه") return Match(30, 2)
            "ربع" -> if (t1 == "ساعه") return Match(15, 2)
            "ثلث" -> if (t1 == "ساعه") return Match(20, 2)
            "دقيقتين", "دقيقتان" -> return Match(2, 1)
        }
        val n = number(t0) ?: return null
        return when (t1) {
            "دقيقه", "دقائق", "د" -> Match(n, 2)
            "ساعه", "ساعات" -> halfAfter(n * 60, 2)
            else -> null
        }
    }

    /** Parses a clock expression starting at [j]: returns (hour, minute, tokensConsumed). */
    private fun matchClock(norm: List<String>, j: Int): Triple<Int, Int, Int>? {
        val t0 = norm.getOrNull(j) ?: return null
        Regex("^(\\d{1,2}):(\\d{2})$").find(t0)?.let {
            val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
            return if (h in 0..23 && m in 0..59) Triple(h, m, 1) else null
        }
        var hour: Int
        var len: Int
        if ((t0 == "الحاديه" || t0 == "الثانيه") && norm.getOrNull(j + 1) in setOf("عشر", "عشره")) {
            hour = if (t0 == "الحاديه") 11 else 12; len = 2
        } else {
            hour = number(t0) ?: return null; len = 1
        }
        if (hour !in 0..23) return null
        var minute = 0
        when (val next = norm.getOrNull(j + len)) {
            "والنصف", "ونص", "ونصف" -> { minute = 30; len++ }
            "والربع", "وربع" -> { minute = 15; len++ }
            "والثلث", "وثلث" -> { minute = 20; len++ }
            "الا" -> {
                val after = norm.getOrNull(j + len + 1)
                when (after) {
                    "ربع", "الربع" -> { minute = 45; hour = (hour + 23) % 24; len += 2 }
                    "ثلث", "الثلث" -> { minute = 40; hour = (hour + 23) % 24; len += 2 }
                    else -> {
                        val m = number(after)
                        if (m != null && m in 1..59) {
                            minute = 60 - m; hour = (hour + 23) % 24; len += 2
                            if (norm.getOrNull(j + len) in setOf("دقيقه", "دقائق")) len++
                        }
                    }
                }
            }
            else -> if (next != null && next.startsWith("و") && next.length > 1) {
                val m = number(next.substring(1))
                if (m != null && m in 1..59) {
                    minute = m; len++
                    if (norm.getOrNull(j + len) in setOf("دقيقه", "دقائق")) len++
                }
            }
        }
        return Triple(hour, minute, len)
    }
}
