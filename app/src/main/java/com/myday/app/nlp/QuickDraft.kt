package com.myday.app.nlp

import com.myday.app.data.TaskEntity

/** مسودة تنتقل من الإضافة السريعة إلى شاشة التعديل الكاملة. */
object QuickDraft {
    @Volatile private var pending: TaskEntity? = null
    fun put(task: TaskEntity) { pending = task }
    fun take(): TaskEntity? = pending.also { pending = null }
}
