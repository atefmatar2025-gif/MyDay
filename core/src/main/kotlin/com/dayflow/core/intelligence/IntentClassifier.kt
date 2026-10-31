package com.dayflow.core.intelligence

import com.dayflow.core.model.LifeArea
import com.dayflow.core.text.ArabicText

/** Keyword-based Arabic intent classification. Order of the checks matters and is tested. */
object IntentClassifier {

    private val areaStems = listOf(
        "دراس" to LifeArea.STUDY, "مذاكر" to LifeArea.STUDY, "ادرس" to LifeArea.STUDY,
        "عمل" to LifeArea.WORK, "شغل" to LifeArea.WORK, "وظيف" to LifeArea.WORK,
        "عباد" to LifeArea.WORSHIP, "صلا" to LifeArea.WORSHIP, "قران" to LifeArea.WORSHIP,
        "اسر" to LifeArea.FAMILY, "عائل" to LifeArea.FAMILY, "اهل" to LifeArea.FAMILY,
        "صح" to LifeArea.HEALTH, "رياض" to LifeArea.HEALTH,
        "راح" to LifeArea.REST,
        "تطوير" to LifeArea.PERSONAL_GROWTH, "قراء" to LifeArea.PERSONAL_GROWTH, "تعلم" to LifeArea.PERSONAL_GROWTH,
        "اجتماع" to LifeArea.SOCIAL,
        "موعد" to LifeArea.APPOINTMENT, "مواعيد" to LifeArea.APPOINTMENT,
    )

    fun classify(query: String): AssistantIntent {
        val t = ArabicText.normalize(query)
        val words = t.split(' ').toSet()
        fun has(vararg keys: String) = keys.any { k -> if (k.contains(' ')) t.contains(k) else words.any { it == k || it.startsWith(k) } }

        return when {
            has("تعارض", "تتعارض", "تداخل", "متداخل", "يتعارض") -> AssistantIntent.CONFLICTS
            has("اجلت", "اجلتها", "تاجيل", "المؤجله", "مؤجله", "اجلتها") -> AssistantIntent.MOST_POSTPONED
            has("كيف") && has("انهي", "انهاء", "انجز", "انجاز", "اخلص", "انتهي", "اكمل") -> AssistantIntent.HOW_TO_FINISH
            has("تقرير", "ملخص") && has("الشهر", "شهر", "شهري") -> AssistantIntent.MONTH_REPORT
            has("تقرير", "ملخص") || t.contains("انجاز الاسبوع") -> AssistantIntent.WEEK_REPORT
            has("كم") && has("ساعه", "ساعات", "وقت", "دقيقه") && areaOf(query) != null -> AssistantIntent.HOURS_BY_AREA
            has("انقذ") || ((has("نظم", "رتب", "خطط", "نظملي", "رتبلي") && has("اليوم", "يومي"))) -> AssistantIntent.PLAN_TODAY
            has("نظم", "رتب", "خطط", "نظملي", "رتبلي") && has("الغد", "غدا", "غد", "بكره", "بكرا", "بكرة") -> AssistantIntent.PLAN_TOMORROW
            has("نظم", "رتب", "خطط") -> AssistantIntent.PLAN_TODAY
            has("تاخرت", "متاخر", "المتاخر", "فاتني", "فاتت", "تجاوزت") -> AssistantIntent.OVERDUE
            has("اهم") -> AssistantIntent.TOP_TASKS
            (has("ماذا", "شو", "ما") && has("انجزت", "انهيت", "اكملت")) -> AssistantIntent.TODAY_DONE
            has("كم") && has("بقي", "تبقي", "متبقي", "باقي", "المتبقيه", "تبقى") -> AssistantIntent.REMAINING
            has("كم") && has("مهمه", "مهام") -> AssistantIntent.REMAINING
            has("عادات", "عاداتي", "العادات", "عادتي") -> AssistantIntent.HABITS
            has("فراغ", "متاح", "المتاح") -> AssistantIntent.FREE_TIME
            has("ماذا", "شو", "بماذا", "ايش", "وش", "ما") && has("افعل", "اعمل", "ابدا", "اسوي", "الان", "هلا") -> AssistantIntent.WHAT_NOW
            has("مساعده", "تستطيع", "يمكنك", "تقدر", "اوامر") -> AssistantIntent.HELP
            else -> AssistantIntent.UNKNOWN
        }
    }

    fun areaOf(query: String): LifeArea? {
        val words = ArabicText.tokens(query)
        for (w in words) {
            val stripped = w.removePrefix("لل").removePrefix("ال").removePrefix("ل").removePrefix("ب")
            for ((stem, area) in areaStems) {
                if (stripped.startsWith(stem) || w.startsWith("ال$stem") || w.startsWith("لل$stem")) {
                    // "عمل" must not match "عملت"/"اعمل" style verbs used in other questions.
                    if (stem == "عمل" && stripped != "عمل") continue
                    if (stem == "صح" && !stripped.startsWith("صحه")) continue
                    if (stem == "راح" && !stripped.startsWith("راحه")) continue
                    if (stem == "اهل" && stripped != "اهل" && stripped != "اهلي") continue
                    return area
                }
            }
        }
        return null
    }

    enum class Period { TODAY, WEEK, MONTH }

    fun periodOf(query: String): Period {
        val t = ArabicText.normalize(query)
        return when {
            t.contains("الشهر") || t.contains("شهر") -> Period.MONTH
            t.contains("اليوم") -> Period.TODAY
            else -> Period.WEEK
        }
    }
}
