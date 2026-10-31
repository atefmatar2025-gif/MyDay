package com.dayflow.core.stats

import com.dayflow.core.model.FocusSession
import com.dayflow.core.model.FocusType
import com.dayflow.core.model.Habit
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.Task
import java.time.DayOfWeek
import java.time.LocalDate

object HabitStats {

    /** Consecutive scheduled days completed, ending today (or yesterday if today is still pending). */
    fun currentStreak(habit: Habit, done: Set<LocalDate>, today: LocalDate): Int {
        var d = today
        if (habit.isScheduledOn(today) && today !in done) d = today.minusDays(1)
        var streak = 0
        var guard = 0
        while (!d.isBefore(habit.createdOn) && guard < 3660) {
            if (habit.isScheduledOn(d)) {
                if (d in done) streak++ else break
            }
            d = d.minusDays(1); guard++
        }
        return streak
    }

    fun bestStreak(habit: Habit, done: Set<LocalDate>, today: LocalDate): Int {
        var best = 0
        var current = 0
        var d = habit.createdOn
        while (!d.isAfter(today)) {
            if (habit.isScheduledOn(d)) {
                if (d in done) { current++; best = maxOf(best, current) } else if (d != today) current = 0
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** Fraction (0..1) of scheduled days in [from, to] that were completed. Today counts only if done. */
    fun consistency(habit: Habit, done: Set<LocalDate>, from: LocalDate, to: LocalDate, today: LocalDate): Double {
        val start = maxOf(from, habit.createdOn)
        val end = minOf(to, today)
        if (end.isBefore(start)) return 0.0
        var scheduled = 0
        var completed = 0
        var d = start
        while (!d.isAfter(end)) {
            if (habit.isScheduledOn(d)) {
                val isDone = d in done
                if (d != today || isDone) scheduled++
                if (isDone) completed++
            }
            d = d.plusDays(1)
        }
        return if (scheduled == 0) 0.0 else completed.toDouble() / scheduled
    }

    fun completionsInRange(done: Set<LocalDate>, from: LocalDate, to: LocalDate): Int =
        done.count { !it.isBefore(from) && !it.isAfter(to) }
}

data class AreaMinutes(val area: LifeArea, val minutes: Int)

object ProductivityStats {

    fun completedBetween(records: List<ProductivityRecord>, from: LocalDate, to: LocalDate): List<ProductivityRecord> =
        records.filter { it.event == HistoryEvent.COMPLETED && inRange(it.at.toLocalDate(), from, to) }

    fun countEvent(records: List<ProductivityRecord>, event: HistoryEvent, from: LocalDate, to: LocalDate): Int =
        records.count { it.event == event && inRange(it.at.toLocalDate(), from, to) }

    /** Time actually spent per life area: completed tasks (actual, else estimate) + focus sessions tied to no task. */
    fun minutesByArea(records: List<ProductivityRecord>, from: LocalDate, to: LocalDate): List<AreaMinutes> =
        completedBetween(records, from, to)
            .groupBy { it.area }
            .map { (area, list) -> AreaMinutes(area, list.sumOf { it.actualMinutes ?: it.estimatedMinutes ?: 0 }) }
            .sortedByDescending { it.minutes }

    /** Planned time per area for tasks (already expanded to occurrences) in the range. */
    fun plannedByArea(tasks: List<Task>): List<AreaMinutes> =
        tasks.filter { !it.archived && it.status != com.dayflow.core.model.TaskStatus.CANCELLED }
            .groupBy { it.area }
            .map { (area, list) -> AreaMinutes(area, list.sumOf { it.estimatedMinutes ?: 0 }) }
            .sortedByDescending { it.minutes }

    fun completedPerDay(records: List<ProductivityRecord>, from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val counts = completedBetween(records, from, to).groupingBy { it.at.toLocalDate() }.eachCount()
        val out = linkedMapOf<LocalDate, Int>()
        var d = from
        while (!d.isAfter(to)) { out[d] = counts[d] ?: 0; d = d.plusDays(1) }
        return out
    }

    /** Average completions per weekday, highest first. Only weekdays with at least one completion. */
    fun productiveWeekdays(records: List<ProductivityRecord>, from: LocalDate, to: LocalDate): List<Pair<DayOfWeek, Int>> =
        completedBetween(records, from, to).groupingBy { it.at.dayOfWeek }.eachCount()
            .toList().sortedByDescending { it.second }

    /** Tasks postponed most often, using the history (survives task edits) - (title, count). */
    fun mostPostponed(records: List<ProductivityRecord>, limit: Int = 5): List<Pair<String, Int>> =
        records.filter { it.event == HistoryEvent.POSTPONED }
            .groupBy { it.taskId ?: -it.taskTitle.hashCode().toLong() }
            .map { (_, list) -> list.last().taskTitle to list.size }
            .sortedByDescending { it.second }
            .take(limit)

    fun estimateAccuracy(records: List<ProductivityRecord>, from: LocalDate, to: LocalDate): Pair<Int, Int> {
        val done = completedBetween(records, from, to).filter { it.estimatedMinutes != null && it.actualMinutes != null }
        return done.sumOf { it.estimatedMinutes!! } to done.sumOf { it.actualMinutes!! }
    }

    fun focusMinutes(sessions: List<FocusSession>, from: LocalDate, to: LocalDate): Int =
        sessions.filter { it.type == FocusType.FOCUS && inRange(it.startedAt.toLocalDate(), from, to) }
            .sumOf { it.actualMinutes }

    fun completionRate(completed: Int, cancelled: Int, stillOpenDue: Int): Double {
        val total = completed + stillOpenDue
        return if (total == 0) 0.0 else completed.toDouble() / total
    }

    private fun inRange(d: LocalDate, from: LocalDate, to: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
}
