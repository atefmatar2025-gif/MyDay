package com.dayflow.app.domain

import android.content.Context
import com.dayflow.app.R
import com.dayflow.app.data.HistoryRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.DailyPlanEntity
import com.dayflow.app.data.db.DailyPlanItemEntity
import com.dayflow.app.data.db.DayflowDatabase
import com.dayflow.app.data.db.TaskAttachmentEntity
import com.dayflow.app.data.db.TaskEntity
import com.dayflow.app.data.db.TaskReminderEntity
import com.dayflow.app.notification.AlarmScheduler
import com.dayflow.app.notification.Channels
import com.dayflow.app.notification.Notifier
import com.dayflow.app.notification.ReminderScheduler
import com.dayflow.app.widget.DayflowWidgetProvider
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.Priority
import com.dayflow.core.model.RecurrenceCodec
import com.dayflow.core.model.RecurrenceRule
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.scheduling.DayPlan
import com.dayflow.core.time.LocalTimes
import com.dayflow.core.time.RecurrenceCalculator
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

data class ReminderDraft(
    val id: Long = 0,
    val offsetMinutes: Int = 10,
    val mode: ReminderScheduler.Mode = ReminderScheduler.Mode.ALARM,
    val soundUri: String? = null,
    val volumePercent: Int = 100,
    val vibrate: Boolean = true,
    val ringSeconds: Int = 60,
    val repeatCount: Int = 2,
    val repeatIntervalMinutes: Int = 5,
    val enabled: Boolean = true,
)

data class AttachmentDraft(val id: Long = 0, val uri: String, val displayName: String, val mimeType: String?)

/** Everything the task editor can change. */
data class TaskDraft(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
    val startAt: LocalDateTime? = null,
    val dueAt: LocalDateTime? = null,
    val estimatedMinutes: Int? = null,
    val fixedTime: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    val parentId: Long? = null,
    val dependsOn: List<Long> = emptyList(),
    val reminders: List<ReminderDraft> = emptyList(),
    val attachments: List<AttachmentDraft> = emptyList(),
)

class ValidationException(message: String) : Exception(message)

/**
 * Application use cases for tasks. Every state change goes through here so that reminders,
 * the immutable history ("My Productivity Memory") and the widget always stay consistent.
 */
