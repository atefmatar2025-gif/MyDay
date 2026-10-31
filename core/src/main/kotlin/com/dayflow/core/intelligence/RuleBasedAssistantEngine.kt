package com.dayflow.core.intelligence

import com.dayflow.core.model.FocusType
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.Task
import com.dayflow.core.scheduling.DayPlan
import com.dayflow.core.scheduling.DayPlanner
import com.dayflow.core.stats.HabitStats
import com.dayflow.core.stats.ProductivityStats
import com.dayflow.core.text.Ar
import com.dayflow.core.time.LocalTimes
import java.time.Duration
import java.time.LocalDate

/**
 * DAYFLOW Intelligence Engine, phase 1: a deterministic rule-based engine.
 * It is NOT a language model; it understands a defined set of Arabic questions and answers only
 * from the user's local data in [AssistantContext].
 */
class RuleBasedAssistantEngine : AssistantEngine {

    override val displayName = "محرك DAYFLOW المحلي (قائم على القواعد)"
    override val isLanguageModel = false

    override fun answer(query: String, context: AssistantContext): AssistantReply {
        val intent = IntentClassifier.classify(query)
        return when (intent) {
            AssistantIntent.WHAT_NOW -> whatNow(context)
            AssistantIntent.TOP_TASKS -> topTasks(context)
            AssistantIntent.REMAINING -> remaining(context)
            AssistantIntent.OVERDUE -> overdue(context)
            AssistantIntent.PLAN_TOMORROW -> planReply(context, context.now.toLocalDate().plusDays(1), intent)
            AssistantIntent.PLAN_TODAY -> planReply(context, context.now.toLocalDate(), intent)
            AssistantIntent.HOURS_BY_AREA -> hoursByArea(query, context)
            AssistantIntent.MOST_POSTPONED -> mostPostponed(context)
            AssistantIntent.CONFLICTS -> conflicts(context)
            AssistantIntent.HOW_TO_FINISH -> howToFinish(context)
            AssistantIntent.WEEK_REPORT -> periodReport(context, monthly = false)
            AssistantIntent.MONTH_REPORT -> periodReport(context, monthly = true)
            AssistantIntent.TODAY_DONE -> todayDone(context)
            AssistantIntent.HABITS -> habits(context)
            AssistantIntent.FREE_TIME -> freeTime(context)
            AssistantIntent.HELP, AssistantIntent.UNKNOWN -> help(intent)
        }
    }

    override fun dailyInsight(context: AssistantContext): String? {
        val today = context.now.toLocalDate()
        val overdue = openOverdue(context)
        if (overdue.isNotEmpty()) return "لديك ${Ar.tasks(overdue.size)} متأخرة. جرّب «أنقذ يومي» لإعادة ترتيب ما تبقى."
        val todays = todaysOpen(context)
        val conflicts = ConflictDetector.find(TaskOccurrences.forDate(context.tasks, today))
        if (conflicts.isNotEmpty()) return "يوجد تعارض بين «${conflicts[0].first.title}» و«${conflicts[0].second.title}» اليوم."
        val plan = DayPlanner.plan(context.tasks, today, context.now, context.settings)
        if (!plan.fitsEverything && todays.isNotEmpty()) {
            return "المهام المتبقية تحتاج ${Ar.duration(plan.requiredMinutes)} بينما المتاح ${Ar.duration(plan.freeMinutes)}. راجع الخطة المقترحة."
        }
        val postponed = context.tasks.filter { it.status.isOpen && it.postponeCount >= 3 }.maxByOrNull { it.postponeCount }
        if (postponed != null) return "أجّلتَ «${postponed.title}» ${Ar.times(postponed.postponeCount)}. ربما تقسيمها إلى خطوات أصغر يساعد."
        if (todays.isEmpty()) return null
        return "أمامك اليوم ${Ar.tasks(todays.size)} مفتوحة، ووقتك المتاح يكفيها."
    }

    // ---------------------------------------------------------------- handlers

    private fun whatNow(c: AssistantContext): AssistantReply {
        val rec = TaskRanker.whatNow(c.tasks, c.now, c.settings)
        val actions = rec.task?.takeIf { rec.kind == Recommendation.Kind.TASK }
            ?.let { listOf(AssistantAction.StartTask(it.id, "ابدأ الآن")) } ?: emptyList()
        val bullets = rec.reasons.toMutableList()
        if (rec.kind == Recommendation.Kind.TASK && rec.freeMinutes > 0) {
            bullets += "وقتك المتاح قبل الالتزام التالي: ${Ar.duration(rec.freeMinutes.toInt())}"
        }
        return AssistantReply(AssistantIntent.WHAT_NOW, rec.headline, bullets, actions)
    }

