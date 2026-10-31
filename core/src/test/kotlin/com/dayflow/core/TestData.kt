package com.dayflow.core

import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.Priority
import com.dayflow.core.model.Task
import com.dayflow.core.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** Builders for tests. Nothing here is used by the app. */
object TestData {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 2) // a Monday

    fun at(hour: Int, minute: Int = 0, date: LocalDate = TODAY): LocalDateTime = date.atTime(hour, minute)

    fun task(
        id: Long,
        title: String = "مهمة $id",
        priority: Priority = Priority.MEDIUM,
        start: LocalDateTime? = null,
        due: LocalDateTime? = null,
        minutes: Int? = 30,
        fixed: Boolean = false,
        status: TaskStatus = TaskStatus.NEW,
        area: LifeArea = LifeArea.PERSONAL,
        dependsOn: List<Long> = emptyList(),
        parentId: Long? = null,
        postponed: Int = 0,
    ) = Task(
        id = id, title = title, priority = priority, startAt = start, dueAt = due,
        estimatedMinutes = minutes, fixedTime = fixed, status = status, area = area,
        dependsOn = dependsOn, parentId = parentId, postponeCount = postponed,
    )
}
