package com.myday.app.ui

import com.myday.app.data.isRecurring
import com.myday.app.data.CompletionEntity
import com.myday.app.data.TaskEntity
import com.myday.app.util.Fmt
import com.myday.app.util.TaskTime
import java.time.LocalDate

/** عنصر في الجدول الزمني ليوم ما (مهمة معلقة أو إنجاز مسجل). */
data class DayEntry(
    val task: TaskEntity,
    val time: Long,
    val done: Boolean,
)

data class DayStats(val total: Int, val done: Int, val pending: Int) {
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
}

object TaskQueries {

    fun isOverdue(t: TaskEntity, now: Long) = !t.isCompleted && t.dueAt < now

    /**
     * مهام يوم محدد: المعلقة المستحقة فيه + المنجزة فيه + (للأيام القادمة) تكرارات المهام المتكررة.
     */
    fun entriesForDay(
        date: LocalDate,
        tasks: List<TaskEntity>,
        completions: List<CompletionEntity>,
    ): List<DayEntry> {
        val start = Fmt.startOfDay(date)
        val end = Fmt.endOfDay(date)
        val byId = tasks.associateBy { it.id }
        val result = mutableListOf<DayEntry>()
        val doneOccurrences = HashSet<Pair<Long, Long>>()

        completions.forEach { c ->
            if (c.occurrenceDueAt in start until end) {
                val t = byId[c.taskId] ?: return@forEach
                // المهام غير المتكررة المنجزة تظهر من جدول المهام نفسه
                if (t.isRecurring) {
                    result += DayEntry(t, c.occurrenceDueAt, true)
                    doneOccurrences += t.id to c.occurrenceDueAt
                }
            }
        }
        tasks.forEach { t ->
            if (!t.isRecurring) {
                if (t.dueAt in start until end) result += DayEntry(t, t.dueAt, t.isCompleted)
            } else if (!t.isCompleted) {
                // الموعد الحالي إن كان في هذا اليوم، أو تكرار مستقبلي يقع فيه
                val occ = TaskTime.occursOnDay(t, start, end)
                if (occ != null && (t.id to occ) !in doneOccurrences) result += DayEntry(t, occ, false)
            }
        }
        return result.sortedWith(compareBy<DayEntry> { it.time }.thenByDescending { it.task.priority.level })
    }

    fun statsForDay(date: LocalDate, tasks: List<TaskEntity>, completions: List<CompletionEntity>): DayStats {
        val entries = entriesForDay(date, tasks, completions)
        val done = entries.count { it.done }
        return DayStats(entries.size, done, entries.size - done)
    }

    /** ترتيب ذكي مقترح: المتأخرة أولاً، ثم الأولوية الأعلى، ثم الأقرب موعداً. */
    fun smartSort(tasks: List<TaskEntity>, now: Long): List<TaskEntity> =
        tasks.sortedWith(
            compareBy<TaskEntity> { it.isCompleted }
                .thenByDescending { isOverdue(it, now) }
                .thenByDescending { it.priority.level }
                .thenBy { it.dueAt }
        )

    /** عدد الأيام المتتالية (حتى اليوم أو أمس) التي أُنجز فيها شيء. */
    fun streak(completions: List<CompletionEntity>): Int {
        val days = completions.map { Fmt.toDate(it.completedAt) }.toHashSet()
        var d = LocalDate.now()
        if (d !in days) d = d.minusDays(1)
        var count = 0
        while (d in days) { count++; d = d.minusDays(1) }
        return count
    }
}
