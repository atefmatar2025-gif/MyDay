package com.dayflow.core.intelligence

import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.FocusSession
import com.dayflow.core.model.Habit
import com.dayflow.core.model.HabitLog
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** A read-only snapshot of the user's local data handed to an engine. */
data class AssistantContext(
    val now: LocalDateTime,
    val tasks: List<Task>,
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val history: List<ProductivityRecord> = emptyList(),
    val settings: DaySettings = DaySettings(),
    val weekStart: DayOfWeek = DayOfWeek.SATURDAY,
)

/**
 * Something the user *may* do after reading a reply. Engines never change data themselves:
 * the UI shows these as buttons and every change still requires the user's tap/confirmation.
 */
sealed interface AssistantAction {
    val label: String

    data class StartTask(val taskId: Long, override val label: String) : AssistantAction
    data class OpenTask(val taskId: Long, override val label: String) : AssistantAction
    data class ReviewPlan(val date: LocalDate, override val label: String) : AssistantAction
}

enum class AssistantIntent {
    WHAT_NOW, TOP_TASKS, REMAINING, OVERDUE, PLAN_TOMORROW, PLAN_TODAY, HOURS_BY_AREA,
    MOST_POSTPONED, CONFLICTS, HOW_TO_FINISH, WEEK_REPORT, MONTH_REPORT, TODAY_DONE,
    HABITS, FREE_TIME, HELP, UNKNOWN,
}

data class AssistantReply(
    val intent: AssistantIntent,
    val text: String,
    val bullets: List<String> = emptyList(),
    val actions: List<AssistantAction> = emptyList(),
)

/**
 * The swappable engine boundary. Today the app ships [RuleBasedAssistantEngine]; an on-device
 * language model can be added later by implementing this interface (e.g. a `LocalLlmEngine`
 * that uses the same [AssistantContext] for grounding) without changing the UI or data layers.
 */
interface AssistantEngine {
    /** Display name shown in the UI. */
    val displayName: String

    /** False for rule-based engines - the UI must not present them as a language model. */
    val isLanguageModel: Boolean

    fun answer(query: String, context: AssistantContext): AssistantReply

    /** A short proactive suggestion for the dashboard, or null when there is nothing useful to say. */
    fun dailyInsight(context: AssistantContext): String?
}
