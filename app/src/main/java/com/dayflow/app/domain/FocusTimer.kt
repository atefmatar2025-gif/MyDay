package com.dayflow.app.domain

import android.content.Context
import com.dayflow.app.R
import com.dayflow.app.data.AppStateStore
import com.dayflow.app.data.FocusRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.FocusSessionEntity
import com.dayflow.app.notification.AlarmScheduler
import com.dayflow.app.notification.Channels
import com.dayflow.app.notification.IntentKeys
import com.dayflow.app.notification.Notifier
import com.dayflow.core.model.FocusType
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pomodoro / focus timer whose state is persisted (DataStore), so it survives the app being
 * closed or killed. The end of each phase is an AlarmManager alarm - no foreground service and
 * no control over other apps or Do-Not-Disturb is needed.
 */
class FocusTimer(
    private val context: Context,
    private val store: AppStateStore,
    private val settings: SettingsRepository,
    private val focusRepo: FocusRepository,
    private val tasks: TaskRepository,
    private val interactor: TaskInteractor,
    private val alarms: AlarmScheduler,
) {
    companion object {
        const val IDLE = "IDLE"
        const val FOCUS = "FOCUS"
        const val BREAK = "BREAK"
        const val LONG_BREAK = "LONG_BREAK"
        const val FOCUS_DONE = "FOCUS_DONE"
        const val BREAK_DONE = "BREAK_DONE"
        val RUNNING = setOf(FOCUS, BREAK, LONG_BREAK)
    }

    private val mutex = Mutex()
    val state: Flow<AppStateStore.FocusState> = store.focusState

    suspend fun startFocus(taskId: Long?, minutes: Int) = mutex.withLock {
        val current = store.focusState.first()
        startPhase(FOCUS, minutes, taskId ?: 0, current.cycle)
    }

    suspend fun startBreak(long: Boolean) = mutex.withLock {
        val s = settings.current()
        val current = store.focusState.first()
        startPhase(if (long) LONG_BREAK else BREAK, if (long) s.longBreakMinutes else s.shortBreakMinutes, current.taskId, current.cycle)
    }

    private suspend fun startPhase(phase: String, minutes: Int, taskId: Long, cycle: Int) {
        val now = System.currentTimeMillis()
        val endsAt = now + minutes.coerceIn(1, 240) * 60_000L
        store.setFocusState(AppStateStore.FocusState(phase, endsAt, 0, taskId, cycle, now, minutes))
        alarms.scheduleFocusEnd(endsAt)
        if (phase == FOCUS && taskId > 0) {
            val e = tasks.entity(taskId)
            if (e != null && e.status != com.dayflow.core.model.TaskStatus.IN_PROGRESS.name) interactor.start(taskId)
        }
    }

    suspend fun pause() = mutex.withLock {
        val s = store.focusState.first()
        if (s.phase !in RUNNING || s.isPaused) return@withLock
        alarms.cancelFocusEnd()
        store.setFocusState(s.copy(pausedRemainingMs = (s.endsAt - System.currentTimeMillis()).coerceAtLeast(1000)))
    }

    suspend fun resume() = mutex.withLock {
        val s = store.focusState.first()
        if (!s.isPaused) return@withLock
        val endsAt = System.currentTimeMillis() + s.pausedRemainingMs
        store.setFocusState(s.copy(endsAt = endsAt, pausedRemainingMs = 0))
        alarms.scheduleFocusEnd(endsAt)
    }

    /** Stops early. A focus phase of at least one minute is still recorded (as not completed). */
    suspend fun stop() = mutex.withLock {
        val s = store.focusState.first()
        alarms.cancelFocusEnd()
        if (s.phase in RUNNING) record(s, completed = false)
        store.setFocusState(AppStateStore.FocusState(phase = IDLE, cycle = s.cycle, taskId = s.taskId))
    }

    /** Called by the alarm (or the UI when the countdown reaches zero). Safe to call twice. */
    suspend fun onPhaseFinished() = mutex.withLock {
        val s = store.focusState.first()
        if (s.phase !in RUNNING || s.isPaused) return@withLock
        if (System.currentTimeMillis() < s.endsAt - 2_000) return@withLock // not yet - e.g. stale alarm
        alarms.cancelFocusEnd()
        record(s, completed = true)
        val cfg = settings.current()
        if (s.phase == FOCUS) {
            val cycle = s.cycle + 1
            store.setFocusState(AppStateStore.FocusState(phase = FOCUS_DONE, cycle = cycle, taskId = s.taskId))
            val long = cycle % cfg.cyclesBeforeLongBreak.coerceAtLeast(1) == 0
            Notifier.simple(
                context, Channels.FOCUS, Notifier.ID_FOCUS,
                context.getString(R.string.notif_focus_done_title),
                context.getString(R.string.notif_focus_done_text, if (long) cfg.longBreakMinutes else cfg.shortBreakMinutes),
                IntentKeys.DEST_FOCUS,
            )
        } else {
            store.setFocusState(AppStateStore.FocusState(phase = BREAK_DONE, cycle = s.cycle, taskId = s.taskId))
            Notifier.simple(
                context, Channels.FOCUS, Notifier.ID_FOCUS,
                context.getString(R.string.notif_break_done_title), context.getString(R.string.notif_break_done_text),
                IntentKeys.DEST_FOCUS,
            )
        }
    }

    /** After reboot/time change: re-register the phase end, or finish a phase that ended meanwhile. */
    suspend fun restoreAlarm() {
        val s = store.focusState.first()
        if (s.phase !in RUNNING || s.isPaused) return
        if (s.endsAt <= System.currentTimeMillis()) onPhaseFinished() else alarms.scheduleFocusEnd(s.endsAt)
    }

    suspend fun reset() = mutex.withLock {
        alarms.cancelFocusEnd()
        store.setFocusState(AppStateStore.FocusState())
    }

    private suspend fun record(s: AppStateStore.FocusState, completed: Boolean) {
        val end = minOf(System.currentTimeMillis(), s.endsAt)
        val minutes = if (completed) s.plannedMinutes else ((end - s.startedAt) / 60_000L).toInt().coerceIn(0, s.plannedMinutes)
        if (minutes < 1) return
        val type = if (s.phase == FOCUS) FocusType.FOCUS else FocusType.BREAK
        val title = if (s.taskId > 0) tasks.entity(s.taskId)?.title else null
        focusRepo.add(
            FocusSessionEntity(
                taskId = s.taskId.takeIf { it > 0 }, taskTitle = title, type = type.name,
                startedAt = LocalTimes.toEpochSecond(LocalDateTime.ofInstant(Instant.ofEpochMilli(s.startedAt), ZoneId.systemDefault()))!!,
                plannedMinutes = s.plannedMinutes, actualMinutes = minutes, completed = completed,
            ),
        )
        if (type == FocusType.FOCUS && s.taskId > 0) interactor.addActualMinutes(s.taskId, minutes)
    }
}