    private fun topTasks(c: AssistantContext): AssistantReply {
        val free = TaskRanker.freeMinutesFrom(c.now, TaskOccurrences.forDate(c.tasks, c.now.toLocalDate()), c.settings)
        val ranked = TaskRanker.rank(candidates(c), c.now, free).take(3)
        if (ranked.isEmpty()) return AssistantReply(AssistantIntent.TOP_TASKS, "لا توجد مهام مفتوحة لليوم.")
        return AssistantReply(
            AssistantIntent.TOP_TASKS,
            "أهم ${if (ranked.size == 3) "ثلاث مهام" else Ar.tasks(ranked.size)} الآن:",
            ranked.mapIndexed { i, s -> "${i + 1}. ${s.task.title}" + (s.reasons.firstOrNull()?.let { " — $it" } ?: "") },
            ranked.map { AssistantAction.OpenTask(it.task.id, it.task.title) },
        )
    }

    private fun remaining(c: AssistantContext): AssistantReply {
        val today = c.now.toLocalDate()
        val todays = todaysOpen(c)
        val overdue = openOverdue(c).filter { it.id !in todays.map { t -> t.id } }
        val doneToday = ProductivityStats.countEvent(c.history, HistoryEvent.COMPLETED, today, today)
        val allOpen = c.tasks.count { it.status.isOpen && !it.archived }
        val text = "تبقّى لديك اليوم ${Ar.tasks(todays.size)}" +
            (if (overdue.isNotEmpty()) " إضافة إلى ${Ar.tasks(overdue.size)} متأخرة" else "") + "."
        return AssistantReply(
            AssistantIntent.REMAINING, text,
            listOf("أنجزتَ اليوم: ${Ar.tasks(doneToday)}", "كل المهام المفتوحة: ${Ar.tasks(allOpen)}"),
        )
    }

    private fun overdue(c: AssistantContext): AssistantReply {
        val list = openOverdue(c).sortedBy { it.dueAt ?: it.startAt }
        if (list.isEmpty()) return AssistantReply(AssistantIntent.OVERDUE, "لا توجد مهام متأخرة. أحسنت!")
        return AssistantReply(
            AssistantIntent.OVERDUE,
            "لديك ${Ar.tasks(list.size)} متأخرة:",
            list.take(10).map { t ->
                val due = t.dueAt ?: t.plannedEnd
                val late = due?.let { Duration.between(it, c.now).toMinutes().toInt() } ?: 0
                "${t.title} — متأخرة ${if (late >= 1440) "${late / 1440} يوم" else Ar.duration(late)}"
            },
            list.take(3).map { AssistantAction.OpenTask(it.id, it.title) } +
                AssistantAction.ReviewPlan(c.now.toLocalDate(), "أنقذ يومي"),
        )
    }

    private fun planReply(c: AssistantContext, date: LocalDate, intent: AssistantIntent): AssistantReply {
        val plan = DayPlanner.plan(c.tasks, date, c.now, c.settings)
        val label = if (date == c.now.toLocalDate()) "اليوم" else "الغد"
        if (plan.blocks.isEmpty() && plan.fixed.isEmpty() && plan.atRisk.isEmpty()) {
            return AssistantReply(intent, "لا توجد مهام لتنظيمها في $label.")
        }
        return AssistantReply(
            intent,
            "خطة مقترحة لـ$label (لن يتغير شيء قبل موافقتك). ${plan.summary()}",
            planBullets(plan),
            listOf(AssistantAction.ReviewPlan(date, "مراجعة الخطة واعتمادها")),
        )
    }

