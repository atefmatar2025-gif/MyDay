package com.dayflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dayflow.app.data.db.AreaGoalEntity
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
import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.FocusSession
import com.dayflow.core.model.Habit
import com.dayflow.core.model.HabitLog
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.Task
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

class TaskRepository(private val db: DayflowDatabase) {
    private val dao = db.taskDao()

    val categories: Flow<List<TaskCategoryEntity>> = db.categoryDao().observeAll()
    val tags: Flow<List<TaskTagEntity>> = dao.observeTags()

    /** All tasks (including archived) as domain models, with area, tags and dependencies resolved. */
    val tasks: Flow<List<Task>> = combine(
        dao.observeAll(), categories, dao.observeTagRefs(), tags, dao.observeDependencies(),
    ) { tasks, cats, refs, tagList, deps ->
        assemble(tasks, cats, refs, tagList, deps)
    }

    suspend fun snapshot(): List<Task> = tasks.first()

    private fun assemble(
        tasks: List<TaskEntity>,
        cats: List<TaskCategoryEntity>,
        refs: List<TaskTagCrossRef>,
        tagList: List<TaskTagEntity>,
        deps: List<TaskDependencyEntity>,
    ): List<Task> {
        val areaByCat = cats.associate { it.id to LifeArea.fromName(it.area) }
        val tagName = tagList.associate { it.id to it.name }
        val tagsByTask = refs.groupBy({ it.taskId }, { tagName[it.tagId] }).mapValues { it.value.filterNotNull() }
        val depsByTask = deps.groupBy({ it.taskId }, { it.dependsOnId })
        return tasks.map { e ->
            e.toDomain(
                area = e.categoryId?.let { areaByCat[it] } ?: LifeArea.PERSONAL,
                tags = tagsByTask[e.id].orEmpty(),
                dependsOn = depsByTask[e.id].orEmpty(),
            )
        }
    }

    suspend fun entity(id: Long): TaskEntity? = dao.getById(id)
    fun observeEntity(id: Long): Flow<TaskEntity?> = dao.observeById(id)
    suspend fun children(id: Long) = dao.children(id)

    suspend fun insert(e: TaskEntity): Long = dao.insert(e)
    suspend fun update(e: TaskEntity) = dao.update(e)
    suspend fun delete(id: Long) {
        dao.deleteById(id)
        dao.deleteUnusedTags()
    }

    suspend fun setTags(taskId: Long, names: List<String>) {
        dao.clearTagRefs(taskId)
        val ids = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { name ->
            dao.tagByName(name)?.id ?: dao.insertTag(TaskTagEntity(name = name)).let { id ->
                if (id > 0) id else dao.tagByName(name)!!.id
            }
        }
        dao.insertTagRefs(ids.map { TaskTagCrossRef(taskId, it) })
        dao.deleteUnusedTags()
    }

    suspend fun setDependencies(taskId: Long, dependsOn: List<Long>) {
        dao.clearDependencies(taskId)
        dao.insertDependencies(dependsOn.filter { it != taskId }.distinct().map { TaskDependencyEntity(taskId, it) })
    }

    fun observeAttachments(taskId: Long) = dao.observeAttachments(taskId)
    suspend fun attachments(taskId: Long) = dao.attachments(taskId)
    suspend fun addAttachment(a: TaskAttachmentEntity) = dao.insertAttachment(a)
    suspend fun removeAttachment(id: Long) = dao.deleteAttachment(id)

    // ---- reminders ----
    private val reminderDao = db.reminderDao()
    fun observeReminders(taskId: Long) = reminderDao.observeForTask(taskId)
    suspend fun reminders(taskId: Long) = reminderDao.forTask(taskId)
    suspend fun allReminders() = reminderDao.getAll()
    val allRemindersFlow: Flow<List<TaskReminderEntity>> = reminderDao.observeAll()
    suspend fun reminder(id: Long) = reminderDao.getById(id)
    suspend fun insertReminder(r: TaskReminderEntity) = reminderDao.insert(r)
    suspend fun updateReminder(r: TaskReminderEntity) = reminderDao.update(r)
    suspend fun deleteReminder(r: TaskReminderEntity) = reminderDao.delete(r)
    fun observeNextReminder(now: Long) = reminderDao.observeNext(now)

