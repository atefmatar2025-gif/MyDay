package com.myday.app

import com.myday.app.data.Priority
import com.myday.app.data.RepeatType
import com.myday.app.nlp.ArabicTaskParser
import com.myday.app.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** السبت 31 أكتوبر 2026 الساعة 7 مساءً */
class ParserTest {
    private val now = LocalDateTime.of(2026, 10, 31, 19, 0)
    private val today = now.toLocalDate()
    private fun p(s: String) = ArabicTaskParser.parse(s, now)

    @Test fun tomorrowAfterAsr() {
        val r = p("ذكّرني بكرة بعد العصر أتصل بأبو محمد")
        assertEquals("أتصل بأبو محمد", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(Prayer.ASR, r.prayer)
        assertTrue(r.prayerOffsetMin > 0)
        assertEquals("المواعيد الشخصية", r.categoryName)
    }

    @Test fun beforeMaghribHalfHour() {
        val r = p("قبل المغرب بنص ساعة اشتري خبز")
        assertEquals(Prayer.MAGHRIB, r.prayer)
        assertEquals(-30, r.prayerOffsetMin)
        assertEquals("اشتري خبز", r.title)
        assertEquals("التسوق", r.categoryName)
    }

    @Test fun afterIshaByHour() {
        val r = p("بعد العشا بساعة اقرأ الورد")
        assertEquals(Prayer.ISHA, r.prayer)
        assertEquals(60, r.prayerOffsetMin)
        assertEquals("اقرأ الورد", r.title)
    }

    @Test fun dailyMedicineMorning() {
        val r = p("ذكرني اخد الدوا الساعة 8 الصبح كل يوم")
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals(RepeatType.DAILY, r.repeat)
        assertTrue(r.isCare)
        assertEquals("اخد الدوا", r.title)
        assertEquals("الصحة", r.categoryName)
    }

    @Test fun tomorrowFiveThirtyImportantMeeting() {
        val r = p("بكرا الساعة 5 ونص اجتماع مع المدير مهم")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(17, 30), r.time)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals("اجتماع مع المدير", r.title)
        assertEquals("العمل", r.categoryName)
    }

    @Test fun relativeTwoHours() {
        val r = p("بعد ساعتين اطفي الفرن")
        assertEquals(now.plusHours(2), r.relative)
        assertEquals("اطفي الفرن", r.title)
    }

    @Test fun relativeTenMinutesDigits() {
        val r = p("نبهني بعد ١٠ دقايق")
        assertEquals(now.plusMinutes(10), r.relative)
        assertEquals("مهمة", r.title)
    }

    @Test fun thursdayAtTen() {
        val r = p("يوم الخميس الساعة 10 دكتور الأسنان")
        assertEquals(LocalDate.of(2026, 11, 5), r.date)
        assertEquals(LocalTime.of(10, 0), r.time)
        assertEquals("دكتور الأسنان", r.title)
    }

    @Test fun quarterToFour() {
        val r = p("الساعه اربعه الا ربع العصر روح عالنادي")
        assertEquals(LocalTime.of(15, 45), r.time)
    }

    @Test fun twoAtNight() {
        val r = p("الساعة 2 بالليل صحيني")
        assertEquals(LocalTime.of(2, 0), r.time)
    }

    @Test fun afternoonPeriodWithClock() {
        val r = p("الساعة 4 بعد الظهر اتصل بالبنك")
        assertEquals(LocalTime.of(16, 0), r.time)
        assertNull(r.prayer)
        assertEquals("اتصل بالبنك", r.title)
    }

    @Test fun dayAfterTomorrow() {
        val r = p("بعد بكرا عيد ميلاد ستي")
        assertEquals(today.plusDays(2), r.date)
        assertEquals("عيد ميلاد ستي", r.title)
        assertEquals("الأسرة", r.categoryName)
    }

    @Test fun everyFriday() {
        val r = p("كل جمعة الساعة 11 الصبح نظف البيت")
        assertEquals(RepeatType.WEEKLY, r.repeat)
        assertEquals(LocalDate.of(2026, 11, 6), r.date)
        assertEquals(LocalTime.of(11, 0), r.time)
        assertEquals("نظف البيت", r.title)
    }

    @Test fun someoneIsNotSunday() {
        val r = p("بكرا احكي مع حد بخصوص السيارة")
        assertEquals(today.plusDays(1), r.date)
        assertTrue(r.title.contains("حد"))
    }

    @Test fun egyptianTomorrowEvening() {
        val r = p("بكره بالليل اكلم ماما")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(21, 0), r.time)
        assertEquals("اكلم ماما", r.title)
    }

    @Test fun atFajrDaily() {
        val r = p("عند الفجر كل يوم الأذكار")
        assertEquals(Prayer.FAJR, r.prayer)
        assertEquals(0, r.prayerOffsetMin)
        assertEquals(RepeatType.DAILY, r.repeat)
        assertEquals("العبادة", r.categoryName)
    }
}
