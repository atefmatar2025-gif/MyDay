package com.dayflow.core

import com.dayflow.core.TestData.TODAY
import com.dayflow.core.TestData.at
import com.dayflow.core.TestData.task
import com.dayflow.core.model.Frequency
import com.dayflow.core.model.RecurrenceCodec
import com.dayflow.core.model.RecurrenceRule
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.time.AlarmPlanner
import com.dayflow.core.time.RecurrenceCalculator
import com.dayflow.core.time.ReminderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceAndAlarmTest {

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val newYork = ZoneId.of("America/New_York")

    private fun instant(ldt: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.of(ldt, zone).toInstant()

    // ---------------- recurrence ----------------

    @Test fun codecRoundTrip() {
        val rule = RecurrenceRule(
            Frequency.WEEKLY, 2, setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
            setOf(LocalDate.of(2026, 11, 10)), LocalDate.of(2027, 1, 1),
        )
        assertEquals(rule, RecurrenceCodec.decode(RecurrenceCodec.encode(rule)))
        assertNull(RecurrenceCodec.decode("garbage"))
        assertNull(RecurrenceCodec.decode(null))
    }

    @Test fun dailyEveryTwoDays() {
        val rule = RecurrenceRule(Frequency.DAILY, interval = 2)
        val dates = RecurrenceCalculator.occurrencesBetween(rule, TODAY, TODAY, TODAY.plusDays(6))
        assertEquals(listOf(0L, 2L, 4L, 6L).map { TODAY.plusDays(it) }, dates)
    }

    @Test fun weeklySpecificDaysWithException() {
        val rule = RecurrenceRule(
            Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            exceptions = setOf(TODAY.plusDays(3)), // Thursday skipped
        )
        val dates = RecurrenceCalculator.occurrencesBetween(rule, TODAY, TODAY, TODAY.plusDays(13))
        assertEquals(listOf(TODAY, TODAY.plusDays(7), TODAY.plusDays(10)), dates)
    }

    @Test fun monthlyOn31stClampsToMonthEnd() {
        val anchor = LocalDate.of(2026, 1, 31)
        val rule = RecurrenceRule(Frequency.MONTHLY)
        assertTrue(RecurrenceCalculator.isOccurrence(rule, anchor, LocalDate.of(2026, 2, 28)))
        assertTrue(RecurrenceCalculator.isOccurrence(rule, anchor, LocalDate.of(2026, 4, 30)))
        assertFalse(RecurrenceCalculator.isOccurrence(rule, anchor, LocalDate.of(2026, 3, 30)))
    }

    @Test fun untilStopsRecurrence() {
        val rule = RecurrenceRule(Frequency.DAILY, until = TODAY.plusDays(1))
        val next = RecurrenceCalculator.nextOccurrence(rule, at(8), at(9, date = TODAY.plusDays(1)))
        assertNull(next)
    }

    @Test fun yearlyOccasion() {
        val rule = RecurrenceRule(Frequency.YEARLY)
        val next = RecurrenceCalculator.nextOccurrence(rule, LocalDateTime.of(2020, 3, 15, 9, 0), at(10))
        assertEquals(LocalDateTime.of(2027, 3, 15, 9, 0), next)
    }

    // ---------------- alarm planning ----------------

    @Test fun reminderBeforeStart() {
        val t = task(1, start = at(10))
        val plan = AlarmPlanner.nextTrigger(t, ReminderSpec(5, 1, 15), instant(at(8), riyadh), riyadh)!!
        assertEquals(instant(at(9, 45), riyadh), plan.triggerAt)
    }

    @Test fun pastReminderOfOneOffTaskIsNotScheduled() {
        val t = task(1, start = at(10))
        assertNull(AlarmPlanner.nextTrigger(t, ReminderSpec(5, 1, 0), instant(at(11), riyadh), riyadh))
    }

    @Test fun completedOrCancelledTaskHasNoAlarm() {
        val now = instant(at(8), riyadh)
        assertNull(AlarmPlanner.nextTrigger(task(1, start = at(10), status = TaskStatus.COMPLETED), ReminderSpec(1, 1, 0), now, riyadh))
        assertNull(AlarmPlanner.nextTrigger(task(1, start = at(10), status = TaskStatus.CANCELLED), ReminderSpec(1, 1, 0), now, riyadh))
        assertNull(AlarmPlanner.nextTrigger(task(1, start = at(10)), ReminderSpec(1, 1, 0, enabled = false), now, riyadh))
    }

    @Test fun recurringReminderMovesToNextOccurrence() {
        val t = task(1, start = at(7)).copy(recurrence = RecurrenceRule(Frequency.DAILY))
        val plan = AlarmPlanner.nextTrigger(t, ReminderSpec(1, 1, 10), instant(at(7, 30), riyadh), riyadh)!!
        assertEquals(at(7, date = TODAY.plusDays(1)), plan.occurrence)
        assertEquals(instant(at(6, 50, TODAY.plusDays(1)), riyadh), plan.triggerAt)
    }

    @Test fun rescheduleAfterTaskTimeChange() {
        val now = instant(at(8), riyadh)
        val r = ReminderSpec(1, 1, 0)
        val before = AlarmPlanner.nextTrigger(task(1, start = at(10)), r, now, riyadh)!!
        val after = AlarmPlanner.nextTrigger(task(1, start = at(16)), r, now, riyadh)!!
        assertNotEquals(before.triggerAt, after.triggerAt)
        assertEquals(instant(at(16), riyadh), after.triggerAt)
    }

    @Test fun timezoneChangeKeepsWallClockTime() {
        val t = task(1, start = at(8, date = TODAY.plusDays(1)))
        val r = ReminderSpec(1, 1, 0)
        val inRiyadh = AlarmPlanner.nextTrigger(t, r, instant(at(12), riyadh), riyadh)!!
        val inNy = AlarmPlanner.nextTrigger(t, r, instant(at(12), riyadh), newYork)!!
        assertEquals(8, ZonedDateTime.ofInstant(inRiyadh.triggerAt, riyadh).hour)
        assertEquals(8, ZonedDateTime.ofInstant(inNy.triggerAt, newYork).hour)
        assertNotEquals(inRiyadh.triggerAt, inNy.triggerAt)
    }

    @Test fun dstGapMovesForward() {
        // 2027-03-14 02:30 does not exist in New York (clocks jump 02:00 -> 03:00).
        val day = LocalDate.of(2027, 3, 14)
        val t = task(1, start = day.atTime(2, 30))
        val plan = AlarmPlanner.nextTrigger(t, ReminderSpec(1, 1, 0), instant(day.minusDays(1).atTime(12, 0), newYork), newYork)!!
        val local = ZonedDateTime.ofInstant(plan.triggerAt, newYork)
        assertEquals(3, local.hour)
        assertEquals(30, local.minute)
    }

    @Test fun dstOverlapRingsOnceAtEarlierOffset() {
        // 2026-11-01 01:30 happens twice in New York; we use the first one.
        val day = LocalDate.of(2026, 11, 1)
        val t = task(1, start = day.atTime(1, 30))
        val plan = AlarmPlanner.nextTrigger(t, ReminderSpec(1, 1, 0), instant(day.minusDays(1).atTime(12, 0), newYork), newYork)!!
        val first = ZonedDateTime.of(day.atTime(1, 30), newYork).withEarlierOffsetAtOverlap().toInstant()
        assertEquals(first, plan.triggerAt)
    }

    @Test fun dailyAlarmAcrossDstKeepsLocalTime() {
        val t = task(1, start = LocalDate.of(2027, 3, 13).atTime(7, 0)).copy(recurrence = RecurrenceRule(Frequency.DAILY))
        val now = ZonedDateTime.of(LocalDate.of(2027, 3, 13).atTime(8, 0), newYork).toInstant()
        val plan = AlarmPlanner.nextTrigger(t, ReminderSpec(1, 1, 0), now, newYork)!!
        assertEquals(7, ZonedDateTime.ofInstant(plan.triggerAt, newYork).hour)
        assertEquals(LocalDate.of(2027, 3, 14), plan.occurrence.toLocalDate())
    }

    @Test fun duplicateRemindersCollapse() {
        val t = task(1, start = at(10))
        val now = instant(at(8), riyadh)
        val plans = AlarmPlanner.plansFor(t, listOf(ReminderSpec(1, 1, 15), ReminderSpec(2, 1, 15), ReminderSpec(3, 1, 5)), now, riyadh)
        assertEquals(2, plans.size)
        assertEquals(listOf(1L, 3L), plans.map { it.reminderId })
    }

    @Test fun repeatIfUnansweredRespectsLimit() {
        val now = Instant.parse("2026-11-02T08:00:00Z")
        assertEquals(now.plusSeconds(300), AlarmPlanner.repeatIfUnanswered(now, 5, 0, 2))
        assertNull(AlarmPlanner.repeatIfUnanswered(now, 5, 2, 2))
        assertNull(AlarmPlanner.repeatIfUnanswered(now, 5, 0, 0))
    }

    @Test fun snoozeAndRequestCodesAreStable() {
        val now = Instant.parse("2026-11-02T08:00:00Z")
        assertEquals(now.plusSeconds(600), AlarmPlanner.snoozeUntil(now, 10))
        assertEquals(AlarmPlanner.requestCode(42), AlarmPlanner.requestCode(42))
        assertNotEquals(AlarmPlanner.requestCode(42), AlarmPlanner.requestCode(42, snooze = true))
        assertNotNull(AlarmPlanner.requestCode(Long.MAX_VALUE))
    }

    @Test fun overdueIsDerived() {
        val t = task(1, due = at(9))
        assertEquals(TaskStatus.OVERDUE, t.effectiveStatus(at(10)))
        assertEquals(TaskStatus.NEW, t.effectiveStatus(at(8)))
        assertEquals(TaskStatus.COMPLETED, t.copy(status = TaskStatus.COMPLETED).effectiveStatus(at(10)))
    }
}
