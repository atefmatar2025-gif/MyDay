package com.dayflow.core.intelligence

import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.text.Ar
import com.dayflow.core.time.RecurrenceCalculator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Expands recurring tasks into concrete occurrences for a given day. */
object TaskOccurrences {
    /**
     * Returns the tasks that belong to [date]: one-off tasks anchored on that date, plus a copy
     * of every recurring task that has an occurrence on that date (with start/due moved to it).
     */
    fun forDate(tasks: List<Task>, date: LocalDate): List<Task> = tasks.mapNotNull { t ->
        if (t.archived) return@mapNotNull null
        val anchor = t.startAt ?: t.dueAt ?: return@mapNotNull null
        val rule = t.recurrence
        if (rule == null) {
            if (t.anchorDate() == date) t else null
        } else if (RecurrenceCalculator.isOccurrence(rule, anchor.toLocalDate(), date)) {
            val shift = Duration.between(anchor.toLocalDate().atStartOfDay(), date.atStartOfDay())
            t.copy(startAt = t.startAt?.plus(shift), dueAt = t.dueAt?.plus(shift))
        } else {
            null
        }
    }
}

data class Conflict(val first: Task, val second: Task, val overlapMinutes: Long)

/** Finds overlapping scheduled blocks. A block is [startAt, startAt + estimate). */
object ConflictDetector {
    fun find(tasks: List<Task>): List<Conflict> {
        val blocks = tasks.filter { it.startAt != null && it.status.isOpen && !it.archived }
            .sortedBy { it.startAt }
        val result = mutableListOf<Conflict>()
        for (i in blocks.indices) {
            val a = blocks[i]
            val aEnd = a.plannedEnd!!
            for (j in i + 1 until blocks.size) {
                val b = blocks[j]
                if (!b.startAt!!.isBefore(aEnd)) break
                val end = minOf(aEnd, b.plannedEnd!!)
                val overlap = Duration.between(b.startAt, end).toMinutes()
                if (overlap > 0) result += Conflict(a, b, overlap)
            }
        }
        return result
    }
}

/** Result of "What should I do now?". */
data class Recommendation(
    val kind: Kind,
    val task: Task? = null,
    val reasons: List<String> = emptyList(),
    val freeMinutes: Long = 0,
    val headline: String,
) {
    enum class Kind { TASK, REST, PREPARE_APPOINTMENT, NOTHING, DAY_OVER }
}

/**
 * Transparent scoring used to rank open tasks. Every component is documented so the user can be
 * shown *why* a task was suggested:
 *
 *  priority:        URGENT 100, HIGH 75, MEDIUM 50, LOW 25
 *  deadline:        overdue +40, < 2h +35, < 6h +25, later today +15, tomorrow +8
 *  scheduled now:   start within ±30 min +20
 *  in progress:     +15
 *  fits free time:  +10 if the estimate fits before the next fixed commitment, otherwise -10
 *  postponed:       +3 per postponement (max +15)
 *  unblocks others: +5 per open task that depends on it
 *
 *  Excluded: closed/archived tasks, tasks blocked by an open dependency, parents that still have
 *  open subtasks (do the subtasks), fixed appointments, and tasks planned for a later day.
 */
object TaskRanker {

    data class Scored(val task: Task, val score: Int, val reasons: List<String>)

