package com.dayflow.core.time

import com.dayflow.core.model.Frequency
import com.dayflow.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure recurrence maths. The anchor is the first occurrence (the task's original start);
 * every later occurrence keeps the anchor's time of day.
 */
object RecurrenceCalculator {

    /** Search horizon for [nextOccurrence]; ten years is more than any sensible rule needs. */
    private const val MAX_SEARCH_DAYS = 3660L

    fun isOccurrence(rule: RecurrenceRule, anchor: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(anchor)) return false
        if (rule.until != null && date.isAfter(rule.until)) return false
        if (date in rule.exceptions) return false
        return when (rule.frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(anchor, date) % rule.interval == 0L
            Frequency.WEEKLY -> {
                val days = rule.daysOfWeek.ifEmpty { setOf(anchor.dayOfWeek) }
                if (date.dayOfWeek !in days) return false
                val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ChronoUnit.WEEKS.between(anchorWeek, dateWeek) % rule.interval == 0L
            }
            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date))
                months % rule.interval == 0L && date.dayOfMonth == clampDay(anchor.dayOfMonth, YearMonth.from(date))
            }
            Frequency.YEARLY -> {
                val years = (date.year - anchor.year).toLong()
                date.month == anchor.month && years % rule.interval == 0L &&
                    date.dayOfMonth == clampDay(anchor.dayOfMonth, YearMonth.from(date))
            }
        }
    }

    /** All occurrence dates within [from, to] (inclusive). */
    fun occurrencesBetween(rule: RecurrenceRule, anchor: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var d = if (from.isBefore(anchor)) anchor else from
        while (!d.isAfter(to)) {
            if (isOccurrence(rule, anchor, d)) result += d
            d = d.plusDays(1)
        }
        return result
    }

    /** First occurrence strictly after [after], or null when the rule has ended. */
    fun nextOccurrence(rule: RecurrenceRule, anchor: LocalDateTime, after: LocalDateTime): LocalDateTime? {
        val time = anchor.toLocalTime()
        var d = if (after.toLocalDate().isBefore(anchor.toLocalDate())) anchor.toLocalDate() else after.toLocalDate()
        var steps = 0L
        while (steps < MAX_SEARCH_DAYS) {
            if (rule.until != null && d.isAfter(rule.until)) return null
            if (isOccurrence(rule, anchor.toLocalDate(), d)) {
                val candidate = d.atTime(time)
                if (candidate.isAfter(after)) return candidate
            }
            d = d.plusDays(1)
            steps++
        }
        return null
    }

    private fun clampDay(day: Int, month: YearMonth): Int = minOf(day, month.lengthOfMonth())
}
