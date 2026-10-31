package com.dayflow.core

import com.dayflow.core.TestData.TODAY
import com.dayflow.core.TestData.at
import com.dayflow.core.model.Frequency
import com.dayflow.core.model.Priority
import com.dayflow.core.text.VoiceTaskParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class VoiceTaskParserTest {

    private val now = at(14, 0) // Monday 14:00

    @Test fun specExample() {
        val r = VoiceTaskParser.parse("ذكّرني غداً الساعة الثامنة صباحاً بمراجعة مادة البحث العلمي لمدة ساعة.", now)
        assertEquals("مراجعة مادة البحث العلمي", r.title)
        assertEquals(TODAY.plusDays(1), r.date)
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals(60, r.durationMinutes)
        assertTrue(r.reminder)
        assertFalse(r.needsConfirmation)
    }

    @Test fun digitsAndHalfHour() {
        val r = VoiceTaskParser.parse("اجتماع الفريق اليوم الساعة ٤:٣٠ مساءً لمدة نصف ساعة", now)
        assertEquals("اجتماع الفريق", r.title)
        assertEquals(TODAY, r.date)
        assertEquals(LocalTime.of(16, 30), r.time)
        assertEquals(30, r.durationMinutes)
    }

    @Test fun wordsWithHalfAndEvening() {
        val r = VoiceTaskParser.parse("نبهني بعد غد الساعة السابعة والنصف مساء أن أتصل بأبي", now)
        assertEquals("أتصل بأبي", r.title)
        assertEquals(TODAY.plusDays(2), r.date)
        assertEquals(LocalTime.of(19, 30), r.time)
    }

    @Test fun quarterTo() {
        val r = VoiceTaskParser.parse("موعد الطبيب يوم الخميس الساعة العاشرة إلا ربع صباحا", now)
        assertEquals("موعد الطبيب", r.title)
        assertEquals(TODAY.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.THURSDAY)), r.date)
        assertEquals(LocalTime.of(9, 45), r.time)
    }

    @Test fun relativeTime() {
        val r = VoiceTaskParser.parse("ذكرني بعد ساعتين بإخراج الغسيل", now)
        assertEquals("إخراج الغسيل", r.title)
        assertEquals(TODAY, r.date)
        assertEquals(LocalTime.of(16, 0), r.time)
    }

    @Test fun ambiguousHourIsFlagged() {
        val r = VoiceTaskParser.parse("مكالمة العميل غدا الساعة 3", now)
        assertEquals(LocalTime.of(15, 0), r.time)
        assertTrue(r.needsConfirmation)
    }

    @Test fun passedHourTodayRollsToEvening() {
        val r = VoiceTaskParser.parse("رياضة الساعة 9", now) // 9 AM already passed at 14:00
        assertEquals(TODAY, r.date)
        assertEquals(LocalTime.of(21, 0), r.time)
        assertTrue(r.needsConfirmation)
    }

    @Test fun durationVariants() {
        assertEquals(90, VoiceTaskParser.parse("قراءة لمدة ساعة ونصف", now).durationMinutes)
        assertEquals(45, VoiceTaskParser.parse("مشي لمدة 45 دقيقة", now).durationMinutes)
        assertEquals(180, VoiceTaskParser.parse("مذاكرة لمدة 3 ساعات", now).durationMinutes)
        assertEquals(120, VoiceTaskParser.parse("مشروع لمدة ساعتين", now).durationMinutes)
    }

    @Test fun priorityAndRecurrence() {
        val r = VoiceTaskParser.parse("تقرير المبيعات عاجل جدا كل يوم الخميس", now)
        assertEquals(Priority.URGENT, r.priority)
        assertEquals(Frequency.WEEKLY, r.recurrence?.frequency)
        assertEquals(setOf(DayOfWeek.THURSDAY), r.recurrence?.daysOfWeek)
        assertEquals("تقرير المبيعات", r.title)
    }

    @Test fun plainTextWithoutTime() {
        val r = VoiceTaskParser.parse("شراء الخبز", now)
        assertEquals("شراء الخبز", r.title)
        assertNull(r.date)
        assertNull(r.time)
        assertFalse(r.reminder)
    }

    @Test fun emptyTitleNeedsConfirmation() {
        val r = VoiceTaskParser.parse("ذكرني غدا", now)
        assertTrue(r.needsConfirmation)
    }

    @Test fun numericDate() {
        val r = VoiceTaskParser.parse("تسليم البحث في 15/12 الساعة 10 صباحا", now)
        assertEquals("تسليم البحث", r.title)
        assertEquals(java.time.LocalDate.of(2026, 12, 15), r.date)
        assertEquals(LocalTime.of(10, 0), r.time)
    }
}
