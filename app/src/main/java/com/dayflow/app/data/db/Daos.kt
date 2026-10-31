package com.dayflow.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY COALESCE(startAt, dueAt, 9223372036854775807), id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId")
    suspend fun children(parentId: Long): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- tags ----
    @Query("SELECT * FROM task_tags ORDER BY name")
    fun observeTags(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM task_tag_refs")
    fun observeTagRefs(): Flow<List<TaskTagCrossRef>>

    @Query("SELECT * FROM task_tags WHERE name = :name LIMIT 1")
    suspend fun tagByName(name: String): TaskTagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TaskTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_refs WHERE taskId = :taskId")
    suspend fun clearTagRefs(taskId: Long)

    @Query("DELETE FROM task_tags WHERE id NOT IN (SELECT tagId FROM task_tag_refs)")
    suspend fun deleteUnusedTags()

    // ---- dependencies ----
    @Query("SELECT * FROM task_dependencies")
    fun observeDependencies(): Flow<List<TaskDependencyEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependencies(deps: List<TaskDependencyEntity>)

    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId")
    suspend fun clearDependencies(taskId: Long)

    // ---- attachments ----
    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    fun observeAttachments(taskId: Long): Flow<List<TaskAttachmentEntity>>

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    suspend fun attachments(taskId: Long): List<TaskAttachmentEntity>

    @Insert
    suspend fun insertAttachment(a: TaskAttachmentEntity): Long

    @Query("DELETE FROM task_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM task_categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TaskCategoryEntity>>

    @Query("SELECT * FROM task_categories ORDER BY sortOrder, id")
    suspend fun getAll(): List<TaskCategoryEntity>

    @Query("SELECT COUNT(*) FROM task_categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<TaskCategoryEntity>)

    @Upsert
    suspend fun upsert(item: TaskCategoryEntity): Long

    @Query("DELETE FROM task_categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY offsetMinutes DESC")
    suspend fun forTask(taskId: Long): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY offsetMinutes DESC")
    fun observeForTask(taskId: Long): Flow<List<TaskReminderEntity>>

    @Query("SELECT * FROM task_reminders")
    suspend fun getAll(): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders")
    fun observeAll(): Flow<List<TaskReminderEntity>>

    @Query("SELECT * FROM task_reminders WHERE id = :id")
    suspend fun getById(id: Long): TaskReminderEntity?

    @Query("SELECT * FROM task_reminders WHERE enabled = 1 AND nextTriggerAt IS NOT NULL AND nextTriggerAt > :now ORDER BY nextTriggerAt LIMIT 1")
    fun observeNext(now: Long): Flow<TaskReminderEntity?>

    @Insert
    suspend fun insert(r: TaskReminderEntity): Long

    @Update
    suspend fun update(r: TaskReminderEntity)

    @Delete
    suspend fun delete(r: TaskReminderEntity)
}

@Dao
interface AlarmLogDao {
    @Insert
    suspend fun insert(log: AlarmLogEntity)

    @Query("SELECT * FROM alarm_log ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmLogEntity>>

    /** Used to drop a duplicate delivery of the same scheduled alarm. */
    @Query("SELECT COUNT(*) FROM alarm_log WHERE reminderId = :reminderId AND scheduledFor = :scheduledFor AND action = 'FIRED'")
    suspend fun firedCount(reminderId: Long, scheduledFor: Long): Int

    @Query("DELETE FROM alarm_log WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Insert
    suspend fun insert(h: HabitEntity): Long

    @Update
    suspend fun update(h: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_logs")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs")
    suspend fun getLogs(): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity): Long

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLog(habitId: Long, date: Long)
}

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(s: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAll(): List<FocusSessionEntity>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(r: ProductivityRecordEntity)

    @Query("SELECT * FROM productivity_records ORDER BY at")
    fun observeAll(): Flow<List<ProductivityRecordEntity>>

    @Query("SELECT * FROM productivity_records ORDER BY at")
    suspend fun getAll(): List<ProductivityRecordEntity>
}

@Dao
interface PlanDao {
    @Insert
    suspend fun insertPlan(p: DailyPlanEntity): Long

    @Insert
    suspend fun insertItems(items: List<DailyPlanItemEntity>)

    @Transaction
    suspend fun insertWithItems(plan: DailyPlanEntity, items: List<DailyPlanItemEntity>): Long {
        val id = insertPlan(plan)
        insertItems(items.map { it.copy(planId = id) })
        return id
    }

    @Query("SELECT * FROM daily_plans ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyPlanEntity>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettingsEntity?

    @Upsert
    suspend fun upsert(s: UserSettingsEntity)

    @Query("SELECT * FROM rest_periods ORDER BY startMinute")
    fun observeRestPeriods(): Flow<List<RestPeriodEntity>>

    @Query("SELECT * FROM rest_periods ORDER BY startMinute")
    suspend fun restPeriods(): List<RestPeriodEntity>

    @Insert
    suspend fun insertRestPeriod(r: RestPeriodEntity): Long

    @Query("DELETE FROM rest_periods WHERE id = :id")
    suspend fun deleteRestPeriod(id: Long)

    @Query("SELECT * FROM area_goals")
    fun observeAreaGoals(): Flow<List<AreaGoalEntity>>

    @Upsert
    suspend fun upsertAreaGoal(g: AreaGoalEntity)

    @Query("DELETE FROM area_goals WHERE area = :area")
    suspend fun deleteAreaGoal(area: String)
}

/** Raw whole-table access used only by backup/restore/export and "delete all my data". */
@Dao
interface BackupDao {
    @Query("SELECT * FROM task_categories") suspend fun categories(): List<TaskCategoryEntity>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM task_tags") suspend fun tags(): List<TaskTagEntity>
    @Query("SELECT * FROM task_tag_refs") suspend fun tagRefs(): List<TaskTagCrossRef>
    @Query("SELECT * FROM task_dependencies") suspend fun dependencies(): List<TaskDependencyEntity>
    @Query("SELECT * FROM task_attachments") suspend fun attachments(): List<TaskAttachmentEntity>
    @Query("SELECT * FROM task_reminders") suspend fun reminders(): List<TaskReminderEntity>
    @Query("SELECT * FROM alarm_log") suspend fun alarmLog(): List<AlarmLogEntity>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_logs") suspend fun habitLogs(): List<HabitLogEntity>
    @Query("SELECT * FROM focus_sessions") suspend fun focusSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM productivity_records") suspend fun records(): List<ProductivityRecordEntity>
    @Query("SELECT * FROM daily_plans") suspend fun plans(): List<DailyPlanEntity>
    @Query("SELECT * FROM daily_plan_items") suspend fun planItems(): List<DailyPlanItemEntity>
    @Query("SELECT * FROM user_settings") suspend fun settings(): List<UserSettingsEntity>
    @Query("SELECT * FROM rest_periods") suspend fun restPeriods(): List<RestPeriodEntity>
    @Query("SELECT * FROM area_goals") suspend fun areaGoals(): List<AreaGoalEntity>

    @Insert suspend fun insertCategories(v: List<TaskCategoryEntity>)
    @Insert suspend fun insertTasks(v: List<TaskEntity>)
    @Insert suspend fun insertTags(v: List<TaskTagEntity>)
    @Insert suspend fun insertTagRefs(v: List<TaskTagCrossRef>)
    @Insert suspend fun insertDependencies(v: List<TaskDependencyEntity>)
    @Insert suspend fun insertAttachments(v: List<TaskAttachmentEntity>)
    @Insert suspend fun insertReminders(v: List<TaskReminderEntity>)
    @Insert suspend fun insertAlarmLog(v: List<AlarmLogEntity>)
    @Insert suspend fun insertHabits(v: List<HabitEntity>)
    @Insert suspend fun insertHabitLogs(v: List<HabitLogEntity>)
    @Insert suspend fun insertFocusSessions(v: List<FocusSessionEntity>)
    @Insert suspend fun insertRecords(v: List<ProductivityRecordEntity>)
    @Insert suspend fun insertPlans(v: List<DailyPlanEntity>)
    @Insert suspend fun insertPlanItems(v: List<DailyPlanItemEntity>)
    @Insert suspend fun insertSettings(v: List<UserSettingsEntity>)
    @Insert suspend fun insertRestPeriods(v: List<RestPeriodEntity>)
    @Insert suspend fun insertAreaGoals(v: List<AreaGoalEntity>)
}
