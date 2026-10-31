package com.dayflow.app.domain

import android.content.Context
import android.net.Uri
import com.dayflow.app.AppContainer
import com.dayflow.app.notification.DailySummaryWorker
import com.dayflow.app.widget.DayflowWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Backup, restore, export and "delete all my data" - always keeping alarms consistent. */
class DataManager(private val context: Context, private val c: AppContainer) {

    suspend fun writeBackup(uri: Uri, password: CharArray?) = withContext(Dispatchers.IO) {
        val bytes = c.backupRepository.createBackup(password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot open output")
        c.appState.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun writeExport(uri: Uri) = withContext(Dispatchers.IO) {
        val json = c.backupRepository.exportJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("Cannot open output")
    }

    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open input")
    }

    fun isEncrypted(bytes: ByteArray) = c.backupRepository.isEncrypted(bytes)

    /** Replaces all data with the backup. Throws WrongPasswordException / BackupFormatException. */
    suspend fun restore(bytes: ByteArray, password: CharArray?) = withContext(Dispatchers.IO) {
        cancelAllAlarms()
        c.backupRepository.restore(bytes, password)
        afterBulkChange()
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        cancelAllAlarms()
        c.focusTimer.reset()
        c.backupRepository.deleteEverything()
        c.appState.clearAll()
        afterBulkChange()
    }

    private suspend fun cancelAllAlarms() {
        c.taskRepository.allReminders().forEach { c.alarmScheduler.cancelReminder(it.id) }
        c.habitRepository.all().forEach { c.alarmScheduler.cancelHabit(it.id) }
        c.alarmScheduler.cancelFocusEnd()
    }

    private suspend fun afterBulkChange() {
        c.settingsRepository.current()
        c.taskRepository.ensureDefaultCategories()
        c.reminderScheduler.rescheduleAll()
        DailySummaryWorker.schedule(context, c.settingsRepository.current())
        DayflowWidgetProvider.refresh(context)
    }
}
