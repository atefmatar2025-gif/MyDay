package com.dayflow.core.time

import com.dayflow.core.model.Task
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The scheduling-relevant part of a reminder. */
data class ReminderSpec(
    val id: Long,
    val taskId: Long,
    /** Minutes before the task's start (or deadline if it has no start). 0 = at the time. */
    val offsetMinutes: Int,
    val enabled: Boolean = true,
)

/** A concrete alarm to register with the OS. */
data class AlarmPlan(
    val reminderId: Long,
    val taskId: Long,
    /** The occurrence of the task this alarm belongs to (wall clock). */
    val occurrence: LocalDateTime,
    val triggerAt: Instant,
)

/**
 * Computes when reminders should fire.
 *
 * Design decision: task times are stored as wall-clock [LocalDateTime] ("8:00 in the morning"),
 * and converted to an [Instant] using the zone that is current when the alarm is scheduled.
 * After a time-zone change the app reschedules every alarm, so an 8:00 task still rings at
 * 8:00 local time in the new zone - the behaviour people expect from an alarm clock.
 *
 * Daylight-saving rules (java.time semantics):
 *  - a time inside a "spring forward" gap is moved forward by the gap length (02:30 -> 03:30);
 *  - a time inside a "fall back" overlap uses the earlier offset (rings once, the first time).
 */
object AlarmPlanner {

    fun toInstant(local: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.of(local, zone).toInstant()

    fun nextTrigger(task: Task, reminder: ReminderSpec, now: Instant, zone: ZoneId): AlarmPlan? {
        if (!reminder.enabled || task.archived || !task.status.isOpen) return null
        val base = task.startAt ?: task.dueAt ?: return null
        val offset = reminder.offsetMinutes.toLong().coerceAtLeast(0)

        fun planFor(occurrence: LocalDateTime) =
            AlarmPlan(reminder.id, task.id, occurrence, toInstant(occurrence.minusMinutes(offset), zone))

        val first = planFor(base)
        if (first.triggerAt.isAfter(now)) return first

        val rule = task.recurrence ?: return null
        // Earliest occurrence whose trigger (occurrence - offset) is after now.
        val localNow = LocalDateTime.ofInstant(now, zone)
        var after = localNow.plusMinutes(offset).minusSeconds(1)
        repeat(3) { // guard against DST edge cases where conversion lands at/before now
            val next = RecurrenceCalculator.nextOccurrence(rule, base, after) ?: return null
            val plan = planFor(next)
            if (plan.triggerAt.isAfter(now)) return plan
            after = next
        }
        return null
    }

    /** Plans for all reminders of a task, without duplicate trigger times. */
    fun plansFor(task: Task, reminders: List<ReminderSpec>, now: Instant, zone: ZoneId): List<AlarmPlan> =
        dedupe(reminders.mapNotNull { nextTrigger(task, it, now, zone) })

    /** Two reminders of the same task that would fire at the same instant fire only once. */
    fun dedupe(plans: List<AlarmPlan>): List<AlarmPlan> =
        plans.sortedWith(compareBy({ it.triggerAt }, { it.reminderId }))
            .distinctBy { it.taskId to it.triggerAt }

    fun snoozeUntil(now: Instant, minutes: Int): Instant = now.plusSeconds(minutes.coerceIn(1, 24 * 60) * 60L)

    /**
     * When an alarm is not answered it may ring again. Returns the next attempt time or null when
     * [attemptsSoFar] already reached [maxRepeats].
     */
    fun repeatIfUnanswered(firedAt: Instant, intervalMinutes: Int, attemptsSoFar: Int, maxRepeats: Int): Instant? {
        if (maxRepeats <= 0 || attemptsSoFar >= maxRepeats) return null
        return firedAt.plusSeconds(intervalMinutes.coerceAtLeast(1) * 60L)
    }

    /** Stable Android request code for a reminder's PendingIntent (one alarm slot per reminder). */
    fun requestCode(reminderId: Long, snooze: Boolean = false): Int {
        val base = (reminderId % 400_000_000L).toInt()
        return if (snooze) base + 1_000_000_000 else base
    }
}
