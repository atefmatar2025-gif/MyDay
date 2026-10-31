package com.dayflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dayflow.app.MainActivity
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.notification.IntentKeys
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.text.Ar
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Home-screen widget: today's date, next task, remaining tasks, completion % and an add button. */
class DayflowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, DayflowWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val c = app.appContainer()
            c.appScope.launch {
                val tasks = c.taskRepository.snapshot()
                val now = LocalDateTime.now()
                val today = TaskOccurrences.forDate(tasks, now.toLocalDate()).filter { it.status.name != "CANCELLED" }
                val done = today.count { !it.status.isOpen }
                val remaining = today.size - done
                val percent = if (today.isEmpty()) 0 else done * 100 / today.size
                val next = today.filter { it.status.isOpen && it.startAt != null && it.startAt!!.isAfter(now.minusMinutes(1)) }
                    .minByOrNull { it.startAt!! }
                    ?: today.firstOrNull { it.status.isOpen }

                val views = RemoteViews(app.packageName, R.layout.widget_dayflow).apply {
                    setTextViewText(R.id.widget_date, Ar.date(now.toLocalDate()))
                    setTextViewText(R.id.widget_percent, app.getString(R.string.widget_percent, percent))
                    setTextViewText(
                        R.id.widget_next,
                        next?.let { t -> (t.startAt?.let { Ar.time(it.toLocalTime()) + " • " } ?: "") + t.title }
                            ?: app.getString(R.string.widget_no_tasks),
                    )
                    setTextViewText(R.id.widget_remaining, app.getString(R.string.widget_remaining, remaining))
                    setOnClickPendingIntent(R.id.widget_root, open(app, null, 31))
                    setOnClickPendingIntent(R.id.widget_add, open(app, IntentKeys.DEST_ADD_TASK, 32))
                }
                manager.updateAppWidget(ids, views)
            }
        }

        private fun open(context: Context, destination: String?, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                destination?.let { putExtra(IntentKeys.EXTRA_DESTINATION, it) }
            }
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
