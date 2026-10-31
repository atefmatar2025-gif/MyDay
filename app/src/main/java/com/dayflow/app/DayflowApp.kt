package com.dayflow.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dayflow.app.data.AppStateStore
import com.dayflow.app.data.BackupRepository
import com.dayflow.app.data.FocusRepository
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.HistoryRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.DayflowDatabase
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.DataManager
import com.dayflow.app.domain.FocusTimer
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.notification.AlarmScheduler
import com.dayflow.app.notification.Channels
import com.dayflow.app.notification.DailySummaryWorker
import com.dayflow.app.notification.IntentKeys
import com.dayflow.app.notification.MaintenanceWorker
import com.dayflow.app.notification.ReminderScheduler
import com.dayflow.app.widget.DayflowWidgetProvider
import com.dayflow.core.intelligence.AssistantEngine
import com.dayflow.core.intelligence.RuleBasedAssistantEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency injection. A small, explicit container was chosen over Hilt to keep the
 * build simple (one annotation processor - Room) while preserving the same layering:
 * Presentation -> Domain (interactors) -> Data (repositories) / Notification / Engines.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DayflowDatabase = DayflowDatabase.build(appContext)

    val taskRepository = TaskRepository(database)
    val habitRepository = HabitRepository(database)
    val focusRepository = FocusRepository(database)
    val historyRepository = HistoryRepository(database)
    val settingsRepository = SettingsRepository(database)
    val backupRepository = BackupRepository(database)
    val appState = AppStateStore(appContext)
    val alarmLogDao = database.alarmLogDao()

    val alarmScheduler = AlarmScheduler(appContext)
    val reminderScheduler = ReminderScheduler(taskRepository, habitRepository, alarmLogDao, alarmScheduler)

    val taskInteractor = TaskInteractor(
        appContext, database, taskRepository, historyRepository, settingsRepository, reminderScheduler, alarmScheduler,
    )

    /** Swap point for a future on-device language model (see AssistantEngine). */
    val assistantEngine: AssistantEngine = RuleBasedAssistantEngine()
    val assistantService = AssistantService(
        taskRepository, habitRepository, focusRepository, historyRepository, settingsRepository, assistantEngine,
    )

    val focusTimer = FocusTimer(appContext, appState, settingsRepository, focusRepository, taskRepository, taskInteractor, alarmScheduler)

    val dataManager by lazy { DataManager(appContext, this) }
}

class DayflowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.createAll(this)
        publishShortcuts()
        container.appScope.launch {
            container.settingsRepository.current() // makes sure the settings row exists
            container.taskRepository.ensureDefaultCategories()
            container.reminderScheduler.rescheduleAll()
            container.focusTimer.restoreAlarm()
            DailySummaryWorker.schedule(this@DayflowApp, container.settingsRepository.current())
            MaintenanceWorker.schedule(this@DayflowApp)
            DayflowWidgetProvider.refresh(this@DayflowApp)
        }
    }

    /** Launcher long-press quick actions (dynamic, so they work for debug and release ids). */
    private fun publishShortcuts() {
        fun shortcut(id: String, label: Int, dest: String) = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(getString(label))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .putExtra(IntentKeys.EXTRA_DESTINATION, dest),
            )
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("add_task", R.string.action_add_task, IntentKeys.DEST_ADD_TASK),
                    shortcut("save_my_day", R.string.save_my_day, IntentKeys.DEST_SAVE_MY_DAY),
                    shortcut("focus", R.string.focus_mode, IntentKeys.DEST_FOCUS),
                    shortcut("assistant", R.string.ask_dayflow, IntentKeys.DEST_ASSISTANT),
                ),
            )
        }
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as DayflowApp).container
