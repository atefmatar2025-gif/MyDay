# DAYFLOW release rules. Room, WorkManager and Compose ship their own consumer rules.
# Keep enum names: they are persisted in the database and backup files.
-keepclassmembers enum com.dayflow.** { *; }
-keep class com.dayflow.app.data.db.** { *; }
