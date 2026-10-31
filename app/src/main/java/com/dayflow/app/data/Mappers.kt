package com.dayflow.app.data

import com.dayflow.app.data.db.FocusSessionEntity
import com.dayflow.app.data.db.HabitEntity
import com.dayflow.app.data.db.HabitLogEntity
import com.dayflow.app.data.db.ProductivityRecordEntity
import com.dayflow.app.data.db.RestPeriodEntity
import com.dayflow.app.data.db.TaskEntity
import com.dayflow.app.data.db.UserSettingsEntity
import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.FocusSession
import com.dayflow.core.model.FocusType
import com.dayflow.core.model.Habit
import com.dayflow.core.model.HabitLog
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.MinuteRange
import com.dayflow.core.model.Priority
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.RecurrenceCodec
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.time.LocalTimes
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun TaskEntity.toDomain(area: LifeArea, tags: List<String>, dependsOn: List<Long>): Task = Task(
    id = id,
    title = title,
    description = description,
    notes = notes,
    priority = Priority.fromName(priority),
    status = TaskStatus.fromName(status),
    categoryId = categoryId,
    area = area,
    tags = tags,
    startAt = LocalTimes.fromEpochSecond(startAt),
    dueAt = LocalTimes.fromEpochSecond(dueAt),
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
    parentId = parentId,
    dependsOn = dependsOn,
    fixedTime = fixedTime,
    recurrence = RecurrenceCodec.decode(recurrence),
    postponeCount = postponeCount,
    archived = archived,
    createdAt = Instant.ofEpochMilli(createdAt),
    completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
)

object DayMask {
    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    fun fromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}

fun HabitEntity.toDomain() = Habit(
    id = id,
    name = name,
    icon = icon,
    time = timeMinute?.let { LocalTime.of(it / 60, it % 60) },
    days = DayMask.fromMask(daysMask),
    weeklyTarget = weeklyTarget,
    reminderEnabled = reminderEnabled,
    archived = archived,
    createdOn = LocalDate.ofEpochDay(createdOn),
)

fun HabitLogEntity.toDomain() = HabitLog(habitId, LocalDate.ofEpochDay(date), count)

fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    taskId = taskId,
    type = if (type == FocusType.BREAK.name) FocusType.BREAK else FocusType.FOCUS,
    startedAt = LocalTimes.fromEpochSecond(startedAt)!!,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    completed = completed,
)

fun ProductivityRecordEntity.toDomain() = ProductivityRecord(
    id = id,
    taskId = taskId,
    taskTitle = taskTitle,
    area = LifeArea.fromName(area),
    event = HistoryEvent.entries.firstOrNull { it.name == event } ?: HistoryEvent.CREATED,
    at = LocalTimes.fromEpochSecond(at)!!,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
)

fun UserSettingsEntity.toDaySettings(rest: List<RestPeriodEntity>) = DaySettings(
    dayStart = minuteToTime(dayStartMinute),
    dayEnd = minuteToTime(sleepMinute),
    restPeriods = rest.filter { it.endMinute > it.startMinute }.map { MinuteRange(it.startMinute, it.endMinute) },
    workDays = DayMask.fromMask(workDaysMask),
    bufferMinutes = bufferMinutes,
    maxChunkMinutes = maxChunkMinutes,
)

val UserSettingsEntity.weekStart: DayOfWeek get() = DayOfWeek.of(weekStartDay.coerceIn(1, 7))

fun minuteToTime(minute: Int): LocalTime =
    if (minute >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(minute.coerceAtLeast(0) / 60, minute.coerceAtLeast(0) % 60)

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
