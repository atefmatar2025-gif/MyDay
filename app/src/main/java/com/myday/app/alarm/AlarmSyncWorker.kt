package com.myday.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myday.app.container
import java.util.concurrent.TimeUnit

/**
 * شبكة أمان منخفضة الاستهلاك: كل 12 ساعة تتحقق من أن كل المنبهات مجدولة
 * (مفيد على أجهزة بعض الشركات التي تمسح المنبهات عند "تنظيف" الذاكرة).
 * لا تُستخدم للتوقيت الدقيق — هذا دور AlarmManager.
 */
class AlarmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        applicationContext.container.repository.rescheduleAll()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val NAME = "alarm_sync"
        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlarmSyncWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