    fun rank(tasks: List<Task>, now: LocalDateTime, freeMinutes: Long): List<Scored> {
        val open = tasks.filter { it.status.isOpen && !it.archived }
        val openIds = open.map { it.id }.toSet()
        val today = now.toLocalDate()
        return open.mapNotNull { t ->
            if (t.dependsOn.any { it in openIds }) return@mapNotNull null
            if (open.any { it.parentId == t.id }) return@mapNotNull null
            if (t.fixedTime) return@mapNotNull null
            val start = t.startAt
            val due = t.dueAt
            if (start != null && start.toLocalDate().isAfter(today) && (due == null || due.toLocalDate().isAfter(today))) {
                return@mapNotNull null
            }
            val reasons = mutableListOf<String>()
            var score = t.priority.weight * 25
            if (t.priority.weight >= 3) reasons += "أولوية ${Ar.priority(t.priority)}"

            if (due != null) {
                val minutesLeft = Duration.between(now, due).toMinutes()
                when {
                    minutesLeft < 0 -> { score += 40; reasons += "متأخرة عن موعدها" }
                    minutesLeft < 120 -> { score += 35; reasons += "موعدها النهائي خلال أقل من ساعتين" }
                    minutesLeft < 360 -> { score += 25; reasons += "موعدها النهائي خلال ساعات" }
                    due.toLocalDate() == today -> { score += 15; reasons += "مستحقة اليوم" }
                    due.toLocalDate() == today.plusDays(1) -> { score += 8; reasons += "مستحقة غداً" }
                }
            }
            if (start != null && kotlin.math.abs(Duration.between(now, start).toMinutes()) <= 30) {
                score += 20; reasons += "مجدولة لهذا الوقت"
            }
            if (t.status == TaskStatus.IN_PROGRESS) { score += 15; reasons += "بدأتَ فيها بالفعل" }
            val est = t.estimatedMinutes
            if (est != null) {
                if (est <= freeMinutes) { score += 10; reasons += "مدتها (${Ar.duration(est)}) تناسب وقتك المتاح" }
                else score -= 10
            }
            if (t.postponeCount > 0) { score += minOf(15, t.postponeCount * 3); reasons += "أُجّلت ${Ar.times(t.postponeCount)}" }
            val unblocks = open.count { t.id in it.dependsOn }
            if (unblocks > 0) { score += unblocks * 5; reasons += "إنجازها يفتح الطريق لـ ${Ar.tasks(unblocks)}" }
            Scored(t, score, reasons)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.task.dueAt ?: LocalDateTime.MAX }.thenBy { it.task.id })
    }

    /** Minutes from [now] until the next fixed appointment, rest period or the end of the day. */
    fun freeMinutesFrom(now: LocalDateTime, todaysTasks: List<Task>, settings: DaySettings): Long {
        val date = now.toLocalDate()
        val dayEnd = date.atTime(settings.dayEnd)
        val boundaries = mutableListOf(dayEnd)
        todaysTasks.filter { it.fixedTime && it.status.isOpen && it.startAt != null && it.startAt.isAfter(now) }
            .forEach { boundaries += it.startAt!! }
        settings.restPeriods.map { it.start(date) }.filter { it.isAfter(now) }.forEach { boundaries += it }
        val next = boundaries.minOrNull() ?: dayEnd
        return Duration.between(now, next).toMinutes().coerceAtLeast(0)
    }

    fun whatNow(allTasks: List<Task>, now: LocalDateTime, settings: DaySettings): Recommendation {
        val date = now.toLocalDate()
        val nowTime = now.toLocalTime()
        if (nowTime.isAfter(settings.dayEnd) || nowTime.isBefore(settings.dayStart)) {
            return Recommendation(
                Recommendation.Kind.DAY_OVER,
                headline = "أنت خارج ساعات يومك المحددة (${Ar.time(settings.dayStart)} – ${Ar.time(settings.dayEnd)}). الأفضل أن ترتاح وتبدأ غداً.",
            )
        }
        settings.restPeriods.firstOrNull { nowTime >= minuteToTime(it.startMinute) && nowTime < minuteToTime(it.endMinute) }?.let {
            return Recommendation(
                Recommendation.Kind.REST,
                headline = "هذا وقت راحتك حتى ${Ar.time(minuteToTime(it.endMinute))}. خذ استراحتك، ثم عُد.",
            )
        }
        val today = TaskOccurrences.forDate(allTasks, date) + allTasks.filter { it.recurrence == null && it.anchorDate() == null }
        val upcomingFixed = TaskOccurrences.forDate(allTasks, date)
            .filter { it.fixedTime && it.status.isOpen && it.startAt != null && !it.startAt.isBefore(now) }
            .minByOrNull { it.startAt!! }
        if (upcomingFixed != null && Duration.between(now, upcomingFixed.startAt).toMinutes() <= 10) {
            return Recommendation(
                Recommendation.Kind.PREPARE_APPOINTMENT, task = upcomingFixed,
                headline = "استعد لموعد «${upcomingFixed.title}» الساعة ${Ar.time(upcomingFixed.startAt!!.toLocalTime())}.",
            )
        }
        val free = freeMinutesFrom(now, today, settings)
        // Candidates: non-recurring open tasks of any date (overdue ones included) + today's occurrences.
        val candidates = (allTasks.filter { it.recurrence == null } + TaskOccurrences.forDate(allTasks.filter { it.recurrence != null }, date))
            .distinctBy { it.id }
        val best = rank(candidates, now, free).firstOrNull()
            ?: return Recommendation(
                Recommendation.Kind.NOTHING, freeMinutes = free,
                headline = "لا توجد مهام مفتوحة مناسبة الآن. يمكنك إضافة مهمة أو أخذ قسط من الراحة.",
            )
        return Recommendation(
            Recommendation.Kind.TASK, task = best.task, reasons = best.reasons, freeMinutes = free,
            headline = "ابدأ بـ «${best.task.title}»",
        )
    }

    private fun minuteToTime(minute: Int): LocalTime =
        if (minute >= 24 * 60) LocalTime.MAX else LocalTime.of(minute / 60, minute % 60)
}