    private fun hoursByArea(query: String, c: AssistantContext): AssistantReply {
        val area = IntentClassifier.areaOf(query) ?: LifeArea.STUDY
        val today = c.now.toLocalDate()
        val (from, to, label) = when (IntentClassifier.periodOf(query)) {
            IntentClassifier.Period.TODAY -> Triple(today, today, "اليوم")
            IntentClassifier.Period.MONTH -> Triple(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), "هذا الشهر")
            IntentClassifier.Period.WEEK -> {
                val s = LocalTimes.startOfWeek(today, c.weekStart); Triple(s, s.plusDays(6), "هذا الأسبوع")
            }
        }
        var planned = 0
        var d = from
        while (!d.isAfter(to)) {
            planned += TaskOccurrences.forDate(c.tasks, d).filter { it.area == area && it.status != com.dayflow.core.model.TaskStatus.CANCELLED }
                .sumOf { it.estimatedMinutes ?: 0 }
            d = d.plusDays(1)
        }
        val done = ProductivityStats.minutesByArea(c.history, from, to).firstOrNull { it.area == area }?.minutes ?: 0
        val areaTaskIds = c.tasks.filter { it.area == area }.map { it.id }.toSet()
        val focus = c.focusSessions.filter {
            it.type == FocusType.FOCUS && it.taskId in areaTaskIds &&
                !it.startedAt.toLocalDate().isBefore(from) && !it.startedAt.toLocalDate().isAfter(to)
        }.sumOf { it.actualMinutes }
        return AssistantReply(
            AssistantIntent.HOURS_BY_AREA,
            "${Ar.area(area)} $label:",
            listOf(
                "الوقت المخطط في المهام: ${Ar.duration(planned)}",
                "وقت المهام المكتملة: ${Ar.duration(done)}",
                "جلسات تركيز على مهام ${Ar.area(area)}: ${Ar.duration(focus)}",
            ),
        )
    }

    private fun mostPostponed(c: AssistantContext): AssistantReply {
        val fromHistory = ProductivityStats.mostPostponed(c.history, 10).filter { it.second > 1 }
        val list = fromHistory.ifEmpty {
            c.tasks.filter { it.postponeCount > 1 }.sortedByDescending { it.postponeCount }.map { it.title to it.postponeCount }
        }
        if (list.isEmpty()) return AssistantReply(AssistantIntent.MOST_POSTPONED, "لا توجد مهام أُجّلت أكثر من مرة.")
        return AssistantReply(
            AssistantIntent.MOST_POSTPONED,
            "المهام التي أجّلتها أكثر من مرة:",
            list.map { (title, n) -> "$title — ${Ar.times(n)}" } + "نصيحة: قسّم المهمة المؤجلة كثيراً إلى خطوة أولى صغيرة لا تتجاوز 25 دقيقة.",
        )
    }

    private fun conflicts(c: AssistantContext): AssistantReply {
        val today = c.now.toLocalDate()
        val found = (0L..6L).flatMap { offset ->
            val d = today.plusDays(offset)
            ConflictDetector.find(TaskOccurrences.forDate(c.tasks, d)).map { d to it }
        }.filter { (_, conf) -> conf.second.startAt!!.isAfter(c.now.minusHours(1)) }
        if (found.isEmpty()) return AssistantReply(AssistantIntent.CONFLICTS, "لا يوجد تعارض بين مواعيدك خلال الأيام السبعة القادمة.")
        return AssistantReply(
            AssistantIntent.CONFLICTS,
            "وجدتُ ${Ar.conflicts(found.size)}:",
            found.map { (d, conf) ->
                "${Ar.shortDate(d)}: «${conf.first.title}» (${Ar.time(conf.first.startAt!!.toLocalTime())}) يتداخل مع «${conf.second.title}» (${Ar.time(conf.second.startAt!!.toLocalTime())}) لمدة ${Ar.duration(conf.overlapMinutes.toInt())}"
            },
            found.map { it.second.second }.distinctBy { it.id }.take(2).map { AssistantAction.OpenTask(it.id, "تعديل «${it.title}»") },
        )
    }

    private fun howToFinish(c: AssistantContext): AssistantReply {
        val today = c.now.toLocalDate()
        val plan = DayPlanner.plan(c.tasks, today, c.now, c.settings)
        if (plan.blocks.isEmpty() && plan.atRisk.isEmpty() && plan.suggestPostpone.isEmpty()) {
            return AssistantReply(AssistantIntent.HOW_TO_FINISH, "لا توجد مهام متبقية لليوم.")
        }
        val tips = mutableListOf<String>()
        if (plan.fitsEverything) {
            tips += "وقتك المتاح (${Ar.duration(plan.freeMinutes)}) يكفي ما تبقى (${Ar.duration(plan.requiredMinutes)}). اتبع هذا الترتيب:"
        } else {
            tips += "بصراحة: ما تبقى يحتاج ${Ar.duration(plan.requiredMinutes)} والمتاح ${Ar.duration(plan.freeMinutes)} فقط، فلن يتسع اليوم لكل شيء."
            if (plan.suggestPostpone.isNotEmpty()) tips += "أجّل: " + plan.suggestPostpone.joinToString("، ") { it.title }
            if (plan.atRisk.isNotEmpty()) tips += "تحتاج قرارك (مهمة/موعد نهائي لا يتسع): " + plan.atRisk.joinToString("، ") { it.title }
        }
        tips += plan.blocks.take(6).map { "${Ar.time(it.start.toLocalTime())} – ${Ar.time(it.end.toLocalTime())}: ${it.task.title}" + partLabel(it.part, it.totalParts) }
        val big = c.tasks.filter { it.status.isOpen && (it.estimatedMinutes ?: 0) > c.settings.maxChunkMinutes }
        if (big.isNotEmpty()) tips += "قسّم المهام الطويلة (${big.first().title}) إلى جلسات تركيز."
        return AssistantReply(
            AssistantIntent.HOW_TO_FINISH, "هكذا يمكنك إنهاء مهامك المتبقية:", tips,
            listOf(AssistantAction.ReviewPlan(today, "أنقذ يومي")),
        )
    }

    private fun periodReport(c: AssistantContext, monthly: Boolean): AssistantReply {
        val today = c.now.toLocalDate()
        val from = if (monthly) today.withDayOfMonth(1) else LocalTimes.startOfWeek(today, c.weekStart)
        val to = if (monthly) today.withDayOfMonth(today.lengthOfMonth()) else from.plusDays(6)
        val prevFrom = if (monthly) from.minusMonths(1) else from.minusDays(7)
        val prevTo = if (monthly) from.minusDays(1) else from.minusDays(1)
        val completed = ProductivityStats.countEvent(c.history, HistoryEvent.COMPLETED, from, to)
        val prevCompleted = ProductivityStats.countEvent(c.history, HistoryEvent.COMPLETED, prevFrom, prevTo)
        val postponed = ProductivityStats.countEvent(c.history, HistoryEvent.POSTPONED, from, to)
        val cancelled = ProductivityStats.countEvent(c.history, HistoryEvent.CANCELLED, from, to)
        val focus = ProductivityStats.focusMinutes(c.focusSessions, from, to)
        val areas = ProductivityStats.minutesByArea(c.history, from, to).filter { it.minutes > 0 }
        val bestDay = ProductivityStats.productiveWeekdays(c.history, from, to).firstOrNull()
        val habitRates = c.habits.filter { !it.archived }.map { h ->
            HabitStats.consistency(h, c.habitLogs.filter { it.habitId == h.id }.map { it.date }.toSet(), from, to, today)
        }
        val label = if (monthly) "الشهر" else "الأسبوع"
        val bullets = mutableListOf(
            "مهام مكتملة: $completed" + delta(completed, prevCompleted, if (monthly) "الشهر الماضي" else "الأسبوع الماضي"),
            "مهام مؤجلة: $postponed — ملغاة: $cancelled",
            "وقت التركيز: ${Ar.duration(focus)}",
        )
        if (areas.isNotEmpty()) bullets += "أكثر جانب نال وقتك: ${Ar.area(areas.first().area)} (${Ar.duration(areas.first().minutes)})"
        if (bestDay != null) bullets += "أكثر أيامك إنجازاً: ${Ar.dayName(bestDay.first)}"
        if (habitRates.isNotEmpty()) bullets += "متوسط الالتزام بالعادات: ${(habitRates.average() * 100).toInt()}%"
        if (completed == 0 && postponed == 0 && focus == 0) {
            return AssistantReply(AssistantIntent.WEEK_REPORT, "لا توجد بيانات إنجاز مسجلة لهذا $label بعد.")
        }
        return AssistantReply(
            if (monthly) AssistantIntent.MONTH_REPORT else AssistantIntent.WEEK_REPORT,
            "تقرير إنجاز $label (${Ar.shortDate(from)} – ${Ar.shortDate(to)}):", bullets,
        )
    }

    private fun todayDone(c: AssistantContext): AssistantReply {
        val today = c.now.toLocalDate()
        val done = ProductivityStats.completedBetween(c.history, today, today)
        val focus = ProductivityStats.focusMinutes(c.focusSessions, today, today)
        val habitsDone = c.habitLogs.count { it.date == today }
        if (done.isEmpty() && focus == 0 && habitsDone == 0) {
            return AssistantReply(AssistantIntent.TODAY_DONE, "لم تُسجَّل إنجازات اليوم بعد. البداية بمهمة صغيرة تصنع الفرق.")
        }
        return AssistantReply(
            AssistantIntent.TODAY_DONE,
            "أنجزتَ اليوم ${Ar.tasks(done.size)}، وركّزتَ ${Ar.duration(focus)}، وسجّلتَ $habitsDone عادة.",
            done.map { "✓ ${it.taskTitle}" },
        )
    }

    private fun habits(c: AssistantContext): AssistantReply {
        val today = c.now.toLocalDate()
        val due = c.habits.filter { !it.archived && it.isScheduledOn(today) }
        if (due.isEmpty()) return AssistantReply(AssistantIntent.HABITS, "لا توجد عادات مجدولة لليوم.")
        val bullets = due.map { h ->
            val dates = c.habitLogs.filter { it.habitId == h.id }.map { it.date }.toSet()
            val streak = HabitStats.currentStreak(h, dates, today)
            "${if (today in dates) "✓" else "○"} ${h.name} — سلسلة $streak يوم"
        }
        val done = due.count { h -> c.habitLogs.any { it.habitId == h.id && it.date == today } }
        return AssistantReply(AssistantIntent.HABITS, "عاداتك اليوم: أنجزتَ $done من ${due.size}.", bullets)
    }

    private fun freeTime(c: AssistantContext): AssistantReply {
        val plan = DayPlanner.plan(c.tasks, c.now.toLocalDate(), c.now, c.settings)
        val remainingFree = (plan.freeMinutes - plan.plannedMinutes).coerceAtLeast(0)
        return AssistantReply(
            AssistantIntent.FREE_TIME,
            "بقي من يومك ${Ar.duration(plan.freeMinutes)} خارج المواعيد الثابتة وأوقات الراحة.",
            listOf(
                "تحتاج مهامك المفتوحة لليوم: ${Ar.duration(plan.requiredMinutes)}",
                "الوقت الحر بعد المهام: ${Ar.duration(remainingFree)}",
            ),
        )
    }

    private fun help(intent: AssistantIntent) = AssistantReply(
        intent,
        if (intent == AssistantIntent.UNKNOWN) "لم أفهم السؤال. أنا محرك محلي قائم على القواعد ولست نموذجاً لغوياً، وأفهم أسئلة مثل:"
        else "أنا محرك DAYFLOW المحلي، أعمل دون إنترنت على بياناتك فقط. يمكنك سؤالي:",
        SUGGESTED_QUESTIONS,
    )

    // ---------------------------------------------------------------- helpers

    private fun candidates(c: AssistantContext): List<Task> =
        (c.tasks.filter { it.recurrence == null } + TaskOccurrences.forDate(c.tasks.filter { it.recurrence != null }, c.now.toLocalDate()))

    private fun todaysOpen(c: AssistantContext): List<Task> =
        TaskOccurrences.forDate(c.tasks, c.now.toLocalDate()).filter { it.status.isOpen }

    private fun openOverdue(c: AssistantContext): List<Task> =
        candidates(c).filter { it.isOverdue(c.now) && !it.archived }

    private fun planBullets(plan: DayPlan): List<String> {
        val out = mutableListOf<String>()
        val items = (plan.fixed.map { it.startAt!! to "📌 ${Ar.time(it.startAt.toLocalTime())}: ${it.title} (موعد ثابت)" } +
            plan.blocks.map { it.start to "${Ar.time(it.start.toLocalTime())} – ${Ar.time(it.end.toLocalTime())}: ${it.task.title}${partLabel(it.part, it.totalParts)}" })
            .sortedBy { it.first }
        out += items.map { it.second }
        if (plan.suggestPostpone.isNotEmpty()) out += "مقترح تأجيلها: " + plan.suggestPostpone.joinToString("، ") { it.title }
        if (plan.atRisk.isNotEmpty()) out += "قد لا يمكن إنجازها: " + plan.atRisk.joinToString("، ") { it.title }
        if (plan.blocked.isNotEmpty()) out += "بانتظار مهام أخرى: " + plan.blocked.joinToString("، ") { it.title }
        return out
    }

    private fun partLabel(part: Int, total: Int) = if (total > 1) " (جزء $part من $total)" else ""

    private fun delta(now: Int, before: Int, label: String): String = when {
        before == 0 && now == 0 -> ""
        now > before -> " (↑ ${now - before} عن $label)"
        now < before -> " (↓ ${before - now} عن $label)"
        else -> " (مثل $label)"
    }

    companion object {
        val SUGGESTED_QUESTIONS = listOf(
            "ماذا يجب أن أفعل الآن؟",
            "ما أهم ثلاث مهام اليوم؟",
            "كم بقي لدي من المهام؟",
            "ما المهام التي تأخرت فيها؟",
            "نظّم لي يوم الغد",
            "كم ساعة خصصت للدراسة هذا الأسبوع؟",
            "ما المهام التي أجلتها أكثر من مرة؟",
            "هل لدي تعارض بين المواعيد؟",
            "كيف أستطيع إنهاء مهامي المتبقية؟",
            "أعطني تقرير إنجاز الأسبوع",
        )
    }
}
