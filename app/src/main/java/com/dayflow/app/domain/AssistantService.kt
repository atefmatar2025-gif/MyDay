package com.dayflow.app.domain

import com.dayflow.app.data.FocusRepository
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.HistoryRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.weekStart
import com.dayflow.core.intelligence.AssistantContext
import com.dayflow.core.intelligence.AssistantEngine
import com.dayflow.core.intelligence.AssistantReply
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Clock that emits the current minute; drives "now"-dependent screens. */
fun minuteTicker(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.truncatedTo(ChronoUnit.MINUTES))
        delay(60_000L - (now.second * 1000L + now.nano / 1_000_000))
    }
}.distinctUntilChanged()

/**
 * Builds the read-only [AssistantContext] from local data and hands it to the active engine.
 * The engine is injected, so a future on-device language model can replace the rule-based one.
 */
class AssistantService(
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val focus: FocusRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    val engine: AssistantEngine,
) {
    private val dataFlow = combine(tasks.tasks, habits.habits, habits.logs, focus.sessions, history.records) { t, h, l, f, r ->
        AssistantContext(LocalDateTime.now(), t, h, l, f, r)
    }

    /** Live snapshot of everything, re-emitted when data changes or the minute changes. */
    val context: Flow<AssistantContext> = combine(dataFlow, settings.settings, settings.daySettings, minuteTicker()) { d, s, ds, now ->
        d.copy(now = now, settings = ds, weekStart = s.weekStart)
    }

    suspend fun snapshot(): AssistantContext = context.first().copy(now = LocalDateTime.now())

    suspend fun ask(question: String): AssistantReply = engine.answer(question, snapshot())
}
