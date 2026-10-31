package com.myday.app

import android.app.Application
import android.content.Context
import com.myday.app.alarm.AlarmScheduler
import com.myday.app.alarm.AlarmSyncWorker
import com.myday.app.alarm.NotificationHelper
import com.myday.app.backup.BackupManager
import com.myday.app.data.AppDatabase
import com.myday.app.data.TaskRepository
import com.myday.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** حاوية الاعتمادات (Dependency Injection يدوي بسيط وواضح). */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val scheduler = AlarmScheduler(context)
    val repository = TaskRepository(context, database, scheduler)
    val backup = BackupManager(context, repository)
    /** نطاق عمليات قصيرة يجب ألا تُلغى بإغلاق شاشة. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class MyDayApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        AlarmSyncWorker.enqueue(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as MyDayApp).container
