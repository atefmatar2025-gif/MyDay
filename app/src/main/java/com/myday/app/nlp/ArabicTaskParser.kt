package com.myday.app.nlp

import com.myday.app.data.Priority
import com.myday.app.data.RepeatType
import com.myday.app.prayer.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** نتيجة فهم جملة مثل «ذكّرني بكرة بعد العصر أتصل بأبو محمد». */
data class ParsedTask(
    val title: String,
    /** تاريخ صريح أو مستنتج (null = لم يُذكر) */
    val date: LocalDate? = null,
    /** وقت صريح (null = لم يُذكر) */
    val time: LocalTime? = null,
    /** موعد نسبي محسوب مثل «بعد ساعتين» */
    val relative: LocalDateTime? = null,
    val prayer: Prayer? = null,
    val prayerOffsetMin: Int = 0,
    val repeat: RepeatType = RepeatType.NONE,
    val priority: Priority? = null,
    val isCare: Boolean = false,
    /** اسم التصنيف الافتراضي المقترح */
    val categoryName: String? = null,
    /** الأجزاء التي فُهمت، لعرضها للمستخدم */
    val understood: List<String> = emptyList(),
)

/**
 * محلل جمل عربية (فصحى ولهجات شامية/خليجية/مصرية شائعة) لاستخراج مهمة وموعدها.
 * يعمل بالكامل على الهاتف دون إنترنت. القواعد مقصودة وبسيطة وقابلة للاختبار.
 */
object ArabicTaskParser {

    // ---------------- التطبيع ----------------

