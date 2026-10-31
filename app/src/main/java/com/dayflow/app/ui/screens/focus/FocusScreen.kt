package com.dayflow.app.ui.screens.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.data.AppStateStore
import com.dayflow.app.data.FocusRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.FocusSessionEntity
import com.dayflow.app.domain.FocusTimer
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.ProgressRing
import com.dayflow.app.ui.components.SectionHeader
import com.dayflow.app.ui.components.StatTile
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.model.Task
import com.dayflow.core.text.Ar
import com.dayflow.core.time.LocalTimes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Random

/** Background noise generated on the device (original, licence-free audio). Plays only while this screen is open. */
enum class NoiseType { NONE, WHITE, PINK, BROWN }

class NoisePlayer {
    @Volatile private var running = false
    @Volatile var volume: Float = 0.4f
        set(value) { field = value; track?.setVolume(value) }
    private var thread: Thread? = null
    @Volatile private var track: AudioTrack? = null

    fun start(type: NoiseType) {
        stop()
        if (type == NoiseType.NONE) return
        running = true
        thread = Thread {
            val rate = 22_050
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            t.setVolume(volume)
            t.play()
            val rnd = Random()
            val buf = ShortArray(2048)
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var brown = 0.0
            while (running) {
                for (i in buf.indices) {
                    val w = rnd.nextDouble() * 2 - 1
                    val v = when (type) {
                        NoiseType.WHITE -> w * 0.25
                        NoiseType.PINK -> { // Paul Kellet's economy pink filter
                            b0 = 0.99765 * b0 + w * 0.0990460
                            b1 = 0.96300 * b1 + w * 0.2965164
                            b2 = 0.57000 * b2 + w * 1.0526913
                            (b0 + b1 + b2 + w * 0.1848) * 0.08
                        }
                        NoiseType.BROWN -> { brown = (brown + 0.02 * w) / 1.02; brown * 3.0 }
                        NoiseType.NONE -> 0.0
                    }
                    buf[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                t.write(buf, 0, buf.size)
            }
            runCatching { t.stop(); t.release() }
            track = null
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(400)
        thread = null
    }
}

data class FocusUiState(
    val timer: AppStateStore.FocusState = AppStateStore.FocusState(),
    val tasks: List<Task> = emptyList(),
    val todayMinutes: Int = 0,
    val todaySessions: List<FocusSessionEntity> = emptyList(),
    val focusMinutes: Int = 25,
    val shortBreak: Int = 5,
    val longBreak: Int = 15,
    val cyclesBeforeLong: Int = 4,
)

class FocusViewModel(
    private val timer: FocusTimer,
    tasks: TaskRepository,
    focus: FocusRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val noise = NoisePlayer()

    val state: StateFlow<FocusUiState> = combine(timer.state, tasks.tasks, focus.sessionEntities, settings.settings) { t, all, sessions, s ->
        val todayStart = LocalTimes.toEpochSecond(LocalDate.now().atStartOfDay())!!
        val today = sessions.filter { it.startedAt >= todayStart }
        FocusUiState(
            timer = t,
            tasks = all.filter { it.status.isOpen && !it.archived },
            todayMinutes = today.filter { it.type == "FOCUS" }.sumOf { it.actualMinutes },
            todaySessions = today,
            focusMinutes = s.focusMinutes, shortBreak = s.shortBreakMinutes, longBreak = s.longBreakMinutes,
            cyclesBeforeLong = s.cyclesBeforeLongBreak,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start(taskId: Long?, minutes: Int) = viewModelScope.launch { timer.startFocus(taskId, minutes) }
    fun startBreak(long: Boolean) = viewModelScope.launch { timer.startBreak(long) }
    fun pause() = viewModelScope.launch { timer.pause() }
    fun resume() = viewModelScope.launch { timer.resume() }
    fun stop() = viewModelScope.launch { timer.stop() }
    fun finishIfDue() = viewModelScope.launch { timer.onPhaseFinished() }
    fun reset() = viewModelScope.launch { timer.reset() }
    fun saveDefaultMinutes(m: Int) = viewModelScope.launch { settings.update { it.copy(focusMinutes = m) } }

    override fun onCleared() {
        noise.stop()
        super.onCleared()
    }
}

@Composable
fun FocusScreen(nav: NavController, initialTaskId: Long?) {
    val vm = dfViewModel { FocusViewModel(it.focusTimer, it.taskRepository, it.focusRepository, it.settingsRepository) }
    val s by vm.state.collectAsStateWithLifecycle()
    val t = s.timer
    var selectedTask by remember { mutableLongStateOf(initialTaskId ?: 0L) }
    var minutes by remember(s.focusMinutes) { mutableIntStateOf(s.focusMinutes) }
    var taskMenu by remember { mutableStateOf(false) }
    var noiseType by remember { mutableStateOf(NoiseType.NONE) }
    var noiseVolume by remember { mutableFloatStateOf(0.4f) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val running = t.phase in FocusTimer.RUNNING
    LaunchedEffect(running, t.isPaused, t.endsAt) {
        while (running && !t.isPaused) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= t.endsAt) { vm.finishIfDue(); break }
            delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { vm.noise.stop() } }

    val remainingMs = when {
        !running -> minutes * 60_000L
        t.isPaused -> t.pausedRemainingMs
        else -> (t.endsAt - nowMs).coerceAtLeast(0)
    }
    val total = (if (running) t.plannedMinutes else minutes).coerceAtLeast(1) * 60_000f
    val progress = 1f - remainingMs / total
    val currentTask = s.tasks.firstOrNull { it.id == (if (running) t.taskId else selectedTask) }

    Column(Modifier.fillMaxSize()) {
        DfTopBar(stringResource(R.string.focus_mode), onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(
                    when (t.phase) {
                        FocusTimer.FOCUS -> R.string.phase_focus
                        FocusTimer.BREAK -> R.string.phase_break
                        FocusTimer.LONG_BREAK -> R.string.phase_long_break
                        FocusTimer.FOCUS_DONE -> R.string.phase_focus_done
                        FocusTimer.BREAK_DONE -> R.string.phase_break_done
                        else -> R.string.phase_ready
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            ProgressRing(
                if (running) progress else 0f, size = 240.dp, stroke = 14.dp,
                color = if (t.phase == FocusTimer.FOCUS || !running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val sec = (remainingMs / 1000).toInt()
                    Text("%02d:%02d".format(sec / 60, sec % 60), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.cycle_x, t.cycle % s.cyclesBeforeLong.coerceAtLeast(1), s.cyclesBeforeLong), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                currentTask?.title ?: stringResource(R.string.no_task_selected),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!running) {
                Box {
                    OutlinedButton(onClick = { taskMenu = true }, enabled = s.tasks.isNotEmpty()) { Text(stringResource(R.string.choose_task)) }
                    DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.no_task_selected)) }, onClick = { selectedTask = 0; taskMenu = false })
                        s.tasks.take(40).forEach { task ->
                            DropdownMenuItem(text = { Text(task.title, maxLines = 1) }, onClick = { selectedTask = task.id; taskMenu = false })
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(listOf(15, 25, 45, 50, 60, 90)) { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(stringResource(R.string.minutes_short, m)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = { vm.start(selectedTask.takeIf { it > 0 }, minutes) }) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.start_focus_session))
                    }
                    if (t.phase == FocusTimer.FOCUS_DONE) {
                        val long = t.cycle % s.cyclesBeforeLong.coerceAtLeast(1) == 0
                        FilledTonalButton(onClick = { vm.startBreak(long) }) {
                            Icon(Icons.Filled.Coffee, null); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.start_break_x, if (long) s.longBreak else s.shortBreak))
                        }
                    }
                }
                if (minutes != s.focusMinutes) {
                    androidx.compose.material3.TextButton(onClick = { vm.saveDefaultMinutes(minutes) }) { Text(stringResource(R.string.make_default)) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (t.isPaused) {
                        Button(onClick = { vm.resume() }) { Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.resume)) }
                    } else {
                        FilledTonalButton(onClick = { vm.pause() }) { Icon(Icons.Filled.Pause, null); Text(stringResource(R.string.pause)) }
                    }
                    OutlinedButton(onClick = { vm.stop() }) { Icon(Icons.Filled.Stop, null); Text(stringResource(R.string.stop_session)) }
                }
                Text(stringResource(R.string.focus_background_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- background sound ----
            DfCard {
                Text(stringResource(R.string.background_sound), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(NoiseType.entries) { n ->
                        FilterChip(
                            selected = noiseType == n,
                            onClick = { noiseType = n; vm.noise.volume = noiseVolume; vm.noise.start(n) },
                            label = {
                                Text(stringResource(when (n) { NoiseType.NONE -> R.string.noise_none; NoiseType.WHITE -> R.string.noise_white; NoiseType.PINK -> R.string.noise_pink; NoiseType.BROWN -> R.string.noise_brown }))
                            },
                        )
                    }
                }
                if (noiseType != NoiseType.NONE) {
                    Slider(value = noiseVolume, onValueChange = { noiseVolume = it; vm.noise.volume = it }, valueRange = 0.05f..1f)
                }
                Text(stringResource(R.string.noise_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- today ----
            SectionHeader(stringResource(R.string.focus_today))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                StatTile(stringResource(R.string.focus_minutes), Ar.duration(s.todayMinutes), Modifier.weight(1f))
                StatTile(stringResource(R.string.focus_sessions), s.todaySessions.count { it.type == "FOCUS" }.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.completed_sessions), s.todaySessions.count { it.type == "FOCUS" && it.completed }.toString(), Modifier.weight(1f))
            }
            s.todaySessions.filter { it.type == "FOCUS" }.take(10).forEach { fs ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        Ar.time(LocalTimes.fromEpochSecond(fs.startedAt)!!.toLocalTime()), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(fs.taskTitle ?: stringResource(R.string.no_task_selected), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(Ar.duration(fs.actualMinutes) + if (fs.completed) " ✓" else "", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
