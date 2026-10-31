package com.dayflow.core.scheduling

import com.dayflow.core.intelligence.Conflict
import com.dayflow.core.intelligence.ConflictDetector
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.Priority
import com.dayflow.core.model.Task
import com.dayflow.core.text.Ar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One scheduled block in a proposed plan. A long task may be split into several parts. */
data class PlanBlock(
    val task: Task,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val part: Int = 1,
    val totalParts: Int = 1,
) {
    val minutes: Long get() = ChronoUnit.MINUTES.between(start, end)
}

/** A proposed plan. It is only a proposal: nothing is changed until the user approves it. */
data class DayPlan(
    val date: LocalDate,
    val blocks: List<PlanBlock>,
    val fixed: List<Task>,
    /** Lower-priority tasks the planner suggests moving to another day. */
    val suggestPostpone: List<Task>,
    /** Important / due tasks that do not fit - shown honestly to the user. */
    val atRisk: List<Task>,
    /** Tasks waiting for a dependency that is not part of this plan. */
    val blocked: List<Task>,
    val conflicts: List<Conflict>,
    val freeMinutes: Int,
    val requiredMinutes: Int,
) {
    val plannedMinutes: Int get() = blocks.sumOf { it.minutes }.toInt()
    val fitsEverything: Boolean get() = atRisk.isEmpty() && suggestPostpone.isEmpty() && blocked.isEmpty()

    fun summary(): String = buildString {
        append("الوقت المتاح: ${Ar.duration(freeMinutes)} — المطلوب: ${Ar.duration(requiredMinutes)}.")
        if (fitsEverything) {
            append(" الخطة تتسع لكل المهام مع الحفاظ على أوقات الراحة.")
        } else {
            append(" الوقت لا يكفي لكل شيء")
            if (suggestPostpone.isNotEmpty()) append("؛ أقترح تأجيل ${Ar.tasks(suggestPostpone.size)} أقل أولوية")
            if (atRisk.isNotEmpty()) append("؛ و${Ar.tasks(atRisk.size)} مهمة قد لا يمكن إنجازها اليوم وتحتاج قرارك")
            append(".")
        }
        if (conflicts.isNotEmpty()) append(" يوجد ${Ar.conflicts(conflicts.size)} بين مواعيد ثابتة.")
    }
}

/**
 * The scheduling engine behind "Save My Day" and "Plan tomorrow".
 *
 * Algorithm (deterministic, unit tested):
 *  1. Collect the day's open tasks: tasks (and recurring occurrences) on that date, open overdue
 *     tasks from earlier days, and undated tasks with HIGH/URGENT priority.
 *  2. Planning window: from max(now, day start) - or the day start for a future day - to day end.
 *  3. Busy time = fixed appointments (+ buffer) and the user's rest periods. Free slots = window - busy.
 *  4. Order flexible tasks: due on/before this day first, then priority, then deadline, then shorter first.
 *     Dependencies inside the plan are placed before their dependants.
 *  5. Greedy earliest-fit placement. Tasks longer than maxChunk are split into parts of at most
 *     maxChunk (and at least minChunk) minutes; shorter tasks need one contiguous slot.
 *     A buffer is kept after every block.
 *  6. Whatever does not fit is reported, never hidden: important/due tasks as "at risk",
 *     the rest as "suggest postponing".
 */
object DayPlanner {

