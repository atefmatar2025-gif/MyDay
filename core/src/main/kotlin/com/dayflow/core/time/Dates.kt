package com.dayflow.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAdjusters

/** Conversions used by the database layer: wall-clock times are stored as epoch seconds at UTC. */
object LocalTimes {
    fun toEpochSecond(value: LocalDateTime?): Long? = value?.toEpochSecond(ZoneOffset.UTC)
    fun fromEpochSecond(value: Long?): LocalDateTime? = value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    fun startOfWeek(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))
}

data class HijriDay(val year: Int, val month: Int, val day: Int)

/**
 * Hijri dates from the JDK's built-in Umm al-Qura calendar ([HijrahDate], available on Android 8+),
 * with a user adjustment in days (-2..+2) because local moon sighting can differ from the
 * calculated calendar.
 */
object HijriCalendar {
    val monthNamesAr = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    fun fromGregorian(date: LocalDate, adjustmentDays: Int = 0): HijriDay? = try {
        val h = HijrahDate.from(date.plusDays(adjustmentDays.coerceIn(-2, 2).toLong()))
        HijriDay(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    } catch (e: Exception) {
        null // outside the supported Umm al-Qura range
    }

    fun format(date: LocalDate, adjustmentDays: Int = 0): String? =
        fromGregorian(date, adjustmentDays)?.let { "${it.day} ${monthNamesAr[it.month - 1]} ${it.year} هـ" }
}
