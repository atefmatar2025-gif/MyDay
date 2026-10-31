package com.myday.app.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.myday.app.container
import com.myday.app.data.Priority
import com.myday.app.data.RepeatType
import com.myday.app.data.TaskEntity
import com.myday.app.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class EditorState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val original: TaskEntity? = null,
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val repeatType: RepeatType = RepeatType.NONE,
    val isFavorite: Boolean = false,
    val alarmEnabled: Boolean = true,
    val reminderMin: Int = 0,
    val soundUri: String? = null,
    val soundName: String? = null,
    val soundDurationSec: Int = 60,
    val repeatIfNoResponse: Boolean = true,
    val repeatIntervalMin: Int = 5,
    val maxRepeats: Int = 3,
    val titleError: Boolean = false,
    val saving: Boolean = false,
) {
    val dueAt: Long get() = Fmt.toMillis(LocalDateTime.of(date, time))
}

enum class SaveResult { SAVED, NEED_TITLE, PAST_TIME }

class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = app.container.repository
    private val settingsRepo = app.container.settings

    private val taskId: Long = handle.get<Long>("taskId") ?: -1L
    private val copyFrom: Long = handle.get<Long>("copyFrom") ?: -1L
    private val presetDay: Long = handle.get<Long>("date") ?: -1L

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            val base = EditorState(
                loaded = true,
                reminderMin = s.defaultReminderMin,
                soundDurationSec = s.ringDurationSec,
                repeatIfNoResponse = s.repeatIfNoResponse,
                repeatIntervalMin = s.repeatIntervalMin,
                maxRepeats = s.maxRepeats,
            )
            val source = when {
                taskId > 0 -> repo.getTask(taskId)
                copyFrom > 0 -> repo.getTask(copyFrom)
                else -> null
            }
            _state.value = if (source != null) {
                val ldt = Fmt.toLdt(source.dueAt)
                var date = ldt.toLocalDate()
                if (taskId <= 0) {
                    // نسخة من مهمة سابقة: ننقلها لأقرب موعد قادم بنفس الساعة
                    val today = LocalDate.now()
                    date = if (LocalDateTime.of(today, ldt.toLocalTime()).isAfter(LocalDateTime.now())) today else today.plusDays(1)
                }
                base.copy(
                    isEdit = taskId > 0,
                    original = if (taskId > 0) source else null,
                    title = source.title,
                    description = source.description,
                    date = date,
                    time = ldt.toLocalTime().withSecond(0).withNano(0),
                    priority = source.priority,
                    categoryId = source.categoryId,
                    repeatType = source.repeatType,
                    isFavorite = source.isFavorite,
                    alarmEnabled = source.alarmEnabled,
                    reminderMin = source.reminderMinutesBefore,
                    soundUri = source.soundUri,
                    soundName = source.soundName,
                    soundDurationSec = source.soundDurationSec,
                    repeatIfNoResponse = source.repeatIfNoResponse,
                    repeatIntervalMin = source.repeatIntervalMin,
                    maxRepeats = source.maxRepeats,
                )
            } else {
                // مهمة جديدة: الساعة الكاملة القادمة (في اليوم المختار من التقويم إن وجد)
                val nextHour = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
                val date = if (presetDay >= 0) LocalDate.ofEpochDay(presetDay) else nextHour.toLocalDate()
                base.copy(date = date, time = nextHour.toLocalTime())
            }
        }
    }

    fun update(block: (EditorState) -> EditorState) = _state.update { block(it).copy(titleError = false) }

    fun save(force: Boolean, onDone: (Long) -> Unit): SaveResult {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(titleError = true) }
            return SaveResult.NEED_TITLE
        }
        val originalDue = s.original?.dueAt
        if (!force && s.repeatType == RepeatType.NONE && s.dueAt <= System.currentTimeMillis() &&
            originalDue != s.dueAt
        ) return SaveResult.PAST_TIME
        if (s.saving) return SaveResult.SAVED

        _state.update { it.copy(saving = true) }
        val base = s.original ?: TaskEntity(title = "", dueAt = 0)
        val nowMs = System.currentTimeMillis()
        val reactivate = s.original?.let { it.isCompleted && s.dueAt > nowMs && it.dueAt != s.dueAt } ?: false
        val task = base.copy(
            title = s.title,
            description = s.description,
            dueAt = s.dueAt,
            priority = s.priority,
            categoryId = s.categoryId,
            repeatType = s.repeatType,
            isFavorite = s.isFavorite,
            alarmEnabled = s.alarmEnabled,
            reminderMinutesBefore = s.reminderMin,
            soundUri = s.soundUri,
            soundName = s.soundName,
            soundDurationSec = s.soundDurationSec,
            repeatIfNoResponse = s.repeatIfNoResponse,
            repeatIntervalMin = s.repeatIntervalMin,
            maxRepeats = s.maxRepeats,
            // إذا نُقلت مهمة منجزة غير متكررة إلى موعد قادم، تعود نشطة
            isCompleted = if (reactivate) false else base.isCompleted,
            completedAt = if (reactivate) null else base.completedAt,
        )
        viewModelScope.launch {
            try {
                val id = repo.saveTask(task)
                onDone(id)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
        return SaveResult.SAVED
    }
}
