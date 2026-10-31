package com.myday.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RingingInfo(
    val taskId: Long,
    val title: String,
    val description: String,
    val dueAt: Long,
    val startedAt: Long = System.currentTimeMillis(),
)

/** حالة الرنين الحالية داخل العملية — تراقبها شاشة المنبه لتغلق نفسها عند الإيقاف. */
object AlarmState {
    private val _ringing = MutableStateFlow<RingingInfo?>(null)
    val ringing: StateFlow<RingingInfo?> = _ringing
    internal fun set(info: RingingInfo?) { _ringing.value = info }
}