    // ---- categories ----
    suspend fun ensureDefaultCategories() {
        val catDao = db.categoryDao()
        if (catDao.count() > 0) return
        catDao.insertAll(
            listOf(
                TaskCategoryEntity(name = "العمل", area = LifeArea.WORK.name, colorHex = "#294C80", isDefault = true, sortOrder = 1),
                TaskCategoryEntity(name = "الدراسة", area = LifeArea.STUDY.name, colorHex = "#5B6CC9", isDefault = true, sortOrder = 2),
                TaskCategoryEntity(name = "العبادة", area = LifeArea.WORSHIP.name, colorHex = "#2E8C83", isDefault = true, sortOrder = 3),
                TaskCategoryEntity(name = "الأسرة", area = LifeArea.FAMILY.name, colorHex = "#D9734E", isDefault = true, sortOrder = 4),
                TaskCategoryEntity(name = "الصحة", area = LifeArea.HEALTH.name, colorHex = "#38A878", isDefault = true, sortOrder = 5),
                TaskCategoryEntity(name = "الراحة", area = LifeArea.REST.name, colorHex = "#8A94A6", isDefault = true, sortOrder = 6),
                TaskCategoryEntity(name = "التطوير الشخصي", area = LifeArea.PERSONAL_GROWTH.name, colorHex = "#9C6ADE", isDefault = true, sortOrder = 7),
                TaskCategoryEntity(name = "الالتزامات الاجتماعية", area = LifeArea.SOCIAL.name, colorHex = "#C2589A", isDefault = true, sortOrder = 8),
                TaskCategoryEntity(name = "المواعيد", area = LifeArea.APPOINTMENT.name, colorHex = "#F39B42", isDefault = true, sortOrder = 9),
                TaskCategoryEntity(name = "المناسبات الشخصية", area = LifeArea.PERSONAL.name, colorHex = "#E0A526", isDefault = true, sortOrder = 10),
                TaskCategoryEntity(name = "مهام شخصية", area = LifeArea.PERSONAL.name, colorHex = "#6B7A90", isDefault = true, sortOrder = 11),
            ),
        )
    }

    suspend fun saveCategory(c: TaskCategoryEntity) = db.categoryDao().upsert(c)
    suspend fun deleteCategory(id: Long) = db.categoryDao().delete(id)
}

class HabitRepository(db: DayflowDatabase) {
    private val dao = db.habitDao()
    val habits: Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val habitEntities: Flow<List<HabitEntity>> = dao.observeAll()
    val logs: Flow<List<HabitLog>> = dao.observeLogs().map { list -> list.map { it.toDomain() } }

    suspend fun habit(id: Long) = dao.getById(id)
    suspend fun all() = dao.getAll()
    suspend fun snapshot(): List<Habit> = dao.getAll().map { it.toDomain() }
    suspend fun logsSnapshot(): List<HabitLog> = dao.getLogs().map { it.toDomain() }
    suspend fun save(h: HabitEntity): Long = if (h.id == 0L) dao.insert(h) else { dao.update(h); h.id }
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setDone(habitId: Long, date: LocalDate, done: Boolean) {
        if (done) dao.insertLog(HabitLogEntity(habitId = habitId, date = date.toEpochDay()))
        else dao.deleteLog(habitId, date.toEpochDay())
    }
}

class FocusRepository(db: DayflowDatabase) {
    private val dao = db.focusDao()
    val sessions: Flow<List<FocusSession>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val sessionEntities: Flow<List<FocusSessionEntity>> = dao.observeAll()
    suspend fun snapshot() = dao.getAll().map { it.toDomain() }
    suspend fun add(s: FocusSessionEntity) = dao.insert(s)
}

class HistoryRepository(db: DayflowDatabase) {
    private val dao = db.historyDao()
    val records: Flow<List<ProductivityRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun snapshot() = dao.getAll().map { it.toDomain() }

    suspend fun record(task: Task, event: HistoryEvent, at: LocalDateTime, actualMinutes: Int? = task.actualMinutes, details: String = "") {
        dao.insert(
            ProductivityRecordEntity(
                taskId = task.id,
                taskTitle = task.title,
                area = task.area.name,
                event = event.name,
                at = LocalTimes.toEpochSecond(at)!!,
                estimatedMinutes = task.estimatedMinutes,
                actualMinutes = actualMinutes,
                details = details,
            ),
        )
    }
}

