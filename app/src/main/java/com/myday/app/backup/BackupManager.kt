package com.myday.app.backup

import android.content.Context
import android.net.Uri
import com.myday.app.data.CategoryEntity
import com.myday.app.data.CompletionEntity
import com.myday.app.data.Priority
import com.myday.app.data.RepeatType
import com.myday.app.data.TaskEntity
import com.myday.app.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupException(message: String) : Exception(message)

/** تصدير/استعادة كل البيانات بصيغة JSON عبر منتقي الملفات (بدون أي صلاحية تخزين). */
class BackupManager(private val context: Context, private val repo: TaskRepository) {

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val (tasks, categories, completions) = repo.snapshot()
        val root = JSONObject()
            .put("app", "MyDay")
            .put("format", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
            .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
            .put("completions", JSONArray().apply { completions.forEach { put(it.toJson()) } })
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("تعذر فتح الملف للكتابة")
        out.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        tasks.size
    }

    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw BackupException("تعذر قراءة الملف")
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupException("الملف ليس نسخة احتياطية صالحة") }
        if (root.optString("app") != "MyDay") throw BackupException("الملف لا يخص تطبيق يومي")
        if (root.optInt("format", 0) > FORMAT_VERSION) throw BackupException("النسخة أحدث من التطبيق؛ حدّث التطبيق أولاً")
        try {
            val categories = root.getJSONArray("categories").map { categoryFrom(it) }
            val tasks = root.getJSONArray("tasks").map { taskFrom(it) }
            val completions = root.optJSONArray("completions")?.map { completionFrom(it) } ?: emptyList()
            repo.replaceAll(tasks, categories, completions)
            tasks.size
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("الملف تالف أو ناقص")
        }
    }

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun CategoryEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("colorArgb", colorArgb)
        .put("iconKey", iconKey).put("isDefault", isDefault).put("sortOrder", sortOrder)

    private fun categoryFrom(o: JSONObject) = CategoryEntity(
        id = o.getLong("id"), name = o.getString("name"), colorArgb = o.getLong("colorArgb"),
        iconKey = o.optString("iconKey", "star"), isDefault = o.optBoolean("isDefault"), sortOrder = o.optInt("sortOrder"),
    )

    private fun TaskEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("description", description).put("dueAt", dueAt)
        .put("priority", priority.level).put("categoryId", categoryId ?: JSONObject.NULL)
        .put("isCompleted", isCompleted).put("completedAt", completedAt ?: JSONObject.NULL)
        .put("isFavorite", isFavorite).put("repeatType", repeatType.name)
        .put("alarmEnabled", alarmEnabled).put("reminderMinutesBefore", reminderMinutesBefore)
        .put("soundUri", soundUri ?: JSONObject.NULL).put("soundName", soundName ?: JSONObject.NULL)
        .put("soundDurationSec", soundDurationSec).put("repeatIfNoResponse", repeatIfNoResponse)
        .put("repeatIntervalMin", repeatIntervalMin).put("maxRepeats", maxRepeats)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("anchorPrayer", anchorPrayer ?: JSONObject.NULL).put("anchorOffsetMin", anchorOffsetMin)
        .put("isCare", isCare)
        .put("actionType", actionType ?: JSONObject.NULL).put("actionPhone", actionPhone ?: JSONObject.NULL)
        .put("actionContactName", actionContactName ?: JSONObject.NULL).put("actionMessage", actionMessage ?: JSONObject.NULL)
        .put("actionAutoSend", actionAutoSend)

    private fun taskFrom(o: JSONObject) = TaskEntity(
        id = o.getLong("id"),
        title = o.getString("title"),
        description = o.optString("description", ""),
        dueAt = o.getLong("dueAt"),
        priority = Priority.fromLevel(o.optInt("priority", 1)),
        categoryId = o.optLongOrNull("categoryId"),
        isCompleted = o.optBoolean("isCompleted"),
        completedAt = o.optLongOrNull("completedAt"),
        isFavorite = o.optBoolean("isFavorite"),
        repeatType = RepeatType.fromName(o.optString("repeatType")),
        alarmEnabled = o.optBoolean("alarmEnabled", true),
        reminderMinutesBefore = o.optInt("reminderMinutesBefore", 0),
        soundUri = o.optStringOrNull("soundUri"),
        soundName = o.optStringOrNull("soundName"),
        soundDurationSec = o.optInt("soundDurationSec", 60),
        repeatIfNoResponse = o.optBoolean("repeatIfNoResponse", true),
        repeatIntervalMin = o.optInt("repeatIntervalMin", 5),
        maxRepeats = o.optInt("maxRepeats", 3),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        anchorPrayer = o.optStringOrNull("anchorPrayer"),
        anchorOffsetMin = o.optInt("anchorOffsetMin", 0),
        isCare = o.optBoolean("isCare", false),
        actionType = o.optStringOrNull("actionType"),
        actionPhone = o.optStringOrNull("actionPhone"),
        actionContactName = o.optStringOrNull("actionContactName"),
        actionMessage = o.optStringOrNull("actionMessage"),
        actionAutoSend = o.optBoolean("actionAutoSend", true),
    )

    private fun CompletionEntity.toJson() = JSONObject()
        .put("id", id).put("taskId", taskId).put("occurrenceDueAt", occurrenceDueAt)
        .put("completedAt", completedAt).put("priorityLevel", priorityLevel)

    private fun completionFrom(o: JSONObject) = CompletionEntity(
        id = o.getLong("id"), taskId = o.getLong("taskId"), occurrenceDueAt = o.getLong("occurrenceDueAt"),
        completedAt = o.getLong("completedAt"), priorityLevel = o.optInt("priorityLevel", 1),
    )

    companion object { const val FORMAT_VERSION = 1 }
}
