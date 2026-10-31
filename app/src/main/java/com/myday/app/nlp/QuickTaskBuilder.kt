package com.myday.app.nlp

import com.myday.app.data.CategoryEntity
import com.myday.app.data.Priority
import com.myday.app.data.TaskEntity
import com.myday.app.prayer.PrayerConfig
import com.myday.app.settings.AppSettings
import com.myday.app.util.TaskTime
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** يحوّل الجملة المفهومة إلى مهمة جاهزة للحفظ. */
object QuickTaskBuilder {

    private fun ms(ldt: LocalDateTime, zone: ZoneId) = ldt.atZone(zone).toInstant().toEpochMilli()

    /** الموعد الفعلي؛ null إذا كانت المهمة مرتبطة بصلاة ولم تُحدَّد المدينة. */
    fun resolveDue(p: ParsedTask, now: LocalDateTime, cfg: PrayerConfig?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val nowMs = ms(now, zone)
        val today = now.toLocalDate()
        p.relative?.let { return ms(it, zone) }
        p.prayer?.let { prayer ->
            if (cfg == null) return null
            val d0 = p.date ?: today
            val t = TaskTime.prayerTime(d0, prayer, p.prayerOffsetMin, cfg) ?: return null
            if (p.date == null && t <= nowMs) return TaskTime.prayerTime(d0.plusDays(1), prayer, p.prayerOffsetMin, cfg)
            return t
        }
        p.time?.let { time ->
            val d0 = p.date ?: today
            var dt = LocalDateTime.of(d0, time)
            if (p.date == null && !dt.isAfter(now)) dt = dt.plusDays(1)
            return ms(dt, zone)
        }
        p.date?.let { d ->
            val dt = LocalDateTime.of(d, LocalTime.of(9, 0))
            return if (dt.isAfter(now)) ms(dt, zone) else ms(nextFullHour(now), zone)
        }
        return ms(nextFullHour(now), zone)
    }

    private fun nextFullHour(now: LocalDateTime) = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)

    fun build(
        p: ParsedTask,
        dueAt: Long,
        settings: AppSettings,
        categories: List<CategoryEntity>,
    ): TaskEntity = TaskEntity(
        title = p.title,
        dueAt = dueAt,
        priority = p.priority ?: if (p.isCare) Priority.HIGH else Priority.MEDIUM,
        categoryId = p.categoryName?.let { name -> categories.firstOrNull { it.name == name }?.id },
        repeatType = p.repeat,
        alarmEnabled = true,
        reminderMinutesBefore = settings.defaultReminderMin,
        soundDurationSec = settings.ringDurationSec,
        repeatIfNoResponse = settings.repeatIfNoResponse || p.isCare,
        repeatIntervalMin = settings.repeatIntervalMin,
        maxRepeats = settings.maxRepeats,
        anchorPrayer = if (p.relative == null) p.prayer?.name else null,
        anchorOffsetMin = if (p.relative == null && p.prayer != null) p.prayerOffsetMin else 0,
        isCare = p.isCare,
    )
}
