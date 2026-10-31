package com.myday.app.util

import com.myday.app.data.RepeatType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** حسابات التكرار (يومي/أسبوعي/شهري) مع احترام التوقيت الصيفي والمنطقة الزمنية. */
object Recurrence {

    private const val MAX_STEPS = 20_000

    /** موعد التكرار رقم [step] انطلاقاً من [anchor] (step = 0 يعني الموعد نفسه). */
    fun occurrence(anchor: Long, type: RepeatType, step: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val base: ZonedDateTime = Instant.ofEpochMilli(anchor).atZone(zone)
        val result = when (type) {
            RepeatType.NONE -> base
            RepeatType.DAILY -> base.plusDays(step)
            RepeatType.WEEKLY -> base.plusWeeks(step)
            RepeatType.MONTHLY -> base.plusMonths(step)
        }
        return result.toInstant().toEpochMilli()
    }

    /** الموعد التالي مباشرة بعد [time]. */
    fun next(time: Long, type: RepeatType, zone: ZoneId = ZoneId.systemDefault()): Long? =
        if (type == RepeatType.NONE) null else occurrence(time, type, 1, zone)

    /**
     * أول موعد ضمن سلسلة التكرار يكون بعد [threshold] تماماً.
     * يرجع null للمهام غير المتكررة إذا كان موعدها لا يتجاوز العتبة.
     */
    fun firstAfter(anchor: Long, type: RepeatType, threshold: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (anchor > threshold) return anchor
        if (type == RepeatType.NONE) return null
        // تقدير أولي سريع ثم تصحيح خطوة بخطوة
        val approxStepMs = when (type) {
            RepeatType.DAILY -> 86_400_000L
            RepeatType.WEEKLY -> 7 * 86_400_000L
            RepeatType.MONTHLY -> 31 * 86_400_000L // تقدير أدنى حتى لا نتجاوز موعداً
            RepeatType.NONE -> 1L
        }
        var step = maxOf(0L, (threshold - anchor) / approxStepMs - 2)
        var guard = 0
        while (guard++ < MAX_STEPS) {
            val t = occurrence(anchor, type, step, zone)
            if (t > threshold) return t
            step++
        }
        return null
    }

    /** هل تقع مهمة متكررة (أو عادية) في اليوم المحدد؟ يُستخدم للتقويم. */
    fun occursOnDay(anchor: Long, type: RepeatType, dayStart: Long, dayEnd: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (type == RepeatType.NONE) return if (anchor in dayStart until dayEnd) anchor else null
        if (anchor >= dayEnd) return null
        val t = firstAfter(anchor, type, dayStart - 1, zone) ?: return null
        return if (t < dayEnd) t else null
    }
}
