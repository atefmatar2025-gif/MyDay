package com.myday.app.util

import com.myday.app.data.RepeatType
import com.myday.app.data.TaskEntity
import com.myday.app.prayer.Prayer
import com.myday.app.prayer.PrayerConfig
import com.myday.app.prayer.PrayerStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * توقيت المهمة الموحّد: وقت ثابت، أو وقت نسبي لصلاة (مثل «بعد العصر بـ 30 د»)
 * يتحرك كل يوم مع تغيّر أوقات الصلاة.
 */
object TaskTime {

    fun prayerOf(task: TaskEntity): Prayer? = Prayer.fromName(task.anchorPrayer)

    /** التاريخ الذي تنتمي إليه الصلاة المرتبطة بالموعد الحالي. */
    fun anchorDate(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(task.dueAt - task.anchorOffsetMin * 60_000L).atZone(zone).toLocalDate()

    /** وقت المهمة في يوم معيّن. */
    fun timeOn(
        task: TaskEntity,
        date: LocalDate,
        cfg: PrayerConfig? = PrayerStore.current(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val p = prayerOf(task)
        if (p == null || cfg == null) {
            val t = Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalTime()
            return LocalDateTime.of(date, t).atZone(zone).toInstant().toEpochMilli()
        }
        return prayerTime(date, p, task.anchorOffsetMin, cfg)
    }

    fun prayerTime(date: LocalDate, prayer: Prayer, offsetMin: Int, cfg: PrayerConfig? = PrayerStore.current()): Long? {
        val base = PrayerStore.timesFor(date, cfg)?.get(prayer) ?: return null
        return base + offsetMin * 60_000L
    }

    private fun stepDate(d: LocalDate, type: RepeatType, k: Long): LocalDate = when (type) {
        RepeatType.NONE -> d
        RepeatType.DAILY -> d.plusDays(k)
        RepeatType.WEEKLY -> d.plusWeeks(k)
        RepeatType.MONTHLY -> d.plusMonths(k)
    }

    /** أول موعد للمهمة بعد [threshold] تماماً. */
    fun firstAfter(
        task: TaskEntity,
        threshold: Long,
        cfg: PrayerConfig? = PrayerStore.current(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (prayerOf(task) == null || cfg == null) {
            return Recurrence.firstAfter(task.dueAt, task.repeatType, threshold, zone)
        }
        if (task.dueAt > threshold) return task.dueAt
        if (task.repeatType == RepeatType.NONE) return null
        val base = anchorDate(task, zone)
        val days = (threshold - task.dueAt) / 86_400_000L
        var k = when (task.repeatType) {
            RepeatType.DAILY -> days - 2
            RepeatType.WEEKLY -> days / 7 - 1
            RepeatType.MONTHLY -> days / 31 - 1
            RepeatType.NONE -> 0
        }.coerceAtLeast(1)
        var guard = 0
        while (guard++ < 2_000) {
            val t = timeOn(task, stepDate(base, task.repeatType, k), cfg, zone)
            if (t != null && t > threshold) return t
            k++
        }
        return null
    }

    /** موعد المهمة داخل يوم معيّن إن وُجد (للتقويم والجدول اليومي). */
    fun occursOnDay(
        task: TaskEntity,
        dayStart: Long,
        dayEnd: Long,
        cfg: PrayerConfig? = PrayerStore.current(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (prayerOf(task) == null || cfg == null) {
            return Recurrence.occursOnDay(task.dueAt, task.repeatType, dayStart, dayEnd, zone)
        }
        if (task.dueAt in dayStart until dayEnd) return task.dueAt
        if (task.repeatType == RepeatType.NONE || task.dueAt >= dayEnd) return null
        val t = firstAfter(task, dayStart - 1, cfg, zone) ?: return null
        return if (t < dayEnd) t else null
    }

    /** وصف عربي طبيعي: «بعد العصر بـ 30 د» / «قبل المغرب بـ 15 د» / «عند الظهر». */
    fun anchorLabel(prayer: Prayer, offsetMin: Int): String = when {
        offsetMin == 0 -> if (prayer == Prayer.SUNRISE) "عند الشروق" else "عند أذان ${prayer.label}"
        offsetMin > 0 -> "بعد ${prayer.label} ${by(offsetMin)}"
        else -> "قبل ${prayer.label} ${by(-offsetMin)}"
    }

    private fun by(min: Int): String = when (min) {
        60 -> "بساعة"
        120 -> "بساعتين"
        else -> "بـ ${durationLabel(min)}"
    }

    fun anchorLabel(task: TaskEntity): String? = prayerOf(task)?.let { anchorLabel(it, task.anchorOffsetMin) }

    private fun durationLabel(min: Int): String = when {
        min < 60 -> "$min د"
        min % 60 == 0 -> if (min == 60) "ساعة" else if (min == 120) "ساعتين" else "${min / 60} ساعات"
        else -> "${min / 60} س ${min % 60} د"
    }
}
