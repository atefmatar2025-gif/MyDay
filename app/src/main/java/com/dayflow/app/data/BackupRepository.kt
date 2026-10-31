package com.dayflow.app.data

import androidx.room.withTransaction
import com.dayflow.app.BuildConfig
import com.dayflow.app.data.db.AlarmLogEntity
import com.dayflow.app.data.db.AreaGoalEntity
import com.dayflow.app.data.db.DailyPlanEntity
import com.dayflow.app.data.db.DailyPlanItemEntity
import com.dayflow.app.data.db.DayflowDatabase
import com.dayflow.app.data.db.FocusSessionEntity
import com.dayflow.app.data.db.HabitEntity
import com.dayflow.app.data.db.HabitLogEntity
import com.dayflow.app.data.db.ProductivityRecordEntity
import com.dayflow.app.data.db.RestPeriodEntity
import com.dayflow.app.data.db.TaskAttachmentEntity
import com.dayflow.app.data.db.TaskCategoryEntity
import com.dayflow.app.data.db.TaskDependencyEntity
import com.dayflow.app.data.db.TaskEntity
import com.dayflow.app.data.db.TaskReminderEntity
import com.dayflow.app.data.db.TaskTagCrossRef
import com.dayflow.app.data.db.TaskTagEntity
import com.dayflow.app.data.db.UserSettingsEntity
import com.dayflow.core.backup.BackupCodec
import com.dayflow.core.backup.BackupCrypto
import com.dayflow.core.backup.BackupFormatException
import com.dayflow.core.backup.BackupSnapshot

private typealias Row = Map<String, Any?>

private fun Row.long(k: String): Long = (this[k] as? Number)?.toLong() ?: throw BackupFormatException("حقل مفقود: $k")
private fun Row.longOrNull(k: String): Long? = (this[k] as? Number)?.toLong()
private fun Row.int(k: String): Int = long(k).toInt()
private fun Row.intOrNull(k: String): Int? = longOrNull(k)?.toInt()
private fun Row.str(k: String): String = this[k] as? String ?: throw BackupFormatException("حقل مفقود: $k")
private fun Row.strOrNull(k: String): String? = this[k] as? String
private fun Row.bool(k: String): Boolean = this[k] as? Boolean ?: false
private fun Row.float(k: String): Float = (this[k] as? Number)?.toFloat() ?: 1f

/** One table in the backup: how to read it from Room and how to convert rows both ways. */
private class TableCodec<T>(
    val name: String,
    val read: suspend () -> List<T>,
    val write: suspend (List<T>) -> Unit,
    val toRow: (T) -> Row,
    val fromRow: (Row) -> T,
) {
    suspend fun export(): List<Row> = read().map(toRow)
    suspend fun import(rows: List<Row>) { if (rows.isNotEmpty()) write(rows.map(fromRow)) }
}

/**
 * Export / backup / restore / delete-everything.
 * The backup contains every table, so a restore on a new phone recreates the full state.
 */
class BackupRepository(private val db: DayflowDatabase) {
    private val dao = db.backupDao()