class SettingsRepository(private val db: DayflowDatabase) {
    private val dao = db.settingsDao()

    val settings: Flow<UserSettingsEntity> = dao.observe().map { it ?: UserSettingsEntity() }
    val restPeriods: Flow<List<RestPeriodEntity>> = dao.observeRestPeriods()
    val areaGoals: Flow<List<AreaGoalEntity>> = dao.observeAreaGoals()
    val daySettings: Flow<DaySettings> = combine(settings, restPeriods) { s, r -> s.toDaySettings(r) }

    suspend fun current(): UserSettingsEntity = dao.get() ?: UserSettingsEntity().also { dao.upsert(it) }
    suspend fun currentDaySettings(): DaySettings = current().toDaySettings(dao.restPeriods())

    suspend fun update(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        dao.upsert(transform(current()))
    }

    suspend fun addRestPeriod(label: String, start: Int, end: Int) = dao.insertRestPeriod(RestPeriodEntity(label = label, startMinute = start, endMinute = end))
    suspend fun deleteRestPeriod(id: Long) = dao.deleteRestPeriod(id)
    suspend fun setAreaGoal(area: LifeArea, weeklyMinutes: Int?) {
        if (weeklyMinutes == null || weeklyMinutes <= 0) dao.deleteAreaGoal(area.name)
        else dao.upsertAreaGoal(AreaGoalEntity(area.name, weeklyMinutes))
    }
}

private val Context.appStateStore by preferencesDataStore(name = "app_state")

/** Small, non-relational app state (DataStore). Relational user data lives in Room. */
class AppStateStore(private val context: Context) {
    private object Keys {
        val focusPhase = stringPreferencesKey("focus_phase")
        val focusEndsAt = longPreferencesKey("focus_ends_at")
        val focusPausedRemaining = longPreferencesKey("focus_paused_remaining")
        val focusTaskId = longPreferencesKey("focus_task_id")
        val focusCycle = intPreferencesKey("focus_cycle")
        val focusStartedAt = longPreferencesKey("focus_started_at")
        val focusPlannedMinutes = intPreferencesKey("focus_planned")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val permissionsIntroShown = booleanPreferencesKey("permissions_intro_shown")
    }

    data class FocusState(
        val phase: String = "IDLE", // IDLE, FOCUS, BREAK, LONG_BREAK
        val endsAt: Long = 0,
        val pausedRemainingMs: Long = 0,
        val taskId: Long = 0,
        val cycle: Int = 0,
        val startedAt: Long = 0,
        val plannedMinutes: Int = 0,
    ) {
        val isPaused get() = phase != "IDLE" && pausedRemainingMs > 0
    }

    val focusState: Flow<FocusState> = context.appStateStore.data.map { p ->
        FocusState(
            phase = p[Keys.focusPhase] ?: "IDLE",
            endsAt = p[Keys.focusEndsAt] ?: 0,
            pausedRemainingMs = p[Keys.focusPausedRemaining] ?: 0,
            taskId = p[Keys.focusTaskId] ?: 0,
            cycle = p[Keys.focusCycle] ?: 0,
            startedAt = p[Keys.focusStartedAt] ?: 0,
            plannedMinutes = p[Keys.focusPlannedMinutes] ?: 0,
        )
    }

    suspend fun setFocusState(s: FocusState) {
        context.appStateStore.edit { p ->
            p[Keys.focusPhase] = s.phase
            p[Keys.focusEndsAt] = s.endsAt
            p[Keys.focusPausedRemaining] = s.pausedRemainingMs
            p[Keys.focusTaskId] = s.taskId
            p[Keys.focusCycle] = s.cycle
            p[Keys.focusStartedAt] = s.startedAt
            p[Keys.focusPlannedMinutes] = s.plannedMinutes
        }
    }

    val lastBackupAt: Flow<Long> = context.appStateStore.data.map { it[Keys.lastBackupAt] ?: 0L }
    suspend fun setLastBackupAt(ms: Long) { context.appStateStore.edit { it[Keys.lastBackupAt] = ms } }

    suspend fun clearAll() { context.appStateStore.edit { it.clear() } }
}
