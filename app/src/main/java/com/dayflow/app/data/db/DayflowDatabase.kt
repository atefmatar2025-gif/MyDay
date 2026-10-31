package com.dayflow.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TaskCategoryEntity::class, TaskEntity::class, TaskTagEntity::class, TaskTagCrossRef::class,
        TaskDependencyEntity::class, TaskAttachmentEntity::class, TaskReminderEntity::class, AlarmLogEntity::class,
        HabitEntity::class, HabitLogEntity::class, FocusSessionEntity::class, ProductivityRecordEntity::class,
        DailyPlanEntity::class, DailyPlanItemEntity::class, UserSettingsEntity::class, RestPeriodEntity::class,
        AreaGoalEntity::class,
    ],
    version = DayflowDatabase.VERSION,
    exportSchema = true,
)
abstract class DayflowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun alarmLogDao(): AlarmLogDao
    abstract fun habitDao(): HabitDao
    abstract fun focusDao(): FocusDao
    abstract fun historyDao(): HistoryDao
    abstract fun planDao(): PlanDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "dayflow.db"

        /** Children first, so foreign keys never block a full wipe. */
        val TABLES_DELETE_ORDER = listOf(
            "daily_plan_items", "daily_plans", "task_reminders", "task_attachments", "task_dependencies",
            "task_tag_refs", "task_tags", "alarm_log", "habit_logs", "habits", "focus_sessions",
            "productivity_records", "tasks", "task_categories", "rest_periods", "area_goals", "user_settings",
        )

        fun build(context: Context): DayflowDatabase =
            Room.databaseBuilder(context, DayflowDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // Deliberately NO fallbackToDestructiveMigration(): a missing migration must fail
                // loudly in testing rather than silently wipe the user's data.
                .build()
    }
}

/**
 * Explicit, tested migrations. Every schema change bumps [DayflowDatabase.VERSION], adds a
 * Migration here, and a test in MigrationTest (androidTest) that uses the exported schema JSON.
 *
 * Example for the next version:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE tasks ADD COLUMN colorHex TEXT")
 *     }
 * }
 * ```
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
