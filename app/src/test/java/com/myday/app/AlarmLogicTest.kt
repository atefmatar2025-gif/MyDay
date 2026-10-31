package com.myday.app

import com.myday.app.alarm.AlarmTimes
import com.myday.app.data.RepeatType
import com.myday.app.data.TaskEntity
import com.myday.app.util.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmLogicTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun task(due: Long, repeat: RepeatType = RepeatType.NONE) =
        TaskEntity(id = 1, title = "t", dueAt = due, repeatType = repeat)

    @Test fun oneTimeFutureAlarm() {
        val due = ms(2026, 11, 1, 8, 0)
        assertEquals(due, AlarmTimes.nextAlarm(task(due), ms(2026, 10, 31, 20, 0)))
    }

    @Test fun oneTimePastAlarmIsNotScheduled() {
        val due = ms(2026, 10, 30, 8, 0)
        assertNull(AlarmTimes.nextAlarm(task(due), ms(2026, 10, 31, 8, 0)))
    }

    @Test fun completedTaskHasNoAlarm() {
        val due = ms(2026, 11, 1, 8, 0)
        assertNull(AlarmTimes.nextAlarm(task(due).copy(isCompleted = true), ms(2026, 10, 31, 8, 0)))
    }

    @Test fun dailyOverdueSchedulesNextOccurrence() {
        val due = ms(2026, 10, 25, 8, 0)
        val now = ms(2026, 10, 31, 9, 0)
        assertEquals(ms(2026, 11, 1, 8, 0), Recurrence.firstAfter(due, RepeatType.DAILY, now, zone))
    }

    @Test fun dailyKeepsWallClockAcrossDst() {
        // تغيير التوقيت الصيفي في القدس: نهاية أكتوبر
        val due = ms(2026, 10, 20, 7, 30)
        val next = Recurrence.firstAfter(due, RepeatType.DAILY, ms(2026, 11, 2, 0, 0), zone)!!
        assertEquals(ms(2026, 11, 2, 7, 30), next)
    }

    @Test fun weeklyAndMonthly() {
        val due = ms(2026, 1, 31, 10, 0)
        assertEquals(ms(2026, 2, 7, 10, 0), Recurrence.firstAfter(due, RepeatType.WEEKLY, due, zone))
        assertEquals(ms(2026, 2, 28, 10, 0), Recurrence.firstAfter(due, RepeatType.MONTHLY, due, zone))
        assertEquals(ms(2026, 3, 31, 10, 0), Recurrence.firstAfter(due, RepeatType.MONTHLY, ms(2026, 3, 1, 0, 0), zone))
    }

    @Test fun snoozeOverridesSchedule() {
        val now = ms(2026, 10, 31, 8, 0)
        val t = task(ms(2026, 10, 31, 7, 55)).copy(snoozedUntil = now + 300_000)
        assertEquals(now + 300_000, AlarmTimes.nextAlarm(t, now))
    }

    @Test fun reminderBeforeDue() {
        val due = ms(2026, 11, 1, 8, 0)
        val t = task(due).copy(reminderMinutesBefore = 15)
        val r = AlarmTimes.nextReminder(t, ms(2026, 10, 31, 20, 0))!!
        assertEquals(due - 15 * 60_000, r.first)
        assertEquals(due, r.second)
    }

    @Test fun reminderSkipsToNextOccurrenceWhenTooLate() {
        val due = ms(2026, 11, 1, 8, 0)
        val t = task(due, RepeatType.DAILY).copy(reminderMinutesBefore = 30)
        val r = AlarmTimes.nextReminder(t, ms(2026, 11, 1, 7, 45))!!
        assertEquals(ms(2026, 11, 2, 8, 0), r.second)
    }

    @Test fun occursOnDayForRecurring() {
        val due = ms(2026, 10, 1, 9, 0)
        // 1 أكتوبر 2026 خميس، و15 أكتوبر خميس، و16 جمعة
        val start = ms(2026, 10, 16, 0, 0)
        val end = ms(2026, 10, 17, 0, 0)
        assertEquals(ms(2026, 10, 16, 9, 0), Recurrence.occursOnDay(due, RepeatType.DAILY, start, end, zone))
        assertNull(Recurrence.occursOnDay(due, RepeatType.WEEKLY, start, end, zone))
        assertEquals(
            ms(2026, 10, 15, 9, 0),
            Recurrence.occursOnDay(due, RepeatType.WEEKLY, ms(2026, 10, 15, 0, 0), ms(2026, 10, 16, 0, 0), zone)
        )
    }

    @Test fun monthlyLongSpanDoesNotSkip() {
        val due = ms(2020, 1, 10, 9, 0)
        assertEquals(ms(2030, 1, 10, 9, 0), Recurrence.firstAfter(due, RepeatType.MONTHLY, ms(2030, 1, 1, 0, 0), zone))
    }
}
