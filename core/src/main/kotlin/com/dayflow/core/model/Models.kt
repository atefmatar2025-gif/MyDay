package com.dayflow.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Task priority. [weight] is used by the ranking and planning algorithms. */
enum class Priority(val weight: Int) {
    URGENT(4), HIGH(3), MEDIUM(2), LOW(1);

    companion object {
        fun fromName(value: String?): Priority = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** Stored task status. OVERDUE is normally derived at read time, see [Task.effectiveStatus]. */
enum class TaskStatus {
    NEW, SCHEDULED, IN_PROGRESS, COMPLETED, POSTPONED, OVERDUE, CANCELLED;

    val isOpen: Boolean get() = this != COMPLETED && this != CANCELLED

    companion object {
        fun fromName(value: String?): TaskStatus = entries.firstOrNull { it.name == value } ?: NEW
    }
}

/** The life areas used by Life Balance, the timeline colours and the assistant. */
enum class LifeArea {
    WORK, STUDY, WORSHIP, FAMILY, HEALTH, REST, PERSONAL_GROWTH, SOCIAL, APPOINTMENT, PERSONAL;

    companion object {
        fun fromName(value: String?): LifeArea = entries.firstOrNull { it.name == value } ?: PERSONAL
    }
}

/** A half-open time range [start, end) within one day, in minutes from midnight. */
data class MinuteRange(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0..(24 * 60)) { "start out of range" }
        require(endMinute in 0..(24 * 60)) { "end out of range" }
    }
    val length: Int get() = (endMinute - startMinute).coerceAtLeast(0)
    fun start(date: LocalDate): LocalDateTime = date.atStartOfDay().plusMinutes(startMinute.toLong())
    fun end(date: LocalDate): LocalDateTime = date.atStartOfDay().plusMinutes(endMinute.toLong())
}

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.NEW,
    val categoryId: Long? = null,
    val area: LifeArea = LifeArea.PERSONAL,
    val tags: List<String> = emptyList(),
    /** Planned start (wall-clock time in the user's current zone). */
    val startAt: LocalDateTime? = null,
    /** Deadline (wall-clock). */
    val dueAt: LocalDateTime? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val parentId: Long? = null,
    val dependsOn: List<Long> = emptyList(),
    /** A fixed appointment cannot be moved by the planner. */
    val fixedTime: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    val postponeCount: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val completedAt: Instant? = null,
) {
    /** Effective end of the scheduled block, if this task has a start. */
    val plannedEnd: LocalDateTime?
        get() = startAt?.plusMinutes((estimatedMinutes ?: DEFAULT_BLOCK_MINUTES).toLong())

    /** Status shown to the user: an open task whose deadline passed is OVERDUE. */
    fun effectiveStatus(now: LocalDateTime): TaskStatus {
        if (!status.isOpen) return status
        val deadline = dueAt ?: if (fixedTime) plannedEnd else null
        return if (deadline != null && deadline.isBefore(now)) TaskStatus.OVERDUE else status
    }

    fun isOverdue(now: LocalDateTime) = effectiveStatus(now) == TaskStatus.OVERDUE

    /** The day the task "belongs" to for daily views. */
    fun anchorDate(): LocalDate? = (startAt ?: dueAt)?.toLocalDate()

    companion object {
        const val DEFAULT_BLOCK_MINUTES = 30
    }
}

data class Habit(
    val id: Long = 0,
    val name: String,
    val icon: String = "✓",
    /** Preferred time of day, null = any time. */
    val time: LocalTime? = null,
    /** Days on which the habit is scheduled. Empty = every day. */
    val days: Set<DayOfWeek> = emptySet(),
    /** Optional weekly target (number of completions per week). */
    val weeklyTarget: Int? = null,
    val reminderEnabled: Boolean = false,
    val archived: Boolean = false,
    val createdOn: LocalDate = LocalDate.EPOCH,
) {
    fun isScheduledOn(date: LocalDate): Boolean = days.isEmpty() || date.dayOfWeek in days
}

data class HabitLog(val habitId: Long, val date: LocalDate, val count: Int = 1)

enum class FocusType { FOCUS, BREAK }

data class FocusSession(
    val id: Long = 0,
    val taskId: Long? = null,
    val type: FocusType = FocusType.FOCUS,
    val startedAt: LocalDateTime,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean,
)

/** Kinds of immutable history events stored separately from the live task row. */
enum class HistoryEvent { CREATED, STARTED, COMPLETED, POSTPONED, CANCELLED, RESCHEDULED, DELETED, REOPENED }

/**
 * One immutable row of "My Productivity Memory". It keeps a copy of the task title,
 * area and estimates so the history survives later edits or deletion of the task.
 */
data class ProductivityRecord(
    val id: Long = 0,
    val taskId: Long?,
    val taskTitle: String,
    val area: LifeArea,
    val event: HistoryEvent,
    val at: LocalDateTime,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
)

/** User preferences that influence planning. */
data class DaySettings(
    val dayStart: LocalTime = LocalTime.of(7, 0),
    val dayEnd: LocalTime = LocalTime.of(23, 0),
    val restPeriods: List<MinuteRange> = emptyList(),
    val workDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val bufferMinutes: Int = 5,
    /** Tasks longer than this are split into parts by the planner. */
    val maxChunkMinutes: Int = 90,
    val minChunkMinutes: Int = 25,
)
