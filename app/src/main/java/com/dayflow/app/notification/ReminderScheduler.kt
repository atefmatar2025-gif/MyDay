package com.dayflow.app.notification

import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.AlarmLogDao
import com.dayflow.app.data.db.AlarmLogEntity
import com.dayflow.app.data.db.TaskReminderEntity
import com.dayflow.app.data.toDomain
import com.dayflow.core.model.Task
import com.dayflow.core.time.AlarmPlanner
import com.dayflow.core.time.LocalTimes
import com.dayflow.core.time.ReminderSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps AlarmManager in sync with the database. All public operations are idempotent:
 * every reminder owns one PendingIntent slot (request code = reminder id), so re-running a
 * schedule replaces the previous alarm instead of adding a duplicate.
 */
class ReminderScheduler(
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val log: AlarmLogDao,
    private val alarms: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()

    enum class Mode { ALARM, NOTIFICATION }

    data class FireDecision(
        val reminder: TaskReminderEntity,
        val task: Task,
        val ringing: Boolean,
    )

    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun now(): Instant = clock.instant()

    suspend fun rescheduleTask(taskId: Long) = mutex.withLock { rescheduleTaskLocked(taskId) }

    private suspend fun rescheduleTaskLocked(taskId: Long) {
        val reminders = tasks.reminders(taskId)
        reminders.forEach { alarms.cancelReminder(it.id) }
        val task = tasks.snapshot().firstOrNull { it.id == taskId }
        if (task == null) {
            reminders.forEach { tasks.updateReminder(it.copy(nextTriggerAt = null, nextOccurrence = null, attempts = 0)) }
            return
        }
        val specs = reminders.map { ReminderSpec(it.id, it.taskId, it.offsetMinutes, it.enabled) }
        val plans = AlarmPlanner.plansFor(task, specs, now(), zone()).associateBy { it.reminderId }
        for (r in reminders) {
            val plan = plans[r.id]
            if (plan == null) {
                val wasDuplicate = r.enabled && AlarmPlanner.nextTrigger(task, ReminderSpec(r.id, r.taskId, r.offsetMinutes), now(), zone()) != null
                tasks.updateReminder(r.copy(nextTriggerAt = null, nextOccurrence = null, attempts = 0))
                if (wasDuplicate) logAction(r, task.title, "SKIPPED_DUPLICATE", null, true)
                continue
            }
            val millis = plan.triggerAt.toEpochMilli()
            val exact = alarms.scheduleReminder(r.id, millis, ringing = r.mode == Mode.ALARM.name, snooze = false, taskId = task.id)
            tasks.updateReminder(
                r.copy(nextTriggerAt = millis, nextOccurrence = LocalTimes.toEpochSecond(plan.occurrence), attempts = 0),
            )
            logAction(r, task.title, if (exact) "SCHEDULED" else "INEXACT_FALLBACK", millis, exact)
        }
    }

    suspend fun cancelTask(taskId: Long) = mutex.withLock {
        val title = tasks.entity(taskId)?.title ?: ""
        tasks.reminders(taskId).forEach {
            alarms.cancelReminder(it.id)
            tasks.updateReminder(it.copy(nextTriggerAt = null, nextOccurrence = null, attempts = 0))
            logAction(it, title, "CANCELED", null, true)
        }
    }

    /** Re-registers everything: after boot, app update, time/zone change or permission change. */
    suspend fun rescheduleAll() = mutex.withLock {
        val all = tasks.allReminders().map { it.taskId }.distinct()
        all.forEach { rescheduleTaskLocked(it) }
        rescheduleHabitsLocked()
        log.deleteOlderThan(now().toEpochMilli() - 90L * 24 * 3600 * 1000) // keep 90 days of alarm history
    }

    /**
     * Called by [AlarmReceiver]. Returns null when the alarm must be ignored (task gone, closed,
     * reminder disabled, or a duplicate delivery of an alarm that already fired).
     */
    suspend fun onFired(reminderId: Long, scheduledFor: Long, isSnooze: Boolean): FireDecision? = mutex.withLock {
        val r = tasks.reminder(reminderId) ?: return@withLock null
        val task = tasks.snapshot().firstOrNull { it.id == r.taskId } ?: return@withLock null
        if (!r.enabled || !task.status.isOpen || task.archived) return@withLock null
        if (!isSnooze && scheduledFor > 0 && log.firedCount(reminderId, scheduledFor) > 0) {
            logAction(r, task.title, "SKIPPED_DUPLICATE", scheduledFor, true)
            return@withLock null
        }
        logAction(r, task.title, "FIRED", scheduledFor.takeIf { it > 0 }, alarms.canScheduleExact())
        if (!isSnooze) {
            // Register the next occurrence for recurring tasks (same slot; snooze slot is separate).
            rescheduleTaskLocked(task.id)
            tasks.reminder(reminderId)?.let { tasks.updateReminder(it.copy(attempts = 0)) }
        }
        FireDecision(tasks.reminder(reminderId) ?: r, task, ringing = r.mode == Mode.ALARM.name)
    }

    suspend fun snooze(reminderId: Long, minutes: Int) = mutex.withLock {
        val r = tasks.reminder(reminderId) ?: return@withLock
        val title = tasks.entity(r.taskId)?.title ?: ""
        val at = AlarmPlanner.snoozeUntil(now(), minutes).toEpochMilli()
        val exact = alarms.scheduleReminder(r.id, at, ringing = r.mode == Mode.ALARM.name, snooze = true, taskId = r.taskId)
        logAction(r, title, "SNOOZED", at, exact, details = "$minutes")
    }

    suspend fun dismissed(reminderId: Long) = mutex.withLock {
        val r = tasks.reminder(reminderId) ?: return@withLock
        tasks.updateReminder(r.copy(attempts = 0))
        logAction(r, tasks.entity(r.taskId)?.title ?: "", "DISMISSED", null, true)
    }

    /** The alarm rang for its full duration without an answer. */
    suspend fun unanswered(reminderId: Long) = mutex.withLock {
        val r = tasks.reminder(reminderId) ?: return@withLock
        val title = tasks.entity(r.taskId)?.title ?: ""
        val next = AlarmPlanner.repeatIfUnanswered(now(), r.repeatIntervalMinutes, r.attempts, r.repeatCount)
        if (next == null) {
            tasks.updateReminder(r.copy(attempts = 0))
            logAction(r, title, "MISSED", null, true)
        } else {
            tasks.updateReminder(r.copy(attempts = r.attempts + 1))
            val exact = alarms.scheduleReminder(r.id, next.toEpochMilli(), ringing = true, snooze = true, taskId = r.taskId)
            logAction(r, title, "REPEATED", next.toEpochMilli(), exact, details = "${r.attempts + 1}/${r.repeatCount}")
        }
    }

    // ---------------- habits ----------------

    suspend fun rescheduleHabits() = mutex.withLock { rescheduleHabitsLocked() }

    private suspend fun rescheduleHabitsLocked() {
        val now = LocalDateTime.ofInstant(now(), zone())
        for (h in habits.all()) {
            alarms.cancelHabit(h.id)
            val habit = h.toDomain()
            val time = habit.time ?: continue
            if (!h.reminderEnabled || h.archived) continue
            var d = now.toLocalDate()
            var guard = 0
            while (guard++ < 8) {
                if (habit.isScheduledOn(d) && d.atTime(time).isAfter(now)) {
                    alarms.scheduleHabit(h.id, AlarmPlanner.toInstant(d.atTime(time), zone()).toEpochMilli())
                    break
                }
                d = d.plusDays(1)
            }
        }
    }

    private suspend fun logAction(r: TaskReminderEntity, title: String, action: String, scheduledFor: Long?, exact: Boolean, details: String = "") {
        log.insert(
            AlarmLogEntity(
                reminderId = r.id, taskId = r.taskId, taskTitle = title, action = action,
                at = now().toEpochMilli(), scheduledFor = scheduledFor, exact = exact, details = details,
            ),
        )
    }
}
