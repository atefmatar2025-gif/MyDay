package com.dayflow.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Database schema (version 1).
 * Wall-clock times (task start/due) are stored as "local epoch seconds" (LocalDateTime at UTC),
 * instants (created/completed/alarm times) as epoch milliseconds, dates as epoch days.
 * Enum values are stored by name.
 */

@Entity(tableName = "task_categories")
data class TaskCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** LifeArea name - drives Life Balance and colours. */
    val area: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = TaskCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("parentId"), Index("startAt"), Index("dueAt"), Index("status")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val priority: String,
    val status: String,
    val categoryId: Long? = null,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val parentId: Long? = null,
    val fixedTime: Boolean = false,
    /** Compact recurrence rule, see RecurrenceCodec. */
    val recurrence: String? = null,
    val postponeCount: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    /** When the user last pressed "start" (epoch ms) - used to measure actual time. */
    val startedAt: Long? = null,
)

@Entity(tableName = "task_tags", indices = [Index(value = ["name"], unique = true)])
data class TaskTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "task_tag_refs",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskTagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TaskTagCrossRef(val taskId: Long, val tagId: Long)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "dependsOnId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["dependsOnId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dependsOnId")],
)
data class TaskDependencyEntity(val taskId: Long, val dependsOnId: Long)

@Entity(
    tableName = "task_attachments",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** content:// URI with a persisted read permission; the file itself stays where the user keeps it. */
    val uri: String,
    val displayName: String,
    val mimeType: String?,
)

@Entity(
    tableName = "task_reminders",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val offsetMinutes: Int = 0,
    /** ALARM (rings, full-screen) or NOTIFICATION (normal notification). */
    val mode: String = "ALARM",
    /** null = default sound from settings. */
    val soundUri: String? = null,
    /** 0..100, applied to the alarm stream volume chosen by the user in Android. */
    val volumePercent: Int = 100,
    val vibrate: Boolean = true,
    val ringSeconds: Int = 60,
    val repeatCount: Int = 2,
    val repeatIntervalMinutes: Int = 5,
    val enabled: Boolean = true,
    /** Next trigger registered with AlarmManager (epoch ms), null if none. */
    val nextTriggerAt: Long? = null,
    /** Occurrence (local epoch seconds) the registered trigger belongs to. */
    val nextOccurrence: Long? = null,
    /** Unanswered repeats so far for the current occurrence. */
    val attempts: Int = 0,
)

@Entity(tableName = "alarm_log", indices = [Index("taskId"), Index("at")])
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long?,
    val taskId: Long?,
    val taskTitle: String,
    /** SCHEDULED, FIRED, DISMISSED, SNOOZED, MISSED, REPEATED, CANCELED, INEXACT_FALLBACK, SKIPPED_DUPLICATE */
    val action: String,
    val at: Long,
    val scheduledFor: Long? = null,
    val exact: Boolean = true,
    val details: String = "",
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "✓",
    /** minutes from midnight, null = any time */
    val timeMinute: Int? = null,
    /** bit mask of ISO days (bit 1 = Monday ... bit 7 = Sunday), 0 = every day */
    val daysMask: Int = 0,
    val weeklyTarget: Int? = null,
    val reminderEnabled: Boolean = false,
    val archived: Boolean = false,
    val createdOn: Long,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "habit_logs",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** epoch day */
    val date: Long,
    val count: Int = 1,
)

@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Plain column (no FK) so sessions survive task deletion. */
    val taskId: Long?,
    val taskTitle: String?,
    val type: String,
    /** local epoch seconds */
    val startedAt: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean,
)

/** Immutable history ("My Productivity Memory"), deliberately not linked by FK to tasks. */
@Entity(tableName = "productivity_records", indices = [Index("at"), Index("taskId"), Index("event")])
data class ProductivityRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val taskTitle: String,
    val area: String,
    val event: String,
    /** local epoch seconds */
    val at: Long,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val details: String = "",
)

@Entity(tableName = "daily_plans", indices = [Index("date")])
data class DailyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** epoch day */
    val date: Long,
    val createdAt: Long,
    val appliedAt: Long? = null,
    val summary: String,
    val freeMinutes: Int,
    val requiredMinutes: Int,
)

@Entity(
    tableName = "daily_plan_items",
    foreignKeys = [ForeignKey(entity = DailyPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class DailyPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val taskId: Long?,
    val taskTitle: String,
    /** BLOCK, POSTPONE, AT_RISK, BLOCKED, FIXED */
    val kind: String,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val part: Int = 1,
    val totalParts: Int = 1,
)

/** Single-row settings table (id = 1). */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    /** SYSTEM, LIGHT, DARK */
    val themeMode: String = "SYSTEM",
    val fontScale: Float = 1.0f,
    val language: String = "ar",
    val dayStartMinute: Int = 7 * 60,
    val sleepMinute: Int = 23 * 60,
    /** ISO day mask, see HabitEntity.daysMask. Default: Sun-Thu */
    val workDaysMask: Int = 0b1001111,
    /** ISO day number of the first day of the week (6 = Saturday). */
    val weekStartDay: Int = 6,
    val bufferMinutes: Int = 5,
    val maxChunkMinutes: Int = 90,
    val defaultReminderOffset: Int = 10,
    val defaultReminderMode: String = "ALARM",
    val defaultSoundUri: String? = null,
    val alarmVolumePercent: Int = 100,
    /** NONE, SHORT, LONG, HEARTBEAT */
    val vibrationPattern: String = "LONG",
    val ringSeconds: Int = 60,
    val repeatCount: Int = 2,
    val repeatIntervalMinutes: Int = 5,
    val defaultSnoozeMinutes: Int = 10,
    val showHijri: Boolean = true,
    val hijriAdjustment: Int = 0,
    val dailySummaryEnabled: Boolean = true,
    val dailySummaryMinute: Int = 7 * 60 + 30,
    val habitRemindersEnabled: Boolean = true,
    val completionNotifications: Boolean = true,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    @ColumnInfo(defaultValue = "0") val onboardingDone: Boolean = false,
)

@Entity(tableName = "rest_periods")
data class RestPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
)

/** Optional weekly goal per life area, set by the user (Life Balance never assumes a goal). */
@Entity(tableName = "area_goals")
data class AreaGoalEntity(
    @PrimaryKey val area: String,
    val weeklyMinutes: Int,
)
