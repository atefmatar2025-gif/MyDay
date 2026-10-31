package com.myday.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, CategoryEntity::class, CompletionEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun completionDao(): CompletionDao

    companion object {
        /**
         * التصنيفات الافتراضية تُضاف مرة واحدة عند إنشاء قاعدة البيانات.
         * (ليست بيانات تجريبية؛ هي تصنيفات جاهزة قابلة للتعديل والحذف.)
         */
        val DEFAULT_CATEGORIES = listOf(
            Triple("العمل", 0xFF2563EBL, "work"),
            Triple("الدراسة", 0xFF7C3AEDL, "study"),
            Triple("العبادة", 0xFF059669L, "worship"),
            Triple("الصحة", 0xFFDC2626L, "health"),
            Triple("المواعيد الشخصية", 0xFFDB2777L, "personal"),
            Triple("الأسرة", 0xFFEA580CL, "family"),
            Triple("التسوق", 0xFFCA8A04L, "shopping"),
            Triple("المهام المنزلية", 0xFF0891B2L, "home"),
        )

        /** الإصدار 2: ربط المهام بأوقات الصلاة. يحافظ على كل البيانات الموجودة. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN anchorPrayer TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN anchorOffsetMin INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** الإصدار 3: وضع الرعاية. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isCare INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** الإصدار 4: المهام التي تُنجز نفسها (رسالة/واتساب/اتصال). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN actionType TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN actionPhone TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN actionContactName TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN actionMessage TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN actionAutoSend INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** الإصدار 5: منبه لا يتوقف إلا بمسح رمز. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN proofCode TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN proofLabel TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "myday.db")
                // لا نستخدم fallbackToDestructiveMigration حتى لا تُفقد البيانات عند التحديث.
                // أي تغيير مستقبلي في البنية يجب أن يرافقه Migration صريح.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        DEFAULT_CATEGORIES.forEachIndexed { index, (name, color, icon) ->
                            db.execSQL(
                                "INSERT INTO categories (name, colorArgb, iconKey, isDefault, sortOrder) VALUES (?, ?, ?, 1, ?)",
                                arrayOf<Any>(name, color, icon, index)
                            )
                        }
                    }
                })
                .build()
    }
}
