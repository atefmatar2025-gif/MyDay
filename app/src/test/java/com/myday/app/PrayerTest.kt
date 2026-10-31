package com.myday.app

import com.myday.app.data.RepeatType
import com.myday.app.data.TaskEntity
import com.myday.app.prayer.CalcMethod
import com.myday.app.prayer.Prayer
import com.myday.app.prayer.PrayerConfig
import com.myday.app.prayer.PrayerEngine
import com.myday.app.util.TaskTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrayerTest {
    private val mecca = PrayerConfig("مكة", 21.4225, 39.8262, CalcMethod.UMM_AL_QURA)
    private val riyadhZone = ZoneId.of("Asia/Riyadh")
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private fun fmt(ms: Long?) = Instant.ofEpochMilli(ms!!).atZone(riyadhZone).format(hm)

    /** مطابقة تقويم أم القرى المنشور لمكة المكرمة يوم 31 أكتوبر 2026. */
    @Test fun meccaUmmAlQura() {
        val t = PrayerEngine.compute(LocalDate.of(2026, 10, 31), mecca)
        assertEquals("05:06", fmt(t[Prayer.FAJR]))
        assertEquals("06:23", fmt(t[Prayer.SUNRISE]))
        assertEquals("12:04", fmt(t[Prayer.DHUHR]))
        assertEquals("15:20", fmt(t[Prayer.ASR]))
        assertEquals("17:45", fmt(t[Prayer.MAGHRIB]))
        assertEquals("19:15", fmt(t[Prayer.ISHA]))
    }

    @Test fun adjustmentsApplied() {
        val t = PrayerEngine.compute(LocalDate.of(2026, 10, 31), mecca.copy(adjustments = listOf(2, 0, 0, 0, -1, 0)))
        assertEquals("05:08", fmt(t[Prayer.FAJR]))
        assertEquals("17:44", fmt(t[Prayer.MAGHRIB]))
    }

    @Test fun highLatitudeNeverMissing() {
        val oslo = PrayerConfig("أوسلو", 59.91, 10.75, CalcMethod.MWL)
        val t = PrayerEngine.compute(LocalDate.of(2026, 6, 21), oslo)
        assertEquals(6, t.times.size)
    }

    @Test fun dailyAfterAsrMovesWithPrayerTime() {
        val day1 = LocalDate.of(2026, 10, 31)
        val asr1 = PrayerEngine.compute(day1, mecca)[Prayer.ASR]!!
        val task = TaskEntity(
            id = 1, title = "ورد", dueAt = asr1 + 30 * 60_000L, repeatType = RepeatType.DAILY,
            anchorPrayer = Prayer.ASR.name, anchorOffsetMin = 30
        )
        val next = TaskTime.firstAfter(task, task.dueAt + 1, mecca, riyadhZone)!!
        val asr2 = PrayerEngine.compute(day1.plusDays(1), mecca)[Prayer.ASR]!!
        assertEquals(asr2 + 30 * 60_000L, next)
        // يتغير وقت العصر قليلاً كل يوم، فالفارق ليس 24 ساعة بالضبط بالضرورة
        assertTrue(kotlin.math.abs(next - task.dueAt - 86_400_000L) < 5 * 60_000L)
    }

    @Test fun beforeMaghribLabel() {
        assertEquals("قبل المغرب بـ 15 د", TaskTime.anchorLabel(Prayer.MAGHRIB, -15))
        assertEquals("بعد العصر بساعة", TaskTime.anchorLabel(Prayer.ASR, 60))
        assertEquals("عند أذان الظهر", TaskTime.anchorLabel(Prayer.DHUHR, 0))
    }
}
