package com.myday.app.ui

import com.myday.app.data.isRecurring
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myday.app.backup.BackupException
import com.myday.app.container
import com.myday.app.data.CategoryEntity
import com.myday.app.data.CompletionEntity
import com.myday.app.data.TaskEntity
import com.myday.app.prayer.PrayerConfig
import com.myday.app.prayer.PrayerStore
import com.myday.app.settings.AppSettings
import com.myday.app.settings.CareContact
import com.myday.app.alarm.CareNotifier
import com.myday.app.settings.ThemeMode
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** الحالة المشتركة لكل الشاشات + كل العمليات على المهام. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    private val repo = c.repository
    private val settingsRepo = c.settings

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val tasks: StateFlow<List<TaskEntity>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val completions: StateFlow<List<CompletionEntity>> =
        repo.completions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ساعة تُحدَّث كل 20 ثانية لحساب المتأخر والقادم. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val prayerConfig: StateFlow<PrayerConfig?> = PrayerStore.config

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    private val errors = CoroutineExceptionHandler { _, e ->
        _messages.tryEmit(UiMessage("حدث خطأ: ${e.localizedMessage ?: "غير معروف"}"))
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch(errors) { block() }
    }

    fun message(text: String) { _messages.tryEmit(UiMessage(text)) }

    // ---------- المهام ----------

    fun complete(task: TaskEntity) = launchSafe {
        val previous = repo.complete(task.id) ?: return@launchSafe
        val text = if (task.isRecurring) "أُنجزت \"${task.title}\" — انتقلت للموعد القادم" else "أُنجزت \"${task.title}\" 🎉"
        _messages.emit(UiMessage(text, "تراجع") { launchSafe { repo.restore(previous) } })
    }

    fun uncomplete(task: TaskEntity) = launchSafe { repo.uncomplete(task.id) }

    fun toggleComplete(task: TaskEntity) = if (task.isCompleted) uncomplete(task) else complete(task)

    fun delete(task: TaskEntity) = launchSafe {
        repo.deleteTask(task.id)
        _messages.emit(UiMessage("حُذفت \"${task.title}\"", "تراجع") { launchSafe { repo.reinsert(task) } })
    }

    fun toggleFavorite(task: TaskEntity) = launchSafe { repo.toggleFavorite(task.id) }

    fun postpone(task: TaskEntity, newDueAt: Long, label: String) = launchSafe {
        repo.postpone(task.id, newDueAt)
        _messages.emit(UiMessage("تم تأجيل المهمة $label"))
    }

    // ---------- التصنيفات ----------

    fun saveCategory(category: CategoryEntity) = launchSafe {
        if (category.name.isBlank()) { _messages.emit(UiMessage("اكتب اسم التصنيف")); return@launchSafe }
        val exists = categories.value.any { it.id != category.id && it.name.trim() == category.name.trim() }
        if (exists) { _messages.emit(UiMessage("يوجد تصنيف بهذا الاسم")); return@launchSafe }
        repo.saveCategory(category)
    }

    fun deleteCategory(category: CategoryEntity) = launchSafe {
        repo.deleteCategory(category)
        _messages.emit(UiMessage("حُذف التصنيف \"${category.name}\" — مهامه أصبحت بلا تصنيف"))
    }

    // ---------- الإعدادات ----------

    fun setThemeMode(v: ThemeMode) = launchSafe { settingsRepo.setThemeMode(v) }
    fun setDynamicColor(v: Boolean) = launchSafe { settingsRepo.setDynamicColor(v) }
    fun setUserName(v: String) = launchSafe { settingsRepo.setUserName(v) }
    fun setDefaultSound(uri: String?, name: String) = launchSafe { settingsRepo.setDefaultSound(uri, name) }
    fun setRingDuration(v: Int) = launchSafe { settingsRepo.setRingDuration(v) }
    fun setVibrate(v: Boolean) = launchSafe { settingsRepo.setVibrate(v) }
    fun setGradualVolume(v: Boolean) = launchSafe { settingsRepo.setGradualVolume(v) }
    fun setDefaultReminder(v: Int) = launchSafe { settingsRepo.setDefaultReminder(v) }
    fun setRepeatIfNoResponse(v: Boolean) = launchSafe { settingsRepo.setRepeatIfNoResponse(v) }
    fun setRepeatInterval(v: Int) = launchSafe { settingsRepo.setRepeatInterval(v) }
    fun setMaxRepeats(v: Int) = launchSafe { settingsRepo.setMaxRepeats(v) }
    fun setUse24h(v: Boolean) = launchSafe { settingsRepo.setUse24h(v) }
    fun setTextScale(v: Float) = launchSafe { settingsRepo.setTextScale(v) }
    fun setCareContacts(v: List<CareContact>) = launchSafe { settingsRepo.setCareContacts(v) }
    fun setCareMessage(v: String) = launchSafe { settingsRepo.setCareMessage(v) }

    /** رسالة تجريبية لأول جهة اتصال للتأكد من عمل الإبلاغ. */
    fun sendCareTest() = launchSafe {
        val s = settings.value
        val c = s.careContacts.firstOrNull() ?: run { _messages.emit(UiMessage("أضف جهة اتصال أولاً")); return@launchSafe }
        val text = "رسالة تجريبية من تطبيق يومي: أنت مسجَّل لتلقي تنبيهات الرعاية الخاصة بـ ${s.userName.ifBlank { "صاحب الهاتف" }}."
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CareNotifier.send(getApplication<Application>(), c.phone, text) }
        _messages.emit(UiMessage(if (ok) "أُرسلت رسالة تجريبية إلى ${c.name}" else "تعذر الإرسال — تحقق من صلاحية الرسائل والشريحة"))
    }

    fun finishOnboarding() = launchSafe { settingsRepo.setOnboardingDone(true) }

    fun rescheduleAll() = launchSafe { repo.rescheduleAll() }

    /** حفظ المدينة/طريقة الحساب ثم إعادة حساب مواعيد المهام المرتبطة بالصلاة ومنبهاتها. */
    fun savePrayerConfig(cfg: PrayerConfig, announce: Boolean = true) = launchSafe {
        PrayerStore.save(cfg)
        repo.recomputePrayerTasks()
        if (announce) _messages.emit(UiMessage("تم الحفظ — ${cfg.cityName}"))
    }

    /** ينشئ مهمة اختبار حقيقية يرن منبهها بعد [seconds] ثانية (يطلبها المستخدم بنفسه). */
    fun createTestAlarm(seconds: Int = 60) = launchSafe {
        val s = settings.value
        repo.saveTask(
            TaskEntity(
                title = "اختبار المنبه",
                description = "مهمة اختبار أنشأتها من الإعدادات — يمكنك حذفها بعد التجربة.",
                dueAt = System.currentTimeMillis() + seconds * 1000L,
                alarmEnabled = true,
                soundDurationSec = 30,
                repeatIfNoResponse = s.repeatIfNoResponse,
                repeatIntervalMin = s.repeatIntervalMin,
                maxRepeats = 1,
            )
        )
        _messages.emit(UiMessage("سيرن منبه الاختبار بعد  ثانية — يمكنك إغلاق التطبيق وقفل الشاشة"))
    }

    // ---------- النسخ الاحتياطي ----------

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        try {
            val n = c.backup.export(uri)
            _messages.emit(UiMessage("تم حفظ النسخة الاحتياطية ($n مهمة)"))
        } catch (e: BackupException) {
            _messages.emit(UiMessage(e.message ?: "فشل التصدير"))
        } catch (e: Exception) {
            _messages.emit(UiMessage("فشل التصدير: ${e.localizedMessage}"))
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        try {
            val n = c.backup.import(uri)
            _messages.emit(UiMessage("تمت الاستعادة بنجاح ($n مهمة)"))
        } catch (e: BackupException) {
            _messages.emit(UiMessage(e.message ?: "فشلت الاستعادة"))
        } catch (e: Exception) {
            _messages.emit(UiMessage("فشلت الاستعادة: ${e.localizedMessage}"))
        }
    }
}
