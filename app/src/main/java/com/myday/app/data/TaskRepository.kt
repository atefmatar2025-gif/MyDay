package com.myday.app.data

import android.content.Context
import androidx.room.withTransaction
import com.myday.app.alarm.AlarmScheduler
import com.myday.app.alarm.CareNotifier
import com.myday.app.alarm.NotificationHelper
import com.myday.app.settings.SettingsRepository
import com.myday.app.util.Recurrence
import com.myday.app.util.TaskTime
import kotlinx.coroutines.flow.Flow

/**
 * نقطة الوصول الوحيدة للبيانات. كل تعديل يُحفظ فوراً في Room
 * ثم يُعاد ضبط منبه المهمة المعنية فقط (بدون تكرار، لأن PendingIntent لكل مهمة فريد).
 */
class TaskRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val settings: SettingsRepository,
) {
    private val taskDao = db.taskDao()
    private val categoryDao = db.categoryDao()
    private val completionDao = db.completionDao()

    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val completions: Flow<List<CompletionEntity>> = completionDao.observeAll()

    fun observeTask(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)
    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

    // ---------------- المهام ----------------

    suspend fun saveTask(task: TaskEntity): Long {
        val now = System.currentTimeMillis()
        val clean = task.copy(
            title = task.title.trim(),
            description = task.description.trim(),
            updatedAt = now,
            snoozedUntil = null,
            ringAttempt = 0,
        )
        val id = if (clean.id == 0L) {
            taskDao.insert(clean.copy(createdAt = now))
        } else {
            val old = taskDao.getById(clean.id)
            taskDao.update(clean.copy(createdAt = old?.createdAt ?: clean.createdAt))
            clean.id
        }
        taskDao.getById(id)?.let { scheduler.schedule(it) }
        return id
    }

    suspend fun deleteTask(id: Long) {
        scheduler.cancel(id)
        NotificationHelper.cancelForTask(context, id)
        taskDao.deleteById(id)
    }

    /** إعادة مهمة محذوفة (زر التراجع). */
    suspend fun reinsert(task: TaskEntity) {
        if (task.categoryId != null && categoryDao.getById(task.categoryId) == null) {
            taskDao.insertAll(listOf(task.copy(categoryId = null)))
        } else {
            taskDao.insertAll(listOf(task))
        }
        taskDao.getById(task.id)?.let { scheduler.schedule(it) }
    }

    /** يحدد المهمة كمكتملة ويرجع نسختها السابقة لإتاحة التراجع. */
    suspend fun complete(id: Long): TaskEntity? {
        val task = taskDao.getById(id) ?: return null
        if (task.isCompleted) return task
        val now = System.currentTimeMillis()
        db.withTransaction {
            completionDao.insert(
                CompletionEntity(
                    taskId = task.id,
                    occurrenceDueAt = task.dueAt,
                    completedAt = now,
                    priorityLevel = task.priority.level,
                )
            )
            if (task.isRecurring) {
                // ننتقل إلى الموعد القادم في السلسلة (بعد الآن وبعد الموعد الحالي)
                val next = TaskTime.firstAfter(task, maxOf(now, task.dueAt))
                    ?: Recurrence.next(task.dueAt, task.repeatType)
                    ?: task.dueAt
                taskDao.update(task.copy(dueAt = next, snoozedUntil = null, ringAttempt = 0, updatedAt = now))
            } else {
                taskDao.update(
                    task.copy(isCompleted = true, completedAt = now, snoozedUntil = null, ringAttempt = 0, updatedAt = now)
                )
            }
        }
        NotificationHelper.cancelForTask(context, id)
        taskDao.getById(id)?.let { scheduler.schedule(it) }
        return task
    }

    /** تراجع عن الإتمام: إعادة المهمة لحالتها السابقة وحذف آخر سجل إنجاز. */
    suspend fun restore(previous: TaskEntity) {
        db.withTransaction {
            if (taskDao.getById(previous.id) == null) return@withTransaction
            completionDao.deleteLatestForTask(previous.id)
            taskDao.update(previous.copy(updatedAt = System.currentTimeMillis()))
        }
        taskDao.getById(previous.id)?.let { scheduler.schedule(it) }
    }

    /** إلغاء الإتمام لمهمة غير متكررة من قائمة المنجزة. */
    suspend fun uncomplete(id: Long) {
        val task = taskDao.getById(id) ?: return
        if (!task.isCompleted) return
        db.withTransaction {
            completionDao.deleteLatestForTask(id)
            taskDao.update(task.copy(isCompleted = false, completedAt = null, updatedAt = System.currentTimeMillis()))
        }
        taskDao.getById(id)?.let { scheduler.schedule(it) }
    }

    suspend fun toggleFavorite(id: Long) {
        val task = taskDao.getById(id) ?: return
        taskDao.update(task.copy(isFavorite = !task.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    /** تأجيل المهمة نفسها إلى موعد جديد. */
    suspend fun postpone(id: Long, newDueAt: Long) {
        val task = taskDao.getById(id) ?: return
        val updated = task.copy(dueAt = newDueAt, snoozedUntil = null, ringAttempt = 0, updatedAt = System.currentTimeMillis())
        taskDao.update(updated)
        NotificationHelper.cancelForTask(context, id)
        scheduler.schedule(updated)
    }

    // ---------------- أحداث المنبه ----------------

    /** غفوة المنبه لعدد من الدقائق. */
    suspend fun snooze(id: Long, minutes: Int) {
        val task = taskDao.getById(id) ?: return
        val updated = task.copy(
            snoozedUntil = System.currentTimeMillis() + minutes * 60_000L,
            ringAttempt = 0,
        )
        taskDao.update(updated)
        NotificationHelper.cancelForTask(context, id)
        scheduler.schedule(updated)
    }

    /** إيقاف المنبه من المستخدم: لا تكرار تلقائي، والمهمة تبقى كما هي. */
    suspend fun stopRinging(id: Long) {
        val task = taskDao.getById(id) ?: return
        val updated = task.copy(snoozedUntil = null, ringAttempt = 0)
        taskDao.update(updated)
        NotificationHelper.cancelForTask(context, id)
        scheduler.schedule(updated)
    }

    /** انتهت مدة الرنين دون استجابة: إعادة الرنين بعد فترة إن كان ذلك مفعلاً. */
    suspend fun onRingTimeout(id: Long) {
        val task = taskDao.getById(id) ?: return
        if (task.isCompleted) return
        // مهام الرعاية تعيد الرنين دائماً (3 مرات على الأقل) قبل الإبلاغ
        val maxRepeats = if (task.isCare) maxOf(task.maxRepeats, 3) else task.maxRepeats
        if ((task.repeatIfNoResponse || task.isCare) && task.ringAttempt < maxRepeats) {
            val updated = task.copy(
                snoozedUntil = System.currentTimeMillis() + task.repeatIntervalMin.coerceAtLeast(1) * 60_000L,
                ringAttempt = task.ringAttempt + 1,
            )
            taskDao.update(updated)
            scheduler.schedule(updated)
        } else {
            val updated = task.copy(snoozedUntil = null, ringAttempt = 0)
            taskDao.update(updated)
            scheduler.schedule(updated)
            // وضع الرعاية: إبلاغ العائلة لأن المهمة لم تُؤكَّد بعد كل المحاولات
            val informed = if (task.isCare) CareNotifier.escalate(context, task, settings.current()) else emptyList()
            NotificationHelper.showMissed(context, task, informed.map { it.name })
        }
    }

    /** بعد انطلاق منبه: مسح الغفوة المنتهية وجدولة الموعد القادم (للمهام المتكررة). */
    suspend fun afterAlarmFired(id: Long, firedFor: Long) {
        val task = taskDao.getById(id) ?: return
        val now = maxOf(System.currentTimeMillis(), firedFor) + 1_000
        val snooze = task.snoozedUntil
        val updated = if (snooze != null && snooze <= now) task.copy(snoozedUntil = null) else task
        if (updated != task) taskDao.update(updated)
        scheduler.schedule(updated, now)
    }

    suspend fun rescheduleTask(id: Long, now: Long = System.currentTimeMillis()) {
        taskDao.getById(id)?.let { scheduler.schedule(it, now) }
    }

    /** إعادة جدولة كل المنبهات (بعد إعادة التشغيل أو تغيير الوقت أو الاستعادة). */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        taskDao.getAll().forEach { task ->
            var t = task
            val s = t.snoozedUntil
            if (s != null && s <= now) {
                t = t.copy(snoozedUntil = null)
                taskDao.update(t)
            }
            if (t.isCompleted) scheduler.cancel(t.id) else scheduler.schedule(t, now)
        }
    }

    /** بعد تغيير المدينة أو طريقة الحساب: إعادة حساب مواعيد المهام المرتبطة بالصلاة. */
    suspend fun recomputePrayerTasks() {
        val now = System.currentTimeMillis()
        taskDao.getActive().filter { it.anchorPrayer != null }.forEach { t ->
            val date = TaskTime.anchorDate(t)
            var due = TaskTime.timeOn(t, date) ?: return@forEach
            // للمهام المتكررة نضمن أن الموعد الحالي لم يصبح في الماضي بسبب التعديل فقط
            if (t.isRecurring && due <= now && t.dueAt > now) {
                due = TaskTime.firstAfter(t.copy(dueAt = due), now) ?: due
            }
            if (due != t.dueAt) {
                val updated = t.copy(dueAt = due, updatedAt = now)
                taskDao.update(updated)
                scheduler.schedule(updated)
            }
        }
    }

    /**
     * تنفيذ رسالة SMS لمهمة «تُنجز نفسها»: إرسال ثم تعليمها منجزة.
     * يرجع true عند تسليم الرسالة لنظام الرسائل.
     */
    suspend fun executeSms(id: Long, overrideText: String? = null): Boolean {
        val task = taskDao.getById(id) ?: return false
        val phone = task.actionPhone ?: return false
        val text = (overrideText ?: task.actionMessage)?.trim().orEmpty()
        if (text.isEmpty()) return false
        val s = settings.current()
        val ok = CareNotifier.send(context, phone, text, s.careSimSubId)
        if (ok) {
            complete(id)
            settings.rememberPhrase(text)
            NotificationHelper.showActionResult(context, task, "أُرسلت الرسالة إلى ${task.actionContactName ?: phone}: «$text»")
        } else {
            NotificationHelper.showActionResult(context, task, "تعذر إرسال الرسالة إلى ${task.actionContactName ?: phone} — تحقق من إذن الرسائل والشريحة")
        }
        return ok
    }

    // ---------------- التصنيفات ----------------

    suspend fun saveCategory(category: CategoryEntity): Long =
        if (category.id == 0L) {
            categoryDao.insert(category.copy(name = category.name.trim(), sortOrder = categoryDao.maxSortOrder() + 1))
        } else {
            categoryDao.update(category.copy(name = category.name.trim()))
            category.id
        }

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    // ---------------- النسخ الاحتياطي ----------------

    suspend fun snapshot(): Triple<List<TaskEntity>, List<CategoryEntity>, List<CompletionEntity>> =
        Triple(taskDao.getAll(), categoryDao.getAll(), completionDao.getAll())

    suspend fun replaceAll(tasks: List<TaskEntity>, categories: List<CategoryEntity>, completions: List<CompletionEntity>) {
        val old = taskDao.getAll()
        old.forEach { scheduler.cancel(it.id) }
        db.withTransaction {
            completionDao.deleteAll()
            taskDao.deleteAll()
            categoryDao.deleteAll()
            categoryDao.insertAll(categories)
            val validCategoryIds = categories.map { it.id }.toSet()
            taskDao.insertAll(tasks.map { if (it.categoryId != null && it.categoryId !in validCategoryIds) it.copy(categoryId = null) else it })
            val validTaskIds = tasks.map { it.id }.toSet()
            completionDao.insertAll(completions.filter { it.taskId in validTaskIds })
        }
        rescheduleAll()
    }
}
