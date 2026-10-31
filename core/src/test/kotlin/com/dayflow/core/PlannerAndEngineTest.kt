package com.dayflow.core

import com.dayflow.core.TestData.TODAY
import com.dayflow.core.TestData.at
import com.dayflow.core.TestData.task
import com.dayflow.core.intelligence.AssistantAction
import com.dayflow.core.intelligence.AssistantContext
import com.dayflow.core.intelligence.AssistantIntent
import com.dayflow.core.intelligence.ConflictDetector
import com.dayflow.core.intelligence.IntentClassifier
import com.dayflow.core.intelligence.Recommendation
import com.dayflow.core.intelligence.RuleBasedAssistantEngine
import com.dayflow.core.intelligence.TaskOccurrences
import com.dayflow.core.intelligence.TaskRanker
import com.dayflow.core.model.DaySettings
import com.dayflow.core.model.Frequency
import com.dayflow.core.model.HistoryEvent
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.MinuteRange
import com.dayflow.core.model.Priority
import com.dayflow.core.model.ProductivityRecord
import com.dayflow.core.model.RecurrenceRule
import com.dayflow.core.model.TaskStatus
import com.dayflow.core.scheduling.DayPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PlannerAndEngineTest {

    private val settings = DaySettings(
        dayStart = LocalTime.of(8, 0), dayEnd = LocalTime.of(18, 0),
        restPeriods = listOf(MinuteRange(12 * 60, 13 * 60)), bufferMinutes = 0,
    )

    // ---------------- conflicts ----------------

    @Test fun detectsOverlap() {
        val a = task(1, start = at(9), minutes = 60, fixed = true)
        val b = task(2, start = at(9, 30), minutes = 60, fixed = true)
        val c = task(3, start = at(10, 30), minutes = 30, fixed = true)
        val conflicts = ConflictDetector.find(listOf(a, b, c))
        assertEquals(1, conflicts.size)
        assertEquals(30L, conflicts[0].overlapMinutes)
    }

    @Test fun adjacentBlocksDoNotConflict() {
        val a = task(1, start = at(9), minutes = 60)
        val b = task(2, start = at(10), minutes = 60)
        assertTrue(ConflictDetector.find(listOf(a, b)).isEmpty())
    }

    @Test fun completedTasksIgnoredInConflicts() {
        val a = task(1, start = at(9), minutes = 60, status = TaskStatus.COMPLETED)
        val b = task(2, start = at(9), minutes = 60)
        assertTrue(ConflictDetector.find(listOf(a, b)).isEmpty())
    }

    // ---------------- ranking / what now ----------------

    @Test fun urgentOverdueWinsAndBlockedExcluded() {
        val tasks = listOf(
            task(1, priority = Priority.LOW, due = at(17)),
            task(2, priority = Priority.HIGH, due = at(9)), // overdue at 10:00
            task(3, priority = Priority.URGENT, dependsOn = listOf(2)), // blocked by 2
        )
        val ranked = TaskRanker.rank(tasks, at(10), 120)
        assertEquals(2L, ranked.first().task.id)
        assertFalse(ranked.any { it.task.id == 3L })
    }

    @Test fun parentWithOpenSubtasksIsSkipped() {
        val tasks = listOf(task(1, priority = Priority.URGENT), task(2, parentId = 1))
        val ranked = TaskRanker.rank(tasks, at(10), 120)
        assertEquals(listOf(2L), ranked.map { it.task.id })
    }

    @Test fun whatNowRespectsRestPeriod() {
        val rec = TaskRanker.whatNow(listOf(task(1)), at(12, 15), settings)
        assertEquals(Recommendation.Kind.REST, rec.kind)
    }

    @Test fun whatNowPreparesForImminentAppointment() {
        val rec = TaskRanker.whatNow(listOf(task(1), task(2, start = at(10, 5), fixed = true)), at(10), settings)
        assertEquals(Recommendation.Kind.PREPARE_APPOINTMENT, rec.kind)
        assertEquals(2L, rec.task?.id)
    }

    @Test fun whatNowFreeTimeStopsAtNextAppointment() {
        val rec = TaskRanker.whatNow(listOf(task(1, minutes = 20), task(2, start = at(11), fixed = true)), at(10), settings)
        assertEquals(Recommendation.Kind.TASK, rec.kind)
        assertEquals(60L, rec.freeMinutes)
    }

    // ---------------- Save My Day planner ----------------

    @Test fun plansAroundFixedAndRest() {
        val tasks = listOf(
            task(1, start = at(9), minutes = 60, fixed = true),
            task(2, priority = Priority.HIGH, due = at(17), minutes = 60, start = at(8)),
            task(3, priority = Priority.LOW, minutes = 30, start = at(8)),
        )
        val plan = DayPlanner.plan(tasks, TODAY, at(8), settings)
        assertTrue(plan.fitsEverything)
        // No block may overlap the fixed appointment or the rest period.
        plan.blocks.forEach { b ->
            assertTrue(!b.start.isBefore(at(10)) || !b.end.isAfter(at(9)))
            assertTrue(!b.start.isBefore(at(13)) || !b.end.isAfter(at(12)))
        }
        assertEquals(2L, plan.blocks.first().task.id) // high priority first
    }

    @Test fun reportsHonestlyWhenTimeIsNotEnough() {
        val tasks = listOf(
            task(1, priority = Priority.URGENT, due = at(17), minutes = 60, start = at(8)),
            task(2, priority = Priority.HIGH, due = at(17), minutes = 60, start = at(8)),
            task(3, priority = Priority.LOW, minutes = 60, start = at(8)),
        )
        val plan = DayPlanner.plan(tasks, TODAY, at(16), settings) // only 2 hours left
        assertFalse(plan.fitsEverything)
        assertEquals(listOf(1L, 2L), plan.blocks.map { it.task.id })
        assertEquals(listOf(3L), plan.suggestPostpone.map { it.id })
        assertTrue(plan.summary().contains("لا يكفي"))
    }

    @Test fun importantTaskThatDoesNotFitIsAtRisk() {
        val tasks = listOf(task(1, priority = Priority.URGENT, due = at(17), minutes = 300, start = at(8)))
        val plan = DayPlanner.plan(tasks, TODAY, at(16), settings.copy(maxChunkMinutes = 600))
        assertEquals(listOf(1L), plan.atRisk.map { it.id })
    }

    @Test fun splitsLongTasksIntoParts() {
        val tasks = listOf(task(1, minutes = 180, start = at(8)))
        val plan = DayPlanner.plan(tasks, TODAY, at(8), settings)
        assertTrue(plan.blocks.size >= 2)
        assertTrue(plan.blocks.all { it.minutes <= settings.maxChunkMinutes })
        assertEquals(180L, plan.blocks.sumOf { it.minutes })
        assertEquals(plan.blocks.size, plan.blocks.first().totalParts)
    }

    @Test fun dependencyPlacedBeforeDependant() {
        val tasks = listOf(
            task(1, priority = Priority.URGENT, minutes = 30, start = at(8), dependsOn = listOf(2)),
            task(2, priority = Priority.LOW, minutes = 30, start = at(8)),
        )
        val plan = DayPlanner.plan(tasks, TODAY, at(8), settings)
        val first = plan.blocks.first { it.task.id == 2L }
        val second = plan.blocks.first { it.task.id == 1L }
        assertFalse(second.start.isBefore(first.end))
    }

    @Test fun overdueFromYesterdayIsCarriedIntoToday() {
        val tasks = listOf(task(1, priority = Priority.MEDIUM, start = at(9, date = TODAY.minusDays(1)), minutes = 30))
        val plan = DayPlanner.plan(tasks, TODAY, at(8), settings)
        assertEquals(listOf(1L), plan.blocks.map { it.task.id })
    }

    @Test fun recurringOccurrencesAppearOnTheirDays() {
        val t = task(1, start = at(7, date = TODAY.minusDays(7))).copy(recurrence = RecurrenceRule(Frequency.WEEKLY))
        assertEquals(1, TaskOccurrences.forDate(listOf(t), TODAY).size)
        assertEquals(at(7), TaskOccurrences.forDate(listOf(t), TODAY)[0].startAt)
        assertEquals(0, TaskOccurrences.forDate(listOf(t), TODAY.plusDays(1)).size)
    }

    @Test fun planIsDeterministic() {
        val tasks = (1L..8L).map { task(it, minutes = 45, start = at(8), priority = Priority.entries[(it % 4).toInt()]) }
        assertEquals(DayPlanner.plan(tasks, TODAY, at(8), settings), DayPlanner.plan(tasks.shuffled(), TODAY, at(8), settings))
    }

    @Test fun fullDayStress() {
        val tasks = (1L..200L).map { task(it, minutes = 30, start = at(8)) }
        val plan = DayPlanner.plan(tasks, TODAY, at(8), settings)
        assertTrue(plan.plannedMinutes <= plan.freeMinutes)
        assertEquals(200, plan.blocks.size + plan.suggestPostpone.size + plan.atRisk.size)
    }

    // ---------------- intents & engine ----------------

    @Test fun classifiesAllSpecQuestions() {
        val expected = listOf(
            AssistantIntent.WHAT_NOW, AssistantIntent.TOP_TASKS, AssistantIntent.REMAINING,
            AssistantIntent.OVERDUE, AssistantIntent.PLAN_TOMORROW, AssistantIntent.HOURS_BY_AREA,
            AssistantIntent.MOST_POSTPONED, AssistantIntent.CONFLICTS, AssistantIntent.HOW_TO_FINISH,
            AssistantIntent.WEEK_REPORT,
        )
        RuleBasedAssistantEngine.SUGGESTED_QUESTIONS.zip(expected).forEach { (q, intent) ->
            assertEquals(q, intent, IntentClassifier.classify(q))
        }
        assertEquals(LifeArea.STUDY, IntentClassifier.areaOf("كم ساعة خصصت للدراسة هذا الأسبوع؟"))
    }

    @Test fun engineAnswersFromRealDataOnly() {
        val engine = RuleBasedAssistantEngine()
        val empty = AssistantContext(now = at(10), tasks = emptyList(), settings = settings)
        assertTrue(engine.answer("ما المهام التي تأخرت فيها؟", empty).text.contains("لا توجد"))

        val ctx = AssistantContext(
            now = at(10), settings = settings,
            tasks = listOf(task(1, title = "تقرير", due = at(9)), task(2, title = "قراءة", start = at(15))),
        )
        val overdue = engine.answer("ما المهام التي تأخرت فيها؟", ctx)
        assertTrue(overdue.bullets.single().startsWith("تقرير"))
        assertFalse(engine.isLanguageModel)
    }

    @Test fun planTomorrowNeverChangesDataAndOffersReview() {
        val engine = RuleBasedAssistantEngine()
        val tasks = listOf(task(1, title = "مذاكرة", start = at(9, date = TODAY.plusDays(1)), minutes = 60))
        val ctx = AssistantContext(now = at(20), tasks = tasks, settings = settings)
        val reply = engine.answer("نظّم لي يوم الغد", ctx)
        assertEquals(AssistantIntent.PLAN_TOMORROW, reply.intent)
        assertTrue(reply.actions.any { it is AssistantAction.ReviewPlan && it.date == TODAY.plusDays(1) })
        assertEquals(tasks, ctx.tasks) // immutable snapshot untouched
    }

    @Test fun studyHoursUsesHistory() {
        val engine = RuleBasedAssistantEngine()
        val history = listOf(
            ProductivityRecord(1, 1, "مراجعة", LifeArea.STUDY, HistoryEvent.COMPLETED, at(9), 60, 90),
            ProductivityRecord(2, 2, "تقرير", LifeArea.WORK, HistoryEvent.COMPLETED, at(9), 60, 30),
        )
        val ctx = AssistantContext(now = at(20), tasks = emptyList(), history = history, settings = settings)
        val reply = engine.answer("كم ساعة خصصت للدراسة هذا الأسبوع؟", ctx)
        assertTrue(reply.bullets.any { it.contains("ساعة و30 دقيقة") })
    }

    @Test fun mostPostponedFromHistory() {
        val engine = RuleBasedAssistantEngine()
        val history = (1..3).map { ProductivityRecord(it.toLong(), 7, "تنظيف المكتب", LifeArea.PERSONAL, HistoryEvent.POSTPONED, at(9)) }
        val reply = engine.answer("ما المهام التي أجلتها أكثر من مرة؟", AssistantContext(at(10), emptyList(), history = history))
        assertTrue(reply.bullets.first().contains("تنظيف المكتب"))
    }

    @Test fun unknownQuestionIsHonest() {
        val reply = RuleBasedAssistantEngine().answer("ما عاصمة فرنسا", AssistantContext(at(10), emptyList()))
        assertEquals(AssistantIntent.UNKNOWN, reply.intent)
        assertTrue(reply.text.contains("لست نموذجاً لغوياً"))
    }
}
