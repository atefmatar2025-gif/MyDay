package com.dayflow.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dayflow.app.data.db.DayflowDatabase
import com.dayflow.app.data.db.HabitEntity
import com.dayflow.app.data.db.Migrations
import com.dayflow.app.domain.ReminderDraft
import com.dayflow.app.domain.TaskDraft
import com.dayflow.core.backup.WrongPasswordException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {
    private lateinit var g: TestGraph

    @Before fun setUp() { g = TestGraph() }
    @After fun tearDown() = runTest { g.cleanup() }

    private suspend fun seed() {
        g.tasks.ensureDefaultCategories()
        val parent = g.interactor.save(TaskDraft(title = "رئيسية", startAt = LocalDate.now().plusDays(1).atTime(10, 0), reminders = listOf(ReminderDraft()), tags = listOf("عمل")))
        g.interactor.save(TaskDraft(title = "فرعية", parentId = parent))
        val h = g.habits.save(HabitEntity(name = "القراءة", createdOn = LocalDate.now().toEpochDay()))
        g.habits.setDone(h, LocalDate.now(), true)
        g.settings.update { it.copy(displayName = "سارة") }
    }

    @Test fun exportImportRoundTripPreservesEverything() = runTest {
        seed()
        val before = g.backup.snapshot().tables
        val json = g.backup.exportJson()
        g.backup.deleteEverything()
        assertTrue(g.tasks.snapshot().isEmpty())
        g.backup.restore(json.toByteArray(), null)
        val after = g.backup.snapshot().tables
        before.keys.forEach { table ->
            assertEquals(table, before[table]!!.size, after[table]!!.size)
        }
        assertEquals("سارة", g.settings.current().displayName)
        val child = g.tasks.snapshot().single { it.title == "فرعية" }
        assertEquals(g.tasks.snapshot().single { it.title == "رئيسية" }.id, child.parentId)
    }

    @Test fun encryptedBackupNeedsPassword() = runTest {
        seed()
        val bytes = g.backup.createBackup("secret123".toCharArray())
        assertTrue(g.backup.isEncrypted(bytes))
        try { g.backup.restore(bytes, "wrong".toCharArray()); fail() } catch (e: WrongPasswordException) { }
        // A failed restore must not have wiped anything.
        assertEquals(2, g.tasks.snapshot().size)
        g.backup.restore(bytes, "secret123".toCharArray())
        assertEquals(2, g.tasks.snapshot().size)
    }

    @Test fun deleteEverythingWipesAllTables() = runTest {
        seed()
        g.backup.deleteEverything()
        assertTrue(g.backup.snapshot().tables.values.all { it.isEmpty() })
    }
}

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DayflowDatabase::class.java)

    /** Opens the exported v1 schema and validates every registered migration up to the current version. */
    @Test fun allMigrationsValidate() {
        helper.createDatabase("migration-test", 1).close()
        Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DayflowDatabase::class.java, "migration-test")
            .addMigrations(*Migrations.ALL)
            .build()
            .apply { openHelper.writableDatabase.close() }
    }
}

/** Smoke test of the main UI on a device: onboarding -> dashboard with the "What should I do now?" card. */
@RunWith(AndroidJUnit4::class)
class MainUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dashboardShowsCoreCards() {
        runBlocking {
            val c = compose.activity.appContainer()
            c.settingsRepository.update { it.copy(onboardingDone = true) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("ماذا أفعل الآن؟") }
        compose.onNodeWithText("ماذا أفعل الآن؟").assertIsDisplayed()
        compose.onNodeWithText("المهام").performClick()
        compose.onNodeWithText("ابحث في المهام").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextExists(text: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
