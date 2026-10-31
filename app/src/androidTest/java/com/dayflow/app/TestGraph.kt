package com.dayflow.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayflow.app.data.BackupRepository
import com.dayflow.app.data.HabitRepository
import com.dayflow.app.data.HistoryRepository
import com.dayflow.app.data.SettingsRepository
import com.dayflow.app.data.TaskRepository
import com.dayflow.app.data.db.DayflowDatabase
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.notification.AlarmScheduler
import com.dayflow.app.notification.ReminderScheduler

/** An isolated object graph on an in-memory database, using the real AlarmManager of the device. */
class TestGraph {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: DayflowDatabase = Room.inMemoryDatabaseBuilder(context, DayflowDatabase::class.java).allowMainThreadQueries().build()
    val tasks = TaskRepository(db)
    val habits = HabitRepository(db)
    val history = HistoryRepository(db)
    val settings = SettingsRepository(db)
    val backup = BackupRepository(db)
    val alarms = AlarmScheduler(context)
    val scheduler = ReminderScheduler(tasks, habits, db.alarmLogDao(), alarms)
    val interactor = TaskInteractor(context, db, tasks, history, settings, scheduler, alarms)

    suspend fun cleanup() {
        tasks.allReminders().forEach { alarms.cancelReminder(it.id) }
        db.close()
    }
}