    fun plan(allTasks: List<Task>, date: LocalDate, now: LocalDateTime, settings: DaySettings): DayPlan {
        val dayStartMin = settings.dayStart.toSecondOfDay() / 60
        val dayEndMin = (settings.dayEnd.toSecondOfDay() / 60).coerceAtLeast(dayStartMin)
        val windowStart = when {
            date.isBefore(now.toLocalDate()) -> dayEndMin // past day: nothing to plan
            date == now.toLocalDate() -> maxOf(dayStartMin, roundUp(now.hour * 60 + now.minute, 5))
            else -> dayStartMin
        }.coerceAtMost(dayEndMin)

        val todays = TaskOccurrences.forDate(allTasks, date).filter { it.status.isOpen && !it.archived }
        val todaysIds = todays.map { it.id }.toSet()
        val carried = allTasks.filter { t ->
            t.recurrence == null && t.status.isOpen && !t.archived && t.id !in todaysIds && (
                (t.anchorDate()?.isBefore(date) == true) ||
                    (t.anchorDate() == null && t.priority.weight >= Priority.HIGH.weight)
                )
        }
        val candidates = (todays + carried)
        val openIds = allTasks.filter { it.status.isOpen && !it.archived }.map { it.id }.toSet()

        val fixed = todays.filter { it.fixedTime && it.startAt != null }.sortedBy { it.startAt }
        val parentsWithOpenChildren = allTasks.filter { it.status.isOpen && it.parentId != null }.mapNotNull { it.parentId }.toSet()
        val flexibleAll = candidates.filter { !it.fixedTime && it.id !in parentsWithOpenChildren }
        val flexIds = flexibleAll.map { it.id }.toSet()
        val blocked = flexibleAll.filter { t -> t.dependsOn.any { it in openIds && it !in flexIds } }
        val flexible = flexibleAll - blocked.toSet()

        // ---- free slots (minutes from midnight) ----
        var slots = mutableListOf(windowStart to dayEndMin)
        val busy = mutableListOf<Pair<Int, Int>>()
        fixed.forEach { f ->
            val s = minuteOfDay(f.startAt!!, date)
            val e = minuteOfDay(f.plannedEnd!!, date) + settings.bufferMinutes
            busy += s to e
        }
        settings.restPeriods.forEach { busy += it.startMinute to it.endMinute }
        busy.forEach { (bs, be) -> slots = subtract(slots, bs, be) }
        slots.removeAll { it.second - it.first <= 0 }
        val freeMinutes = slots.sumOf { it.second - it.first }

        // ---- order ----
        val endOfDay = date.atTime(settings.dayEnd)
        fun mustToday(t: Task) = t.dueAt?.let { !it.isAfter(endOfDay) } ?: false
        val ordered = flexible.sortedWith(
            compareBy<Task>({ if (mustToday(it)) 0 else 1 }, { -it.priority.weight }, { it.dueAt ?: LocalDateTime.MAX }, { durationOf(it) }, { it.id }),
        )
        val byId = flexible.associateBy { it.id }
        val placementOrder = mutableListOf<Task>()
        val visiting = mutableSetOf<Long>()
        fun visit(t: Task) {
            if (t in placementOrder || t.id in visiting) return
            visiting += t.id
            t.dependsOn.mapNotNull { byId[it] }.forEach { visit(it) }
            placementOrder += t
        }
        ordered.forEach { visit(it) }

        // ---- greedy placement ----
        val blocks = mutableListOf<PlanBlock>()
        val endOf = mutableMapOf<Long, Int>()
        val unplaced = mutableListOf<Task>()
        for (t in placementOrder) {
            val earliest = t.dependsOn.mapNotNull { endOf[it] }.maxOrNull() ?: 0
            if (t.dependsOn.any { it in byId && it !in endOf }) { unplaced += t; continue }
            val total = durationOf(t)
            val splittable = total > settings.maxChunkMinutes
            val parts = mutableListOf<Pair<Int, Int>>()
            var remaining = total
            val working = slots.toMutableList()
            var idx = 0
            while (remaining > 0 && idx < working.size) {
                val (s0, e) = working[idx]
                val s = maxOf(s0, earliest)
                val usable = e - s
                if (usable <= 0) { idx++; continue }
                val chunk = when {
                    !splittable -> if (usable >= remaining) remaining else 0
                    else -> {
                        val want = minOf(remaining, settings.maxChunkMinutes)
                        when {
                            usable >= want -> want
                            usable >= settings.minChunkMinutes && remaining - usable >= settings.minChunkMinutes -> usable
                            usable >= remaining -> remaining
                            else -> 0
                        }
                    }
                }
                if (chunk <= 0) { idx++; continue }
                parts += s to s + chunk
                remaining -= chunk
                val consumedEnd = minOf(e, s + chunk + settings.bufferMinutes)
                // replace slot with what is left of it (the part before s stays usable for others)
                val replacement = mutableListOf<Pair<Int, Int>>()
                if (s > s0) replacement += s0 to s
                if (consumedEnd < e) replacement += consumedEnd to e
                working.removeAt(idx)
                working.addAll(idx, replacement)
                idx += if (s > s0) 1 else 0
            }
            if (remaining == 0 && parts.isNotEmpty()) {
                slots = working
                parts.forEachIndexed { i, (s, e) ->
                    blocks += PlanBlock(t, toDateTime(date, s), toDateTime(date, e), i + 1, parts.size)
                }
                endOf[t.id] = parts.maxOf { it.second }
            } else {
                unplaced += t
            }
        }

        val atRisk = unplaced.filter { mustToday(it) || it.priority.weight >= Priority.HIGH.weight }
        val postpone = unplaced - atRisk.toSet()
        val required = flexible.sumOf { durationOf(it) }

        return DayPlan(
            date = date,
            blocks = blocks.sortedBy { it.start },
            fixed = fixed,
            suggestPostpone = postpone,
            atRisk = atRisk,
            blocked = blocked,
            conflicts = ConflictDetector.find(fixed),
            freeMinutes = freeMinutes,
            requiredMinutes = required,
        )
    }

    fun durationOf(t: Task): Int {
        val estimate = t.estimatedMinutes ?: Task.DEFAULT_BLOCK_MINUTES
        val spent = if (t.status == com.dayflow.core.model.TaskStatus.IN_PROGRESS) (t.actualMinutes ?: 0) else 0
        return (estimate - spent).coerceAtLeast(10)
    }

    private fun roundUp(value: Int, step: Int) = ((value + step - 1) / step) * step

    private fun minuteOfDay(dt: LocalDateTime, date: LocalDate): Int {
        val minutes = ChronoUnit.MINUTES.between(date.atStartOfDay(), dt)
        return minutes.coerceIn(0, 24L * 60).toInt()
    }

    private fun toDateTime(date: LocalDate, minute: Int): LocalDateTime = date.atStartOfDay().plusMinutes(minute.toLong())

    /** Removes [bs, be) from every slot. */
    internal fun subtract(slots: List<Pair<Int, Int>>, bs: Int, be: Int): MutableList<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for ((s, e) in slots) {
            if (be <= s || bs >= e) { out += s to e; continue }
            if (bs > s) out += s to bs
            if (be < e) out += be to e
        }
        return out
    }
}
