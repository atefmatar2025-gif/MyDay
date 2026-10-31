package com.dayflow.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayflow.app.domain.ReminderDraft
import com.dayflow.app.domain.TaskDraft
import com.dayflow.app.domain.ValidationException
import com.dayflow.core.model.Frequency
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.Priority
import com.dayflow.core.model.RecurrenceRule
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.scheduling.DayPlanner
import com.dayflow.core.model.DaySettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone

/**
 * Runs on a device/emulator (`./gradlew connectedDebugAndroidTest`). Uses the real AlarmManager.
 * Exact-alarm permission state of the device decides whether alarms are exact or the inexact
 * fallback; both paths must register an alarm.
 */
@RunWith(AndroidJUnit4::class)
class TaskAndAlarmInstrumentedTest {
    private lateinit var g: TestGraph
    private val tomorrow9: LocalDateTime = LocalDate.now().plusDays(1).atTime(9, 0)

    @Before fun setUp() { g = TestGraph() }

    @After fun tearDown() = runTest { g.cleanup() }

    private fun draft(title: String = "مراجعة البحث", start: LocalDateTime? = tomorrow9, reminders: List<ReminderDraft> = listOf(ReminderDraft(offsetMinutes = 10))) =
        TaskDraft(title = title, startAt = start, estimatedMinutes = 60, priority = Priority.HIGH, reminders = reminders, tags = listOf("دراسة"))

    @Test fun addTaskStoresEverythingAndSchedulesAlarm() = runTest {
        val id = g.interactor.save(draft())
        val task = g.tasks.snapshot().single { it.id == id }
        assertEquals(TaskStatus.SCHEDULED, task.status)
        assertEquals(listOf("دراسة"), task.tags)
        val r = g.tasks.reminders(id).single()
        assertNotNull(r.nextTriggerAt)
        assertTrue(g.alarms.isReminderScheduled(r.id))
        assertEquals(HistoryEvent.CREATED, g.history.snapshot().single().event)
    }

    @Test fun validationRejectsBadInput() = runTest {
        try { g.interactor.save(draft(title = "  ")); fail() } catch (e: ValidationException) { }
        try { g.interactor.save(draft(start = null)); fail() } catch (e: ValidationException) { }
        try {
            g.interactor.save(draft().copy(dueAt = tomorrow9.minusHours(1))); fail()
        } catch (e: ValidationException) { }
    }

    @Test fun editingTimeReschedulesAlarm() = runTest {
        val id = g.interactor.save(draft())
        val before = g.tasks.reminders(id).single().nextTriggerAt
        val d = g.interactor.loadDraft(id)!!
        g.interactor.save(d.copy(startAt = tomorrow9.plusHours(3)))
        val after = g.tasks.reminders(id).single().nextTriggerAt
        assertNotEquals(before, after)
        assertEquals(3 * 3600 * 1000L, after!! - before!!)
        assertTrue(g.history.snapshot().any { it.event == HistoryEvent.RESCHEDULED })
    }

    @Test fun removingReminderCancelsItsAlarm() = runTest {
        val id = g.interactor.save(draft(reminders = listOf(ReminderDraft(offsetMinutes = 10), ReminderDraft(offsetMinutes = 30))))
        val rs = g.tasks.reminders(id)
        assertEquals(2, rs.size)
        val d = g.interactor.loadDraft(id)!!
        g.interactor.save(d.copy(reminders = d.reminders.take(1)))
        val removed = rs.first { r -> g.tasks.reminders(id).none { it.id == r.id } }
        assertFalse(g.alarms.isReminderScheduled(removed.id))
    }

    @Test fun deletingTaskCancelsAlarmsButKeepsHistory() = runTest {
        val id = g.interactor.save(draft())
        val rid = g.tasks.reminders(id).single().id
        g.interactor.delete(id)
        assertFalse(g.alarms.isReminderScheduled(rid))
        assertTrue(g.tasks.reminders(id).isEmpty()) // cascade
        assertTrue(g.tasks.snapshot().none { it.id == id })
        val events = g.history.snapshot().map { it.event }
        assertTrue(HistoryEvent.CREATED in events && HistoryEvent.DELETED in events)
    }

    @Test fun completingOneOffTaskCancelsAlarm() = runTest {
        val id = g.interactor.save(draft())
        val rid = g.tasks.reminders(id).single().id
        g.interactor.complete(id)
        assertEquals(TaskStatus.COMPLETED, g.tasks.snapshot().single { it.id == id }.status)
        assertNull(g.tasks.reminder(rid)!!.nextTriggerAt)
        assertFalse(g.alarms.isReminderScheduled(rid))
    }

    @Test fun completingRecurringTaskMovesToNextOccurrence() = runTest {
        val start = LocalDate.now().atTime(LocalTime.of(23, 59)).minusDays(0)
        val id = g.interactor.save(draft(start = start).copy(recurrence = RecurrenceRule(Frequency.DAILY)))
        g.interactor.complete(id)
        val t = g.tasks.snapshot().single { it.id == id }
        assertEquals(TaskStatus.SCHEDULED, t.status)
        assertEquals(start.plusDays(1), t.startAt)
        assertTrue(g.alarms.isReminderScheduled(g.tasks.reminders(id).single().id))
    }

