package com.myday.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Priority(val level: Int, val label: String) {
    LOW(0, "منخفضة"),
    MEDIUM(1, "متوسطة"),
    HIGH(2, "مرتفعة");

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class RepeatType(val label: String) {
    NONE("بدون تكرار"),
    DAILY("يومياً"),
    WEEKLY("أسبوعياً"),
    MONTHLY("شهرياً");

    companion object {
        fun fromName(name: String?): RepeatType = entries.firstOrNull { it.name == name } ?: NONE
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val iconKey: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("categoryId"), Index("dueAt"), Index("isCompleted")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** موعد المهمة (أو موعد التكرار القادم للمهام المتكررة) بالمللي ثانية */
    val dueAt: Long,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val isFavorite: Boolean = false,
    val repeatType: RepeatType = RepeatType.NONE,
    /** منبه صوتي حقيقي (شاشة كاملة + صوت متكرر) في وقت المهمة */
    val alarmEnabled: Boolean = true,
    /** تذكير مسبق (إشعار) قبل الموعد بعدد الدقائق؛ 0 = بدون */
    val reminderMinutesBefore: Int = 0,
    /** null = استخدام النغمة الافتراضية من الإعدادات */
    val soundUri: String? = null,
    val soundName: String? = null,
    val soundDurationSec: Int = 60,
    val repeatIfNoResponse: Boolean = true,
    val repeatIntervalMin: Int = 5,
    val maxRepeats: Int = 3,
    val snoozedUntil: Long? = null,
    val ringAttempt: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** خاصية محسوبة خارج الكيان حتى لا تعتبرها Room عموداً. */
val TaskEntity.isRecurring: Boolean get() = repeatType != RepeatType.NONE

/** سجل الإنجاز — يسمح بإحصاء كل مرة تُنجز فيها مهمة متكررة */
@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("taskId"), Index("occurrenceDueAt"), Index("completedAt")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val occurrenceDueAt: Long,
    val completedAt: Long,
    @ColumnInfo(defaultValue = "1") val priorityLevel: Int = 1,
)