    // Insert order: parents before children (FK checks are additionally deferred during restore).
    private val codecs: List<TableCodec<*>> = listOf(
        TableCodec("user_settings", dao::settings, dao::insertSettings, { s: UserSettingsEntity ->
            mapOf(
                "id" to s.id, "displayName" to s.displayName, "themeMode" to s.themeMode, "fontScale" to s.fontScale.toDouble(),
                "language" to s.language, "dayStartMinute" to s.dayStartMinute, "sleepMinute" to s.sleepMinute,
                "workDaysMask" to s.workDaysMask, "weekStartDay" to s.weekStartDay, "bufferMinutes" to s.bufferMinutes,
                "maxChunkMinutes" to s.maxChunkMinutes, "defaultReminderOffset" to s.defaultReminderOffset,
                "defaultReminderMode" to s.defaultReminderMode, "defaultSoundUri" to s.defaultSoundUri,
                "alarmVolumePercent" to s.alarmVolumePercent, "vibrationPattern" to s.vibrationPattern,
                "ringSeconds" to s.ringSeconds, "repeatCount" to s.repeatCount, "repeatIntervalMinutes" to s.repeatIntervalMinutes,
                "defaultSnoozeMinutes" to s.defaultSnoozeMinutes, "showHijri" to s.showHijri, "hijriAdjustment" to s.hijriAdjustment,
                "dailySummaryEnabled" to s.dailySummaryEnabled, "dailySummaryMinute" to s.dailySummaryMinute,
                "habitRemindersEnabled" to s.habitRemindersEnabled, "completionNotifications" to s.completionNotifications,
                "focusMinutes" to s.focusMinutes, "shortBreakMinutes" to s.shortBreakMinutes, "longBreakMinutes" to s.longBreakMinutes,
                "cyclesBeforeLongBreak" to s.cyclesBeforeLongBreak, "onboardingDone" to s.onboardingDone,
            )
        }, { r ->
            val d = UserSettingsEntity()
            UserSettingsEntity(
                id = 1, displayName = r.strOrNull("displayName") ?: "", themeMode = r.strOrNull("themeMode") ?: d.themeMode,
                fontScale = r.float("fontScale"), language = r.strOrNull("language") ?: "ar",
                dayStartMinute = r.intOrNull("dayStartMinute") ?: d.dayStartMinute, sleepMinute = r.intOrNull("sleepMinute") ?: d.sleepMinute,
                workDaysMask = r.intOrNull("workDaysMask") ?: d.workDaysMask, weekStartDay = r.intOrNull("weekStartDay") ?: d.weekStartDay,
                bufferMinutes = r.intOrNull("bufferMinutes") ?: d.bufferMinutes, maxChunkMinutes = r.intOrNull("maxChunkMinutes") ?: d.maxChunkMinutes,
                defaultReminderOffset = r.intOrNull("defaultReminderOffset") ?: d.defaultReminderOffset,
                defaultReminderMode = r.strOrNull("defaultReminderMode") ?: d.defaultReminderMode, defaultSoundUri = r.strOrNull("defaultSoundUri"),
                alarmVolumePercent = r.intOrNull("alarmVolumePercent") ?: d.alarmVolumePercent,
                vibrationPattern = r.strOrNull("vibrationPattern") ?: d.vibrationPattern, ringSeconds = r.intOrNull("ringSeconds") ?: d.ringSeconds,
                repeatCount = r.intOrNull("repeatCount") ?: d.repeatCount, repeatIntervalMinutes = r.intOrNull("repeatIntervalMinutes") ?: d.repeatIntervalMinutes,
                defaultSnoozeMinutes = r.intOrNull("defaultSnoozeMinutes") ?: d.defaultSnoozeMinutes, showHijri = r.bool("showHijri"),
                hijriAdjustment = r.intOrNull("hijriAdjustment") ?: 0, dailySummaryEnabled = r.bool("dailySummaryEnabled"),
                dailySummaryMinute = r.intOrNull("dailySummaryMinute") ?: d.dailySummaryMinute, habitRemindersEnabled = r.bool("habitRemindersEnabled"),
                completionNotifications = r.bool("completionNotifications"), focusMinutes = r.intOrNull("focusMinutes") ?: d.focusMinutes,
                shortBreakMinutes = r.intOrNull("shortBreakMinutes") ?: d.shortBreakMinutes, longBreakMinutes = r.intOrNull("longBreakMinutes") ?: d.longBreakMinutes,
                cyclesBeforeLongBreak = r.intOrNull("cyclesBeforeLongBreak") ?: d.cyclesBeforeLongBreak, onboardingDone = true,
            )
        }),
        TableCodec("rest_periods", dao::restPeriods, dao::insertRestPeriods, { e: RestPeriodEntity ->
            mapOf("id" to e.id, "label" to e.label, "startMinute" to e.startMinute, "endMinute" to e.endMinute)
        }, { r -> RestPeriodEntity(r.long("id"), r.str("label"), r.int("startMinute"), r.int("endMinute")) }),
        TableCodec("area_goals", dao::areaGoals, dao::insertAreaGoals, { e: AreaGoalEntity ->
            mapOf("area" to e.area, "weeklyMinutes" to e.weeklyMinutes)
        }, { r -> AreaGoalEntity(r.str("area"), r.int("weeklyMinutes")) }),
        TableCodec("task_categories", dao::categories, dao::insertCategories, { e: TaskCategoryEntity ->
            mapOf("id" to e.id, "name" to e.name, "area" to e.area, "colorHex" to e.colorHex, "isDefault" to e.isDefault, "sortOrder" to e.sortOrder)
        }, { r -> TaskCategoryEntity(r.long("id"), r.str("name"), r.str("area"), r.str("colorHex"), r.bool("isDefault"), r.intOrNull("sortOrder") ?: 0) }),
        TableCodec("tasks", dao::tasks, dao::insertTasks, { e: TaskEntity ->
            mapOf(
                "id" to e.id, "title" to e.title, "description" to e.description, "notes" to e.notes, "priority" to e.priority,
                "status" to e.status, "categoryId" to e.categoryId, "startAt" to e.startAt, "dueAt" to e.dueAt,
                "estimatedMinutes" to e.estimatedMinutes, "actualMinutes" to e.actualMinutes, "parentId" to e.parentId,
                "fixedTime" to e.fixedTime, "recurrence" to e.recurrence, "postponeCount" to e.postponeCount, "archived" to e.archived,
                "createdAt" to e.createdAt, "updatedAt" to e.updatedAt, "completedAt" to e.completedAt, "startedAt" to e.startedAt,
            )
        }, { r ->
            TaskEntity(
                id = r.long("id"), title = r.str("title"), description = r.strOrNull("description") ?: "", notes = r.strOrNull("notes") ?: "",
                priority = r.str("priority"), status = r.str("status"), categoryId = r.longOrNull("categoryId"),
                startAt = r.longOrNull("startAt"), dueAt = r.longOrNull("dueAt"), estimatedMinutes = r.intOrNull("estimatedMinutes"),
                actualMinutes = r.intOrNull("actualMinutes"), parentId = r.longOrNull("parentId"), fixedTime = r.bool("fixedTime"),
                recurrence = r.strOrNull("recurrence"), postponeCount = r.intOrNull("postponeCount") ?: 0, archived = r.bool("archived"),
                createdAt = r.long("createdAt"), updatedAt = r.longOrNull("updatedAt") ?: r.long("createdAt"),
                completedAt = r.longOrNull("completedAt"), startedAt = r.longOrNull("startedAt"),
            )
        }),
        TableCodec("task_tags", dao::tags, dao::insertTags, { e: TaskTagEntity -> mapOf("id" to e.id, "name" to e.name) },
            { r -> TaskTagEntity(r.long("id"), r.str("name")) }),
        TableCodec("task_tag_refs", dao::tagRefs, dao::insertTagRefs, { e: TaskTagCrossRef -> mapOf("taskId" to e.taskId, "tagId" to e.tagId) },
            { r -> TaskTagCrossRef(r.long("taskId"), r.long("tagId")) }),
        TableCodec("task_dependencies", dao::dependencies, dao::insertDependencies, { e: TaskDependencyEntity ->
            mapOf("taskId" to e.taskId, "dependsOnId" to e.dependsOnId)
        }, { r -> TaskDependencyEntity(r.long("taskId"), r.long("dependsOnId")) }),
        TableCodec("task_attachments", dao::attachments, dao::insertAttachments, { e: TaskAttachmentEntity ->
            mapOf("id" to e.id, "taskId" to e.taskId, "uri" to e.uri, "displayName" to e.displayName, "mimeType" to e.mimeType)
        }, { r -> TaskAttachmentEntity(r.long("id"), r.long("taskId"), r.str("uri"), r.str("displayName"), r.strOrNull("mimeType")) }),
        TableCodec("task_reminders", dao::reminders, dao::insertReminders, { e: TaskReminderEntity ->
            mapOf(
                "id" to e.id, "taskId" to e.taskId, "offsetMinutes" to e.offsetMinutes, "mode" to e.mode, "soundUri" to e.soundUri,
                "volumePercent" to e.volumePercent, "vibrate" to e.vibrate, "ringSeconds" to e.ringSeconds, "repeatCount" to e.repeatCount,
                "repeatIntervalMinutes" to e.repeatIntervalMinutes, "enabled" to e.enabled,
            )
        }, { r ->
            TaskReminderEntity(
                id = r.long("id"), taskId = r.long("taskId"), offsetMinutes = r.int("offsetMinutes"), mode = r.strOrNull("mode") ?: "ALARM",
                soundUri = r.strOrNull("soundUri"), volumePercent = r.intOrNull("volumePercent") ?: 100, vibrate = r.bool("vibrate"),
                ringSeconds = r.intOrNull("ringSeconds") ?: 60, repeatCount = r.intOrNull("repeatCount") ?: 2,
                repeatIntervalMinutes = r.intOrNull("repeatIntervalMinutes") ?: 5, enabled = r.bool("enabled"),
            )
        }),
        TableCodec("alarm_log", dao::alarmLog, dao::insertAlarmLog, { e: AlarmLogEntity ->
            mapOf(
                "id" to e.id, "reminderId" to e.reminderId, "taskId" to e.taskId, "taskTitle" to e.taskTitle, "action" to e.action,
                "at" to e.at, "scheduledFor" to e.scheduledFor, "exact" to e.exact, "details" to e.details,
            )
        }, { r ->
            AlarmLogEntity(
                r.long("id"), r.longOrNull("reminderId"), r.longOrNull("taskId"), r.str("taskTitle"), r.str("action"), r.long("at"),
                r.longOrNull("scheduledFor"), r.bool("exact"), r.strOrNull("details") ?: "",
            )
        }),
        TableCodec("habits", dao::habits, dao::insertHabits, { e: HabitEntity ->
            mapOf(
                "id" to e.id, "name" to e.name, "icon" to e.icon, "timeMinute" to e.timeMinute, "daysMask" to e.daysMask,
                "weeklyTarget" to e.weeklyTarget, "reminderEnabled" to e.reminderEnabled, "archived" to e.archived,
                "createdOn" to e.createdOn, "sortOrder" to e.sortOrder,
            )
        }, { r ->
            HabitEntity(
                r.long("id"), r.str("name"), r.strOrNull("icon") ?: "✓", r.intOrNull("timeMinute"), r.intOrNull("daysMask") ?: 0,
                r.intOrNull("weeklyTarget"), r.bool("reminderEnabled"), r.bool("archived"), r.long("createdOn"), r.intOrNull("sortOrder") ?: 0,
            )
        }),
        TableCodec("habit_logs", dao::habitLogs, dao::insertHabitLogs, { e: HabitLogEntity ->
            mapOf("id" to e.id, "habitId" to e.habitId, "date" to e.date, "count" to e.count)
        }, { r -> HabitLogEntity(r.long("id"), r.long("habitId"), r.long("date"), r.intOrNull("count") ?: 1) }),
        TableCodec("focus_sessions", dao::focusSessions, dao::insertFocusSessions, { e: FocusSessionEntity ->
            mapOf(
                "id" to e.id, "taskId" to e.taskId, "taskTitle" to e.taskTitle, "type" to e.type, "startedAt" to e.startedAt,
                "plannedMinutes" to e.plannedMinutes, "actualMinutes" to e.actualMinutes, "completed" to e.completed,
            )
        }, { r ->
            FocusSessionEntity(
                r.long("id"), r.longOrNull("taskId"), r.strOrNull("taskTitle"), r.str("type"), r.long("startedAt"),
                r.int("plannedMinutes"), r.int("actualMinutes"), r.bool("completed"),
            )
        }),
        TableCodec("productivity_records", dao::records, dao::insertRecords, { e: ProductivityRecordEntity ->
            mapOf(
                "id" to e.id, "taskId" to e.taskId, "taskTitle" to e.taskTitle, "area" to e.area, "event" to e.event, "at" to e.at,
                "estimatedMinutes" to e.estimatedMinutes, "actualMinutes" to e.actualMinutes, "details" to e.details,
            )
        }, { r ->
            ProductivityRecordEntity(
                r.long("id"), r.longOrNull("taskId"), r.str("taskTitle"), r.str("area"), r.str("event"), r.long("at"),
                r.intOrNull("estimatedMinutes"), r.intOrNull("actualMinutes"), r.strOrNull("details") ?: "",
            )
        }),
        TableCodec("daily_plans", dao::plans, dao::insertPlans, { e: DailyPlanEntity ->
            mapOf(
                "id" to e.id, "date" to e.date, "createdAt" to e.createdAt, "appliedAt" to e.appliedAt, "summary" to e.summary,
                "freeMinutes" to e.freeMinutes, "requiredMinutes" to e.requiredMinutes,
            )
        }, { r ->
            DailyPlanEntity(r.long("id"), r.long("date"), r.long("createdAt"), r.longOrNull("appliedAt"), r.str("summary"), r.int("freeMinutes"), r.int("requiredMinutes"))
        }),
        TableCodec("daily_plan_items", dao::planItems, dao::insertPlanItems, { e: DailyPlanItemEntity ->
            mapOf(
                "id" to e.id, "planId" to e.planId, "taskId" to e.taskId, "taskTitle" to e.taskTitle, "kind" to e.kind,
                "startAt" to e.startAt, "endAt" to e.endAt, "part" to e.part, "totalParts" to e.totalParts,
            )
        }, { r ->
            DailyPlanItemEntity(
                r.long("id"), r.long("planId"), r.longOrNull("taskId"), r.str("taskTitle"), r.str("kind"),
                r.longOrNull("startAt"), r.longOrNull("endAt"), r.intOrNull("part") ?: 1, r.intOrNull("totalParts") ?: 1,
            )
        }),
    )

    suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        schemaVersion = DayflowDatabase.VERSION,
        appVersion = BuildConfig.VERSION_NAME,
        createdAtEpochMillis = System.currentTimeMillis(),
        tables = codecs.associate { it.name to it.export() },
    )

    /** Human-readable JSON export (not encrypted). */
    suspend fun exportJson(): String = BackupCodec.toJson(snapshot())

    /** Compressed backup file, AES-GCM encrypted when [password] is given. */
    suspend fun createBackup(password: CharArray?): ByteArray = BackupCrypto.pack(exportJson(), password)

    fun isEncrypted(bytes: ByteArray): Boolean =
        if (looksLikeJson(bytes)) false else BackupCrypto.isEncrypted(bytes)

    /** Accepts both a .dflw backup and a .json export. Replaces ALL current data atomically. */
    suspend fun restore(bytes: ByteArray, password: CharArray?) {
        val json = if (looksLikeJson(bytes)) bytes.toString(Charsets.UTF_8) else BackupCrypto.unpack(bytes, password)
        val snapshot = BackupCodec.fromJson(json)
        if (snapshot.schemaVersion > DayflowDatabase.VERSION) {
            throw BackupFormatException("هذه النسخة من إصدار أحدث من التطبيق. حدّث DAYFLOW ثم أعد المحاولة.")
        }
        // Parse every row first so a malformed file fails before anything is deleted.
        @Suppress("UNCHECKED_CAST")
        val parsed = codecs.map { codec -> codec to (snapshot.tables[codec.name] ?: emptyList()).map { codec.fromRow(it) } }
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
            wipeTablesInTransaction()
            for ((codec, rows) in parsed) {
                @Suppress("UNCHECKED_CAST")
                (codec as TableCodec<Any?>).write(rows)
            }
        }
    }

    /** "Delete all my data". */
    suspend fun deleteEverything() {
        db.withTransaction { wipeTablesInTransaction() }
    }

    private fun wipeTablesInTransaction() {
        val w = db.openHelper.writableDatabase
        DayflowDatabase.TABLES_DELETE_ORDER.forEach { w.execSQL("DELETE FROM `$it`") }
        runCatching { w.execSQL("DELETE FROM sqlite_sequence") }
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() } ?: return false
        return first.toInt().toChar() == '{' || (bytes.size > 3 && bytes[0] == 0xEF.toByte() && bytes[3].toInt().toChar() == '{')
    }
}