    private val tashkeel = Regex("[\\u064B-\\u0652\\u0670\\u0640]")

    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in tashkeel.replace(s, "")) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    '،', '؟', '!', '؛', ',', '?', '.', '"', '«', '»', '(', ')' -> ' '
                    else -> ch
                }
            )
        }
        return sb.toString().lowercase()
    }

    // ---------------- القواميس ----------------

    private val startFillers = setOf(
        "ذكرني", "ذكريني", "فكرني", "فكريني", "نبهني", "نبهيني", "صحيني", "صحيني", "ذكرنى",
        "سجل", "سجلي", "حط", "حطلي", "اضف", "ضيف", "ضيفي", "اعمل", "اعملي", "تذكير", "منبه", "مهمه",
        "بدي", "بدك", "اريد", "ابغى", "ابغي", "ابي", "عايز", "عاوز", "عاوزه", "عايزه", "من", "فضلك", "لو", "سمحت",
        "يا", "يومي",
    )
    private val leadingConnectors = setOf("ان", "اني", "انه", "انو", "انا", "عشان", "علشان", "حتى", "لازم", "اني", "انك", "انني", "ب")
    private val trailingConnectors = setOf("في", "على", "ع", "و", "يوم", "الساعه", "الموعد")

    private val numberWords = mapOf(
        "واحد" to 1, "وحده" to 1, "وحدة" to 1,
        "اتنين" to 2, "اثنين" to 2, "اثنتين" to 2, "تنتين" to 2, "ثنتين" to 2, "ثنين" to 2,
        "تلاته" to 3, "ثلاثه" to 3, "ثلاث" to 3, "تلات" to 3,
        "اربعه" to 4, "اربع" to 4,
        "خمسه" to 5, "خمس" to 5,
        "سته" to 6, "ست" to 6,
        "سبعه" to 7, "سبع" to 7,
        "تمانيه" to 8, "ثمانيه" to 8, "ثمان" to 8, "تمن" to 8, "ثمن" to 8,
        "تسعه" to 9, "تسع" to 9,
        "عشره" to 10, "عشر" to 10,
        "حداشر" to 11, "احدعش" to 11, "احدعشر" to 11, "حدعش" to 11,
        "اطناشر" to 12, "اتناشر" to 12, "اثنعشر" to 12, "ثنعش" to 12, "طناشر" to 12,
        "عشرين" to 20, "خمسطعش" to 15, "ربعطعش" to 14, "تلاتين" to 30, "ثلاثين" to 30, "اربعين" to 40, "خمسين" to 50,
    )

    private val prayerWords = mapOf(
        "فجر" to Prayer.FAJR,
        "شروق" to Prayer.SUNRISE,
        "ظهر" to Prayer.DHUHR, "ضهر" to Prayer.DHUHR,
        "عصر" to Prayer.ASR,
        "مغرب" to Prayer.MAGHRIB,
        "عشا" to Prayer.ISHA, "عشاء" to Prayer.ISHA, "عشاه" to Prayer.ISHA,
    )

    private val weekdayWords = mapOf(
        "السبت" to DayOfWeek.SATURDAY, "سبت" to DayOfWeek.SATURDAY,
        "الاحد" to DayOfWeek.SUNDAY,
        "الاثنين" to DayOfWeek.MONDAY, "الاتنين" to DayOfWeek.MONDAY, "التنين" to DayOfWeek.MONDAY,
        "الثلاثاء" to DayOfWeek.TUESDAY, "الثلاثا" to DayOfWeek.TUESDAY, "التلاتا" to DayOfWeek.TUESDAY, "التلات" to DayOfWeek.TUESDAY, "الثلوث" to DayOfWeek.TUESDAY,
        "الاربعاء" to DayOfWeek.WEDNESDAY, "الاربعا" to DayOfWeek.WEDNESDAY, "الاربع" to DayOfWeek.WEDNESDAY,
        "الخميس" to DayOfWeek.THURSDAY, "خميس" to DayOfWeek.THURSDAY,
        "الجمعه" to DayOfWeek.FRIDAY, "جمعه" to DayOfWeek.FRIDAY,
    )

    private val todayWords = setOf("اليوم", "النهارده", "النهاردا", "هاليوم", "اليوم")
    private val tonightWords = setOf("الليله", "هالليله", "الليلة")
    private val tomorrowWords = setOf("بكرا", "بكره", "غدا", "غد", "باكر", "باچر", "الغد", "بكرى")
    private val morningWords = setOf("الصبح", "الصباح", "صباحا", "صباح", "صبح", "بدري", "الفجريه")
    private val noonWords = setOf("الظهر", "الضهر", "ظهرا", "الظهريه")
    private val afternoonWords = setOf("العصر", "العصريه", "عصرا")
    private val eveningWords = setOf("المسا", "المساء", "مساء", "مسا", "مساءا", "المغرب", "العشا", "العشاء")
    private val nightWords = setOf("الليل", "بالليل", "ليلا", "بليل", "الليله")

    private val highPriorityWords = setOf("مهم", "مهمه", "ضروري", "ضروريه", "مستعجل", "مستعجله", "عاجل", "عاجله", "جدا")
    private val careWords = setOf(
        "دوا", "الدوا", "دواء", "الدواء", "ادويه", "الادويه", "حبه", "الحبه", "حبوب", "الحبوب", "علاج", "العلاج",
        "ابره", "الابره", "انسولين", "الانسولين", "الضغط", "السكري", "السكر", "قطره", "القطره", "شراب", "فيتامين",
    )

    private val categoryKeywords: List<Pair<String, Set<String>>> = listOf(
        "الصحة" to (careWords + setOf("دكتور", "الدكتور", "طبيب", "الطبيب", "مستشفى", "المستشفى", "عياده", "العياده", "تحليل", "صيدليه", "الصيدليه", "رياضه", "مشي")),
        "العبادة" to setOf("صلاه", "الصلاه", "قران", "القران", "ورد", "الورد", "اذكار", "الاذكار", "صيام", "صوم", "الصوم", "سنه", "قيام", "تسبيح", "دعاء", "الدعاء", "جامع", "الجامع", "المسجد", "مسجد"),
        "الدراسة" to setOf("درس", "الدرس", "دراسه", "ادرس", "امتحان", "الامتحان", "واجب", "الواجب", "محاضره", "المحاضره", "جامعه", "الجامعه", "مدرسه", "المدرسه", "كتاب", "الكتاب", "اقرا"),
        "العمل" to setOf("شغل", "الشغل", "دوام", "الدوام", "اجتماع", "الاجتماع", "ايميل", "تقرير", "التقرير", "مدير", "المدير", "عميل", "زبون", "الزبون", "مشروع", "المشروع", "فاتوره", "الفاتوره"),
        "التسوق" to setOf("اشتري", "اشتريت", "شراء", "جيب", "اجيب", "سوق", "السوق", "سوبرماركت", "البقاله", "دكان", "الدكان", "خبز", "الخبز", "حليب", "خضره", "الخضره"),
        "المهام المنزلية" to setOf("نظف", "انظف", "تنظيف", "اغسل", "غسيل", "الغسيل", "اطبخ", "طبخ", "الطبخ", "اكوي", "كوي", "ارتب", "رتب", "الزبالة", "الزباله", "الجلي", "اجلي"),
        "الأسرة" to setOf("امي", "ابوي", "ابي", "اخوي", "اختي", "اهلي", "زوجتي", "جوزي", "زوجي", "ولادي", "اولادي", "بنتي", "ابني", "سيدي", "ستي", "جدي", "جدتي", "خالتي", "عمتي", "خالي", "عمي"),
        "المواعيد الشخصية" to setOf("موعد", "الموعد", "زياره", "الزياره", "اتصل", "اتصال", "ازور", "حلاق", "الحلاق", "عزومه", "عرس", "العرس"),
    )

    // ---------------- أدوات ----------------

    private class Tok(val raw: String, val n: String) {
        var used = false
    }

    /** يحاول فهم كلمة كرقم (أرقام أو كلمات، مع «ال» أو بدونها). */
    private fun number(n: String): Int? {
        n.toIntOrNull()?.let { return it }
        numberWords[n]?.let { return it }
        if (n.startsWith("ال")) numberWords[n.removePrefix("ال")]?.let { return it }
        return null
    }

    /** يزيل حروف الجر الملتصقة الشائعة: بال، عال، لل، وال، ب، ع، ل، و، ال */
    private fun bases(n: String): List<String> {
        val out = linkedSetOf(n)
        for (p in listOf("بال", "عال", "وال", "فال", "لل", "ال", "ب", "ع", "و", "ل")) {
            if (n.length > p.length + 1 && n.startsWith(p)) out += n.removePrefix(p)
        }
        // اسم بعد إزالة «ال» أيضاً
        out.toList().forEach { if (it.startsWith("ال") && it.length > 3) out += it.removePrefix("ال") }
        return out.toList()
    }

    private fun prayerOf(n: String): Prayer? = bases(n).firstNotNullOfOrNull { prayerWords[it] }

    /** يقبل «الخميس» و«بالخميس» و«عالخميس»، ويتجنب كلمات قصيرة مثل «حد» (أي شخص). */
    private fun weekdayOf(n: String): DayOfWeek? =
        weekdayWords[n] ?: bases(n).filter { it.length >= 3 }
            .firstNotNullOfOrNull { b -> weekdayWords[b] ?: weekdayWords["ال$b"].takeIf { b.length >= 4 } }

    /** يقرأ مدة مثل «ساعه/ساعتين/نص ساعه/ربع ساعه/10 دقايق/عشر دقايق/يومين/3 ايام/اسبوع». */
    private data class Duration(val minutes: Long = 0, val days: Long = 0, val weeks: Long = 0, val months: Long = 0, val consumed: Int)

    private fun readDuration(t: List<Tok>, i: Int, stripBa: Boolean): Duration? {
        if (i >= t.size) return null
        fun w(k: Int): String? = t.getOrNull(i + k)?.n?.let { if (k == 0 && stripBa && it.startsWith("ب") && it.length > 1) it.removePrefix("ب") else it }
        val a = w(0) ?: return null
        val b = w(1)
        when (a) {
            "ساعه" -> return Duration(minutes = 60, consumed = 1)
            "ساعتين" -> return Duration(minutes = 120, consumed = 1)
            "دقيقه" -> return Duration(minutes = 1, consumed = 1)
            "دقيقتين" -> return Duration(minutes = 2, consumed = 1)
            "يوم" -> return Duration(days = 1, consumed = 1)
            "يومين" -> return Duration(days = 2, consumed = 1)
            "اسبوع", "جمعه" -> return Duration(weeks = 1, consumed = 1)
            "اسبوعين", "جمعتين" -> return Duration(weeks = 2, consumed = 1)
            "شهر" -> return Duration(months = 1, consumed = 1)
            "شهرين" -> return Duration(months = 2, consumed = 1)
            "نص", "نصف" -> if (b == "ساعه" || b == "ساعة") return Duration(minutes = 30, consumed = 2)
            "ربع" -> if (b == "ساعه" || b == "ساعة") return Duration(minutes = 15, consumed = 2)
        }
        val num = number(a) ?: return null
        when (b) {
            "دقيقه", "دقايق", "دقائق", "دقيقة", "د" -> return Duration(minutes = num.toLong(), consumed = 2)
            "ساعه", "ساعات", "ساعة" -> {
                // «ساعه ونص»
                val c = t.getOrNull(i + 2)?.n
                val extra = if (c == "ونص" || c == "ونصف") 30 else 0
                return Duration(minutes = num * 60L + extra, consumed = if (extra > 0) 3 else 2)
            }
            "يوم", "ايام" -> return Duration(days = num.toLong(), consumed = 2)
            "اسبوع", "اسابيع" -> return Duration(weeks = num.toLong(), consumed = 2)
            "شهر", "اشهر", "شهور" -> return Duration(months = num.toLong(), consumed = 2)
        }
        return null
    }

    // ---------------- التحليل ----------------

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedTask {
        val toks = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .map { Tok(it, normalize(it).trim()) }
            .filter { it.n.isNotBlank() }
        val understood = mutableListOf<String>()
        val today = now.toLocalDate()

        var date: LocalDate? = null
        var hour: Int? = null
        var minute = 0
        var relative: LocalDateTime? = null
        var prayer: Prayer? = null
        var prayerOffset = 0
        var repeat = RepeatType.NONE
        var priority: Priority? = null
        var period: String? = null // morning/noon/afternoon/evening/night

        // 1) كلمات الطلب في البداية
        var s = 0
        while (s < toks.size && toks[s].n in startFillers) { toks[s].used = true; s++ }

        // 2) الوقت الصريح أولاً حتى نفرّق بين «بعد الظهر» كفترة و«بعد الظهر» كصلاة
        for (i in toks.indices) {
            val tk = toks[i]
            if (tk.used) continue
            val n = tk.n
            // 5:30 أو 17:00
            Regex("^(?:ال|ع|عل)?(\\d{1,2})[:.](\\d{2})$").find(n)?.let { m ->
                hour = m.groupValues[1].toInt(); minute = m.groupValues[2].toInt(); tk.used = true
                if (i > 0 && (toks[i - 1].n == "الساعه" || toks[i - 1].n == "الساعة" || toks[i - 1].n == "على" || toks[i - 1].n == "ع")) toks[i - 1].used = true
            }
            if (hour != null) break
            val isClockWord = n == "الساعه" || n == "الساعة" || n == "ساعه" && i > 0 && toks[i - 1].n == "على" || n == "عالساعه"
            val atWord = n == "على" || n == "ع"
            val glued = (n.startsWith("عال") || n.startsWith("عل")) && number(n.removePrefix("عال").removePrefix("عل")) != null
            val h: Int?
            var consumedUntil = i
            if (isClockWord || atWord) {
                h = toks.getOrNull(i + 1)?.n?.let { number(it) }?.takeIf { it in 1..24 }
                if (h != null) consumedUntil = i + 1
            } else if (glued) {
                h = number(n.removePrefix("عال").removePrefix("عل"))?.takeIf { it in 1..12 }
            } else h = null
            if (h != null) {
                // «على 5 دقايق» ليست وقتاً
                val after = toks.getOrNull(consumedUntil + 1)?.n
                if (atWord && after != null && after in setOf("دقايق", "دقيقه", "ساعات", "ايام", "يوم")) continue
                hour = h
                for (k in i..consumedUntil) toks[k].used = true
                // الدقائق: ونص / وربع / الا ربع / و10 / وعشره / و 20 دقيقه
                val m1 = toks.getOrNull(consumedUntil + 1)
                val m2 = toks.getOrNull(consumedUntil + 2)
                when {
                    m1 == null -> {}
                    m1.n == "ونص" || m1.n == "ونصف" -> { minute = 30; m1.used = true }
                    m1.n == "وربع" -> { minute = 15; m1.used = true }
                    m1.n == "وتلت" || m1.n == "وثلث" -> { minute = 20; m1.used = true }
                    (m1.n == "الا" || m1.n == "إلا") && m2 != null && (m2.n == "ربع" || m2.n == "تلت" || m2.n == "ثلث") -> {
                        minute = if (m2.n == "ربع") 45 else 40; hour = (h - 1).let { if (it == 0) 12 else it }
                        m1.used = true; m2.used = true
                    }
                    m1.n.startsWith("و") && number(m1.n.removePrefix("و")) != null -> {
                        minute = number(m1.n.removePrefix("و"))!!.coerceIn(0, 59); m1.used = true
                        if (m2 != null && m2.n in setOf("دقيقه", "دقايق", "دقائق")) m2.used = true
                    }
                    m1.n == "و" && m2 != null && number(m2.n) != null -> {
                        minute = number(m2.n)!!.coerceIn(0, 59); m1.used = true; m2.used = true
                    }
                }
                break
            }
        }

        // 3) بقية الأنماط
        var i = 0
        while (i < toks.size) {
            val tk = toks[i]
            if (tk.used) { i++; continue }
            val n = tk.n
            val next = toks.getOrNull(i + 1)

            // التكرار
            if (n == "كل" && next != null) {
                val wd = weekdayOf(next.n)
                when {
                    next.n == "يوم" && toks.getOrNull(i + 2)?.let { weekdayOf(it.n) } != null -> {
                        val d = weekdayOf(toks[i + 2].n)!!
                        repeat = RepeatType.WEEKLY; date = nextWeekday(today, d, now, hour, minute)
                        tk.used = true; next.used = true; toks[i + 2].used = true
                        understood += "كل ${weekdayLabel(d)}"; i += 3; continue
                    }
                    next.n == "يوم" || next.n == "يوما" -> { repeat = RepeatType.DAILY; understood += "يومياً" }
                    next.n == "اسبوع" || next.n == "جمعه" && wd == null -> { repeat = RepeatType.WEEKLY; understood += "أسبوعياً" }
                    next.n == "شهر" -> { repeat = RepeatType.MONTHLY; understood += "شهرياً" }
                    wd != null -> {
                        repeat = RepeatType.WEEKLY; date = nextWeekday(today, wd, now, hour, minute)
                        understood += "كل ${weekdayLabel(wd)}"
                    }
                    next.n == "صبح" || next.n == "الصبح" -> { repeat = RepeatType.DAILY; period = "morning"; understood += "يومياً" }
                    else -> { i++; continue }
                }
                tk.used = true; next.used = true; i += 2; continue
            }
            when (n) {
                "يوميا", "يومياً", "يومي" -> if (i > 0) { repeat = RepeatType.DAILY; tk.used = true; understood += "يومياً"; i++; continue }
                "اسبوعيا", "اسبوعياً" -> { repeat = RepeatType.WEEKLY; tk.used = true; understood += "أسبوعياً"; i++; continue }
                "شهريا", "شهرياً" -> { repeat = RepeatType.MONTHLY; tk.used = true; understood += "شهرياً"; i++; continue }
            }

            // «بعد بكرا»
            if ((n == "بعد") && next != null && (next.n in tomorrowWords)) {
                date = today.plusDays(2); tk.used = true; next.used = true; understood += "بعد غد"; i += 2; continue
            }

            // ربط بصلاة: (بعد|قبل|عند|وقت|مع|على) [صلاه] <صلاة> [بـ مدة]
            val relWord = when {
                n == "بعد" -> 1
                n == "قبل" -> -1
                n in setOf("عند", "وقت", "مع", "اول", "على") -> 0
                else -> null
            }
            if (relWord != null && hour == null) {
                var j = i + 1
                if (toks.getOrNull(j)?.n?.let { it == "صلاه" || it == "صلاة" || it == "اذان" || it == "الاذان" } == true) j++
                val pTok = toks.getOrNull(j)
                val p = pTok?.n?.let { prayerOf(it) }
                if (p != null) {
                    var off = when (relWord) { 1 -> 15; -1 -> -15; else -> 0 }
                    var end = j
                    if (relWord != 0) {
                        // «بنص ساعه» / «بساعه» / «ب 10 دقايق» / «بعشر دقايق»
                        val first = toks.getOrNull(j + 1)
                        if (first != null && first.n.startsWith("ب")) {
                            val d = if (first.n == "ب") readDuration(toks, j + 2, false)?.let { it.copy(consumed = it.consumed + 1) }
                            else readDuration(toks, j + 1, true)
                            if (d != null && d.minutes in 1..600) {
                                off = relWord * d.minutes.toInt(); end = j + d.consumed
                            }
                        }
                    }
                    prayer = p; prayerOffset = off
                    for (k in i..end) toks[k].used = true
                    understood += com.myday.app.util.TaskTime.anchorLabel(p, off)
                    i = end + 1; continue
                }
                // موعد نسبي: «بعد ساعتين»، «بعد نص ساعه»، «بعد 3 ايام»
                if (relWord == 1) {
                    val d = readDuration(toks, i + 1, false)
                    if (d != null) {
                        relative = now.plusMinutes(d.minutes).plusDays(d.days).plusWeeks(d.weeks).plusMonths(d.months)
                        for (k in i..(i + d.consumed)) toks[k].used = true
                        understood += "بعد " + toks.subList(i + 1, i + 1 + d.consumed).joinToString(" ") { it.raw }
                        i += d.consumed + 1; continue
                    }
                }
            }
            // «كمان ساعه» / «خلال ساعه»
            if ((n == "كمان" || n == "خلال") && next != null) {
                val d = readDuration(toks, i + 1, false)
                if (d != null) {
                    relative = now.plusMinutes(d.minutes).plusDays(d.days).plusWeeks(d.weeks).plusMonths(d.months)
                    for (k in i..(i + d.consumed)) toks[k].used = true
                    understood += "بعد " + toks.subList(i + 1, i + 1 + d.consumed).joinToString(" ") { it.raw }
                    i += d.consumed + 1; continue
                }
            }

            // التاريخ
            if (n in todayWords) { date = today; tk.used = true; understood += "اليوم"; i++; continue }
            if (n in tonightWords) { date = today; period = "night"; tk.used = true; understood += "الليلة"; i++; continue }
            if (n in tomorrowWords || bases(n).any { it in tomorrowWords }) {
                date = today.plusDays(1); tk.used = true; understood += "غداً"; i++; continue
            }
            val wd = if (n == "يوم" && next != null) weekdayOf(next.n) else weekdayOf(n)
            if (wd != null && !(n == "الاربع" && hour == null && next?.n?.let { number(it) } != null)) {
                if (n == "يوم") { tk.used = true; next!!.used = true } else tk.used = true
                // «الجاي/القادم»
                val after = toks.getOrNull(if (n == "يوم") i + 2 else i + 1)
                if (after != null && after.n in setOf("الجاي", "القادم", "الجايه", "القادمه", "الياي")) after.used = true
                date = nextWeekday(today, wd, now, hour, minute)
                understood += "يوم ${weekdayLabel(wd)}"
                i++; continue
            }
            // «يوم 15» أو «15/11»
            if (n == "يوم" && next != null) {
                val d = number(next.n)
                if (d != null && d in 1..31) {
                    var cand = runCatching { today.withDayOfMonth(d) }.getOrNull() ?: today.with(TemporalAdjusters.lastDayOfMonth())
                    if (cand.isBefore(today)) cand = runCatching { today.plusMonths(1).withDayOfMonth(d) }.getOrNull() ?: today.plusMonths(1)
                    date = cand; tk.used = true; next.used = true; understood += "يوم $d"; i += 2; continue
                }
            }
            Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$").find(n)?.let { m ->
                val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
                val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                runCatching { LocalDate.of(y ?: today.year, mo, d) }.getOrNull()?.let { cand ->
                    date = if (y == null && cand.isBefore(today)) cand.plusYears(1) else cand
                    tk.used = true; understood += "${d}/${mo}"
                }
            }
            if (tk.used) { i++; continue }

            // فترات اليوم
            when {
                n in morningWords -> { period = "morning"; tk.used = true }
                n in noonWords -> { period = "noon"; tk.used = true }
                n in afternoonWords -> { period = "afternoon"; tk.used = true }
                n in eveningWords -> { period = "evening"; tk.used = true }
                n in nightWords -> { period = "night"; tk.used = true }
                n == "بعد" && next != null && next.n in noonWords -> { period = "afternoon"; tk.used = true; next.used = true }
            }
            if (tk.used) {
                // «الساعه 5 العصر» → «العصر» فترة لا صلاة
                i++; continue
            }

            // الأولوية
            if (n in highPriorityWords) {
                // «مهم جدا»
                priority = Priority.HIGH; tk.used = true; i++; continue
            }
            i++
        }

        // 4) تفسير الساعة حسب الفترة
        var time: LocalTime? = null
        hour?.let { h0 ->
            var h = h0
            when (period) {
                "morning" -> if (h == 12) h = 0
                "noon" -> if (h in 1..4) h += 12
                "afternoon", "evening" -> if (h in 1..11) h += 12
                "night" -> if (h in 6..11) h += 12 else if (h == 12) h = 0
                else -> {
                    // دون فترة: 1–6 تعني غالباً بعد الظهر، 7–11 صباحاً، إلا إن مضى الوقت اليوم
                    if (h in 1..6) h += 12
                    else if (h in 7..11 && date == null && LocalTime.of(h, minute).isBefore(now.toLocalTime()) &&
                        LocalTime.of(h + 12, minute).isAfter(now.toLocalTime())
                    ) h += 12
                }
            }
            time = LocalTime.of(h % 24, minute.coerceIn(0, 59))
        }
        if (time == null && prayer == null && relative == null) {
            time = when (period) {
                "morning" -> LocalTime.of(8, 0)
                "noon" -> LocalTime.of(12, 30)
                "afternoon" -> LocalTime.of(16, 0)
                "evening" -> LocalTime.of(19, 0)
                "night" -> LocalTime.of(21, 0)
                else -> null
            }
        }
        time?.let { understood += "الساعة " + it.toString() }
        if (priority == Priority.HIGH) understood += "أولوية مرتفعة"

        // 5) العنوان
        val remaining = toks.filter { !it.used }.toMutableList()
        while (remaining.isNotEmpty() && remaining.first().n in leadingConnectors) remaining.removeAt(0)
        while (remaining.isNotEmpty() && remaining.last().n in trailingConnectors) remaining.removeAt(remaining.size - 1)
        var title = remaining.joinToString(" ") { it.raw }.trim()
        if (title.isEmpty()) title = "مهمة"

        val titleNorm = remaining.map { it.n }
        val isCare = titleNorm.any { w -> w in careWords || bases(w).any { it in careWords } }
        val category = categoryKeywords.firstOrNull { (_, words) ->
            titleNorm.any { w -> w in words || bases(w).any { it in words } }
        }?.first
        if (isCare) understood += "مهمة رعاية"

        return ParsedTask(
            title = title,
            date = date,
            time = time,
            relative = relative,
            prayer = prayer,
            prayerOffsetMin = prayerOffset,
            repeat = repeat,
            priority = priority,
            isCare = isCare,
            categoryName = category,
            understood = understood.distinct(),
        )
    }

    private fun nextWeekday(today: LocalDate, wd: DayOfWeek, now: LocalDateTime, hour: Int?, minute: Int): LocalDate {
        if (today.dayOfWeek == wd) {
            val laterToday = hour == null || LocalTime.of(hour.coerceIn(0, 23), minute).isAfter(now.toLocalTime())
            return if (laterToday) today else today.plusWeeks(1)
        }
        return today.with(TemporalAdjusters.next(wd))
    }

    private fun weekdayLabel(d: DayOfWeek) = when (d) {
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        DayOfWeek.MONDAY -> "الاثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
    }
}