    @Test fun postponeCountsAndShifts() = runTest {
        val id = g.interactor.save(draft())
        g.interactor.postpone(id, tomorrow9.plusDays(1))
        val t = g.tasks.snapshot().single { it.id == id }
        assertEquals(1, t.postponeCount)
        assertEquals(TaskStatus.POSTPONED, t.status)
        assertEquals(tomorrow9.plusDays(1), t.startAt)
    }

    @Test fun cancelAndArchiveStopAlarms() = runTest {
        val a = g.interactor.save(draft())
        val b = g.interactor.save(draft(title = "ب"))
        g.interactor.cancel(a)
        g.interactor.setArchived(b, true)
        assertFalse(g.alarms.isReminderScheduled(g.tasks.reminders(a).single().id))
        assertFalse(g.alarms.isReminderScheduled(g.tasks.reminders(b).single().id))
    }

    @Test fun duplicateCopiesRemindersAndTags() = runTest {
        val id = g.interactor.save(draft())
        val copy = g.interactor.duplicate(id)!!
        assertEquals(1, g.tasks.reminders(copy).size)
        assertEquals(listOf("دراسة"), g.tasks.snapshot().single { it.id == copy }.tags)
    }

    @Test fun rebootRescheduleRestoresAlarms() = runTest {
        val id = g.interactor.save(draft())
        val rid = g.tasks.reminders(id).single().id
        g.alarms.cancelReminder(rid) // what a reboot does to AlarmManager
        assertFalse(g.alarms.isReminderScheduled(rid))
        g.scheduler.rescheduleAll() // what SystemEventsReceiver runs on BOOT_COMPLETED
        assertTrue(g.alarms.isReminderScheduled(rid))
    }

    @Test fun rescheduleIsIdempotent() = runTest {
        val id = g.interactor.save(draft())
        val before = g.tasks.reminders(id).single()
        repeat(3) { g.scheduler.rescheduleAll() }
        val after = g.tasks.reminders(id).single()
        assertEquals(before.nextTriggerAt, after.nextTriggerAt)
    }

    @Test fun timeZoneChangeKeepsLocalWallClock() = runTest {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"))
            val id = g.interactor.save(draft())
            val riyadh = g.tasks.reminders(id).single().nextTriggerAt!!
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            g.scheduler.rescheduleAll() // what TIMEZONE_CHANGED triggers
            val london = g.tasks.reminders(id).single().nextTriggerAt!!
            assertNotEquals(riyadh, london)
            val local = java.time.Instant.ofEpochMilli(london).atZone(java.time.ZoneId.of("Europe/London")).toLocalDateTime()
            assertEquals(tomorrow9.minusMinutes(10), local)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun duplicateDeliveryIsIgnored() = runTest {
        val id = g.interactor.save(draft(start = LocalDateTime.now().plusMinutes(1), reminders = listOf(ReminderDraft(offsetMinutes = 0))))
        val r = g.tasks.reminders(id).single()
        val first = g.scheduler.onFired(r.id, r.nextTriggerAt!!, false)
        val second = g.scheduler.onFired(r.id, r.nextTriggerAt!!, false)
        assertNotNull(first)
        assertNull(second)
    }

    @Test fun firedAlarmForDeletedTaskIsIgnored() = runTest {
        val id = g.interactor.save(draft())
        val r = g.tasks.reminders(id).single()
        g.interactor.delete(id)
        assertNull(g.scheduler.onFired(r.id, r.nextTriggerAt!!, false))
    }

    @Test fun applyingPlanSplitsLongTasksIntoSubtasks() = runTest {
        val today = LocalDate.now().plusDays(1)
        val id = g.interactor.save(TaskDraft(title = "مشروع طويل", startAt = today.atTime(8, 0), estimatedMinutes = 180))
        val plan = DayPlanner.plan(g.tasks.snapshot(), today, LocalDateTime.now(), DaySettings(dayStart = LocalTime.of(8, 0), dayEnd = LocalTime.of(20, 0), bufferMinutes = 0))
        assertTrue(plan.blocks.size >= 2)
        g.interactor.applyPlan(plan, emptySet())
        val children = g.tasks.snapshot().filter { it.parentId == id }
        assertEquals(plan.blocks.size, children.size)
        assertEquals(180, children.sumOf { it.estimatedMinutes ?: 0 })
    }

    @Test fun manyTasksStayResponsive() = runTest {
        repeat(300) { i -> g.interactor.save(TaskDraft(title = "مهمة $i", startAt = tomorrow9.plusMinutes(i.toLong()), estimatedMinutes = 15)) }
        val all = g.tasks.snapshot()
        assertEquals(300, all.size)
        val plan = DayPlanner.plan(all, tomorrow9.toLocalDate(), LocalDateTime.now(), DaySettings())
        assertEquals(300, plan.blocks.map { it.task.id }.distinct().size + plan.suggestPostpone.size + plan.atRisk.size)
    }
}
