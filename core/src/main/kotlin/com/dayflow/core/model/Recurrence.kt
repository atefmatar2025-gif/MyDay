package com.dayflow.core.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A recurrence rule. Stored in the database as a compact, versioned, RRULE-like
 * string (see [RecurrenceCodec]) rather than free-form JSON.
 *
 * @param interval repeat every N units (days / weeks / months / years).
 * @param daysOfWeek for WEEKLY: the weekdays (empty = the anchor's weekday).
 * @param exceptions dates on which an occurrence is skipped.
 * @param until last date (inclusive) on which an occurrence may happen.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val exceptions: Set<LocalDate> = emptySet(),
    val until: LocalDate? = null,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
    }
}

object RecurrenceCodec {
    private const val VERSION = "v1"

    fun encode(rule: RecurrenceRule?): String? {
        if (rule == null) return null
        val parts = mutableListOf(VERSION, "F=${rule.frequency.name}", "I=${rule.interval}")
        if (rule.daysOfWeek.isNotEmpty()) {
            parts += "D=" + rule.daysOfWeek.sortedBy { it.value }.joinToString(",") { it.value.toString() }
        }
        if (rule.exceptions.isNotEmpty()) {
            parts += "X=" + rule.exceptions.sorted().joinToString(",")
        }
        rule.until?.let { parts += "U=$it" }
        return parts.joinToString(";")
    }

    /** Returns null for null/blank/unknown input instead of throwing, so a corrupt row never crashes the app. */
    fun decode(value: String?): RecurrenceRule? {
        if (value.isNullOrBlank()) return null
        return try {
            val map = value.split(';').drop(1).mapNotNull {
                val idx = it.indexOf('=')
                if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
            }.toMap()
            RecurrenceRule(
                frequency = Frequency.valueOf(map.getValue("F")),
                interval = map["I"]?.toInt() ?: 1,
                daysOfWeek = map["D"]?.split(',')?.filter { it.isNotBlank() }
                    ?.map { DayOfWeek.of(it.toInt()) }?.toSet() ?: emptySet(),
                exceptions = map["X"]?.split(',')?.filter { it.isNotBlank() }
                    ?.map { LocalDate.parse(it) }?.toSet() ?: emptySet(),
                until = map["U"]?.let { LocalDate.parse(it) },
            )
        } catch (e: Exception) {
            null
        }
    }
}