class TaskInteractor(
    private val context: Context,
    private val db: DayflowDatabase,
    private val tasks: TaskRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val alarmScheduler: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private fun nowLocal(): LocalDateTime = LocalDateTime.now(clock)
    private fun nowMs(): Long = clock.millis()

    suspend fun loadDraft(id: Long): TaskDraft? {
        val e = tasks.entity(id) ?: return null
        val domain = tasks.snapshot().firstOrNull { it.id == id } ?: return null
        return TaskDraft(
            id = e.id, title = e.title, description = e.description, notes = e.notes,
            priority = domain.priority, categoryId = e.categoryId, tags = domain.tags,
            startAt = domain.startAt, dueAt = domain.dueAt, estimatedMinutes = e.estimatedMinutes,
            fixedTime = e.fixedTime, recurrence = domain.recurrence, parentId = e.parentId, dependsOn = domain.dependsOn,
            reminders = tasks.reminders(id).map {
                ReminderDraft(
                    it.id, it.offsetMinutes, ReminderScheduler.Mode.valueOf(it.mode), it.soundUri, it.volumePercent,
                    it.vibrate, it.ringSeconds, it.repeatCount, it.repeatIntervalMinutes, it.enabled,
                )
            },
            attachments = tasks.attachments(id).map { AttachmentDraft(it.id, it.uri, it.displayName, it.mimeType) },
        )
    }

    suspend fun newDraftDefaults(): ReminderDraft {
        val s = settings.current()
        return ReminderDraft(
            offsetMinutes = s.defaultReminderOffset,
            mode = runCatching { ReminderScheduler.Mode.valueOf(s.defaultReminderMode) }.getOrDefault(ReminderScheduler.Mode.ALARM),
            ringSeconds = s.ringSeconds, repeatCount = s.repeatCount, repeatIntervalMinutes = s.repeatIntervalMinutes,
            volumePercent = 100,
        )
    }

    /** Creates or updates a task. Returns its id. */
    suspend fun save(draft: TaskDraft): Long {
        val title = draft.title.trim()
        if (title.isEmpty()) throw ValidationException(context.getString(R.string.error_title_required))
        if (draft.startAt != null && draft.dueAt != null && draft.dueAt.isBefore(draft.startAt)) {
            throw ValidationException(context.getString(R.string.error_due_before_start))
        }
        if (draft.reminders.any { it.enabled } && draft.startAt == null && draft.dueAt == null) {
            throw ValidationException(context.getString(R.string.error_reminder_needs_time))
        }
        if (draft.recurrence != null && draft.startAt == null && draft.dueAt == null) {
            throw ValidationException(context.getString(R.string.error_recurrence_needs_time))
        }
        val now = nowMs()
        val existing = if (draft.id != 0L) tasks.entity(draft.id) else null
        if (draft.id != 0L) validateGraph(draft)
        val startSec = LocalTimes.toEpochSecond(draft.startAt?.withSecond(0)?.withNano(0))
        val dueSec = LocalTimes.toEpochSecond(draft.dueAt?.withSecond(0)?.withNano(0))
        val status = when {
            existing != null && !TaskStatus.fromName(existing.status).isOpen -> existing.status
            existing != null && existing.status == TaskStatus.IN_PROGRESS.name -> existing.status
            draft.startAt != null -> TaskStatus.SCHEDULED.name
            else -> existing?.status?.takeIf { it != TaskStatus.SCHEDULED.name } ?: TaskStatus.NEW.name
        }
        val entity = TaskEntity(
            id = existing?.id ?: 0,
            title = title,
            description = draft.description.trim(),
            notes = draft.notes.trim(),
            priority = draft.priority.name,
            status = status,
            categoryId = draft.categoryId,
            startAt = startSec,
            dueAt = dueSec,
            estimatedMinutes = draft.estimatedMinutes?.takeIf { it > 0 },
            actualMinutes = existing?.actualMinutes,
            parentId = draft.parentId?.takeIf { it != draft.id },
            fixedTime = draft.fixedTime,
            recurrence = RecurrenceCodec.encode(draft.recurrence),
            postponeCount = existing?.postponeCount ?: 0,
            archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            completedAt = existing?.completedAt,
            startedAt = existing?.startedAt,
        )
        val id = if (existing == null) tasks.insert(entity) else { tasks.update(entity); entity.id }
        tasks.setTags(id, draft.tags)
        tasks.setDependencies(id, draft.dependsOn)
        saveReminders(id, draft.reminders)
        saveAttachments(id, draft.attachments)

        val domain = tasks.snapshot().first { it.id == id }
        when {
            existing == null -> history.record(domain, HistoryEvent.CREATED, nowLocal())
            existing.startAt != startSec || existing.dueAt != dueSec ->
                history.record(domain, HistoryEvent.RESCHEDULED, nowLocal())
        }
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
        return id
    }

    private suspend fun validateGraph(draft: TaskDraft) {
        val all = tasks.snapshot()
        // A task cannot depend on itself, or on a task that (directly or indirectly) depends on it.
        fun dependsTransitively(from: Long, target: Long, seen: MutableSet<Long> = mutableSetOf()): Boolean {
            if (!seen.add(from)) return false
            val deps = all.firstOrNull { it.id == from }?.dependsOn ?: return false
            return deps.any { it == target || dependsTransitively(it, target, seen) }
        }
        if (draft.dependsOn.any { it == draft.id || dependsTransitively(it, draft.id) }) {
            throw ValidationException(context.getString(R.string.error_dependency_cycle))
        }
        var p = draft.parentId
        var guard = 0
        while (p != null && guard++ < 100) {
            if (p == draft.id) throw ValidationException(context.getString(R.string.error_parent_cycle))
            p = all.firstOrNull { it.id == p }?.parentId
        }
    }

    private suspend fun saveReminders(taskId: Long, drafts: List<ReminderDraft>) {
        val existing = tasks.reminders(taskId)
        val keep = drafts.map { it.id }.toSet()
        // Removed reminders: cancel their OS alarm first (rescheduleTask only sees rows that still exist).
        existing.filter { it.id !in keep }.forEach {
            alarmScheduler.cancelReminder(it.id)
            tasks.deleteReminder(it)
        }
        for (d in drafts) {
            val e = TaskReminderEntity(
                id = d.id, taskId = taskId, offsetMinutes = d.offsetMinutes.coerceIn(0, 7 * 24 * 60), mode = d.mode.name,
                soundUri = d.soundUri, volumePercent = d.volumePercent.coerceIn(0, 100), vibrate = d.vibrate,
                ringSeconds = d.ringSeconds.coerceIn(10, 600), repeatCount = d.repeatCount.coerceIn(0, 10),
                repeatIntervalMinutes = d.repeatIntervalMinutes.coerceIn(1, 60), enabled = d.enabled,
            )
            if (d.id == 0L || existing.none { it.id == d.id }) tasks.insertReminder(e.copy(id = 0)) else tasks.updateReminder(e)
        }
    }

    private suspend fun saveAttachments(taskId: Long, drafts: List<AttachmentDraft>) {
        val existing = tasks.attachments(taskId)
        val keep = drafts.map { it.id }.toSet()
        existing.filter { it.id !in keep }.forEach { tasks.removeAttachment(it.id) }
        drafts.filter { it.id == 0L }.forEach {
            tasks.addAttachment(TaskAttachmentEntity(taskId = taskId, uri = it.uri, displayName = it.displayName, mimeType = it.mimeType))
        }
    }

    private suspend fun domain(id: Long): Task? = tasks.snapshot().firstOrNull { it.id == id }

    suspend fun start(id: Long) {
        val e = tasks.entity(id) ?: return
        tasks.update(e.copy(status = TaskStatus.IN_PROGRESS.name, startedAt = nowMs(), updatedAt = nowMs()))
        domain(id)?.let { history.record(it, HistoryEvent.STARTED, nowLocal()) }
        DayflowWidgetProvider.refresh(context)
    }

    /** Adds focused minutes to the task's actual time (from Focus Mode). */
    suspend fun addActualMinutes(id: Long, minutes: Int) {
        val e = tasks.entity(id) ?: return
        // Restart the "started" clock so complete() does not count the focused minutes twice.
        tasks.update(
            e.copy(
                actualMinutes = (e.actualMinutes ?: 0) + minutes,
                startedAt = if (e.startedAt != null) nowMs() else null,
                updatedAt = nowMs(),
            ),
        )
    }

    suspend fun complete(id: Long) {
        val e = tasks.entity(id) ?: return
        val task = domain(id) ?: return
        if (!task.status.isOpen) return
        val sessionMinutes = e.startedAt?.let { Duration.ofMillis(nowMs() - it).toMinutes().toInt().coerceIn(0, 12 * 60) } ?: 0
        val actual = ((e.actualMinutes ?: 0) + sessionMinutes).takeIf { it > 0 }
        history.record(task, HistoryEvent.COMPLETED, nowLocal(), actualMinutes = actual)

        val rule = task.recurrence
        val anchor = task.startAt ?: task.dueAt
        if (rule != null && anchor != null) {
            // Recurring: the series continues - move the row to its next occurrence.
            val currentOcc = TaskOccurrences.forDate(listOf(task), nowLocal().toLocalDate()).firstOrNull()?.let { it.startAt ?: it.dueAt }
                ?: anchor
            val next = RecurrenceCalculator.nextOccurrence(rule, anchor, maxOf(currentOcc, nowLocal()).withSecond(59))
            if (next != null) {
                val shift = Duration.between(anchor, next)
                tasks.update(
                    e.copy(
                        startAt = task.startAt?.plus(shift)?.let { LocalTimes.toEpochSecond(it) },
                        dueAt = task.dueAt?.plus(shift)?.let { LocalTimes.toEpochSecond(it) },
                        status = TaskStatus.SCHEDULED.name, actualMinutes = null, startedAt = null,
                        postponeCount = 0, updatedAt = nowMs(),
                    ),
                )
            } else {
                tasks.update(e.copy(status = TaskStatus.COMPLETED.name, completedAt = nowMs(), actualMinutes = actual, startedAt = null, updatedAt = nowMs()))
            }
        } else {
            tasks.update(e.copy(status = TaskStatus.COMPLETED.name, completedAt = nowMs(), actualMinutes = actual, startedAt = null, updatedAt = nowMs()))
        }
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
        maybeCelebrate()
    }

    private suspend fun maybeCelebrate() {
        if (!settings.current().completionNotifications) return
        val today = nowLocal().toLocalDate()
        val todays = TaskOccurrences.forDate(tasks.snapshot(), today)
        if (todays.isNotEmpty() && todays.none { it.status.isOpen }) {
            Notifier.simple(
                context, Channels.ACHIEVEMENTS, Notifier.ID_ACHIEVEMENT,
                context.getString(R.string.notif_all_done_title), context.getString(R.string.notif_all_done_text),
            )
        }
    }

    suspend fun reopen(id: Long) {
        val e = tasks.entity(id) ?: return
        tasks.update(e.copy(status = if (e.startAt != null) TaskStatus.SCHEDULED.name else TaskStatus.NEW.name, completedAt = null, updatedAt = nowMs()))
        domain(id)?.let { history.record(it, HistoryEvent.REOPENED, nowLocal()) }
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
    }

    /** Moves start and deadline together to [newStart] and counts a postponement. */
    suspend fun postpone(id: Long, newStart: LocalDateTime) {
        val e = tasks.entity(id) ?: return
        val task = domain(id) ?: return
        val base = task.startAt ?: task.dueAt
        val shift = if (base != null) Duration.between(base, newStart) else Duration.ZERO
        tasks.update(
            e.copy(
                startAt = (task.startAt?.plus(shift) ?: if (base == null) newStart else null)?.let { LocalTimes.toEpochSecond(it) },
                dueAt = task.dueAt?.plus(shift)?.let { LocalTimes.toEpochSecond(it) },
                status = TaskStatus.POSTPONED.name, postponeCount = e.postponeCount + 1, updatedAt = nowMs(),
            ),
        )
        history.record(task, HistoryEvent.POSTPONED, nowLocal(), details = newStart.toString())
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
    }

    /** Moves a task in the timeline (not counted as a postponement). */
    suspend fun moveTo(id: Long, newStart: LocalDateTime) {
        val e = tasks.entity(id) ?: return
        val task = domain(id) ?: return
        val shift = task.startAt?.let { Duration.between(it, newStart) } ?: Duration.ZERO
        tasks.update(
            e.copy(
                startAt = LocalTimes.toEpochSecond(newStart),
                dueAt = task.dueAt?.plus(shift)?.let { LocalTimes.toEpochSecond(it) },
                status = if (e.status == TaskStatus.NEW.name) TaskStatus.SCHEDULED.name else e.status,
                updatedAt = nowMs(),
            ),
        )
        history.record(task, HistoryEvent.RESCHEDULED, nowLocal())
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
    }

    suspend fun cancel(id: Long) {
        val e = tasks.entity(id) ?: return
        val task = domain(id) ?: return
        tasks.update(e.copy(status = TaskStatus.CANCELLED.name, updatedAt = nowMs()))
        history.record(task, HistoryEvent.CANCELLED, nowLocal())
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val e = tasks.entity(id) ?: return
        tasks.update(e.copy(archived = archived, updatedAt = nowMs()))
        reminders.rescheduleTask(id)
        DayflowWidgetProvider.refresh(context)
    }

    suspend fun duplicate(id: Long): Long? {
        val draft = loadDraft(id) ?: return null
        return save(
            draft.copy(
                id = 0,
                title = context.getString(R.string.copy_suffix, draft.title),
                reminders = draft.reminders.map { it.copy(id = 0) },
                attachments = draft.attachments.map { it.copy(id = 0) },
            ),
        )
    }

    suspend fun delete(id: Long) {
        val task = domain(id) ?: return
        reminders.cancelTask(id) // cancel alarms BEFORE the rows disappear
        history.record(task, HistoryEvent.DELETED, nowLocal())
        tasks.delete(id)
        DayflowWidgetProvider.refresh(context)
    }

    /**
     * Applies an approved plan. Only called after the user reviewed it and pressed "apply".
     * - flexible one-off tasks get the planned start time;
     * - a task split into parts becomes a parent with one subtask per part;
     * - postponement suggestions the user accepted move to the next day (same time, or day start);
     * - recurring tasks and at-risk tasks are left unchanged.
     */
    suspend fun applyPlan(plan: DayPlan, acceptedPostpones: Set<Long>) {
        val s = settings.current()
        val itemsForRecord = mutableListOf<DailyPlanItemEntity>()
        val byTask = plan.blocks.groupBy { it.task.id }
        for ((taskId, blocks) in byTask) {
            val task = blocks.first().task
            itemsForRecord += blocks.map {
                DailyPlanItemEntity(
                    planId = 0, taskId = taskId, taskTitle = task.title, kind = "BLOCK",
                    startAt = LocalTimes.toEpochSecond(it.start), endAt = LocalTimes.toEpochSecond(it.end),
                    part = it.part, totalParts = it.totalParts,
                )
            }
            if (task.recurrence != null) continue
            if (blocks.size == 1) {
                if (task.startAt != blocks[0].start) moveTo(taskId, blocks[0].start)
            } else {
                val parent = tasks.entity(taskId) ?: continue
                tasks.update(parent.copy(startAt = null, status = TaskStatus.SCHEDULED.name, updatedAt = nowMs()))
                blocks.forEach { b ->
                    save(
                        TaskDraft(
                            title = context.getString(R.string.part_title, task.title, b.part, b.totalParts),
                            priority = task.priority, categoryId = task.categoryId, startAt = b.start,
                            dueAt = task.dueAt, estimatedMinutes = b.minutes.toInt(), parentId = taskId,
                            reminders = if (tasks.reminders(taskId).isNotEmpty()) listOf(newDraftDefaults().copy(offsetMinutes = 0)) else emptyList(),
                        ),
                    )
                }
                reminders.rescheduleTask(taskId)
            }
        }
        for (t in plan.suggestPostpone) {
            itemsForRecord += DailyPlanItemEntity(planId = 0, taskId = t.id, taskTitle = t.title, kind = "POSTPONE")
            if (t.id !in acceptedPostpones || t.recurrence != null) continue
            val time = t.startAt?.toLocalTime() ?: com.dayflow.app.data.minuteToTime(s.dayStartMinute)
            postpone(t.id, plan.date.plusDays(1).atTime(time))
        }
        plan.atRisk.forEach { itemsForRecord += DailyPlanItemEntity(planId = 0, taskId = it.id, taskTitle = it.title, kind = "AT_RISK") }
        plan.blocked.forEach { itemsForRecord += DailyPlanItemEntity(planId = 0, taskId = it.id, taskTitle = it.title, kind = "BLOCKED") }
        db.planDao().insertWithItems(
            DailyPlanEntity(
                date = plan.date.toEpochDay(), createdAt = nowMs(), appliedAt = nowMs(), summary = plan.summary(),
                freeMinutes = plan.freeMinutes, requiredMinutes = plan.requiredMinutes,
            ),
            itemsForRecord,
        )
        DayflowWidgetProvider.refresh(context)
    }
}
