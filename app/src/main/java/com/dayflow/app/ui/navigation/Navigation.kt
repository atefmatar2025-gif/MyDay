package com.dayflow.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dayflow.app.AppContainer
import com.dayflow.app.R
import com.dayflow.app.appContainer
import com.dayflow.app.notification.IntentKeys
import com.dayflow.app.ui.screens.assistant.AssistantScreen
import com.dayflow.app.ui.screens.calendar.CalendarScreen
import com.dayflow.app.ui.screens.dashboard.DashboardScreen
import com.dayflow.app.ui.screens.focus.FocusScreen
import com.dayflow.app.ui.screens.habits.HabitsScreen
import com.dayflow.app.ui.screens.insights.BalanceScreen
import com.dayflow.app.ui.screens.insights.MemoryScreen
import com.dayflow.app.ui.screens.insights.StatsScreen
import com.dayflow.app.ui.screens.more.MoreScreen
import com.dayflow.app.ui.screens.savemyday.SaveMyDayScreen
import com.dayflow.app.ui.screens.settings.AlarmLogScreen
import com.dayflow.app.ui.screens.settings.CategoriesScreen
import com.dayflow.app.ui.screens.settings.PermissionsScreen
import com.dayflow.app.ui.screens.settings.SettingsScreen
import com.dayflow.app.ui.screens.tasks.TaskDetailScreen
import com.dayflow.app.ui.screens.tasks.TaskEditorScreen
import com.dayflow.app.ui.screens.tasks.TasksScreen
import com.dayflow.app.ui.screens.timeline.TimelineScreen

/** ViewModel creation through the manual DI container. */
@Composable
inline fun <reified VM : ViewModel> dfViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

object Routes {
    const val DASHBOARD = "dashboard"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val HABITS = "habits"
    const val MORE = "more"
    const val TASK = "task/{id}"
    const val EDITOR = "edit?id={id}&date={date}&voice={voice}&parent={parent}"
    const val TIMELINE = "timeline?date={date}"
    const val ASSISTANT = "assistant"
    const val FOCUS = "focus?taskId={taskId}"
    const val SAVE_MY_DAY = "savemyday?date={date}"
    const val STATS = "stats"
    const val MEMORY = "memory"
    const val BALANCE = "balance"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val ALARM_LOG = "alarmlog"
    const val CATEGORIES = "categories"

    fun task(id: Long) = "task/$id"
    fun editor(id: Long = 0, date: Long = Long.MIN_VALUE, voice: Boolean = false, parent: Long = 0) =
        "edit?id=$id&date=$date&voice=$voice&parent=$parent"
    fun timeline(epochDay: Long = Long.MIN_VALUE) = "timeline?date=$epochDay"
    fun focus(taskId: Long = 0) = "focus?taskId=$taskId"
    fun saveMyDay(epochDay: Long = Long.MIN_VALUE) = "savemyday?date=$epochDay"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, R.string.nav_home, Icons.Filled.Home),
    Tab(Routes.TASKS, R.string.nav_tasks, Icons.AutoMirrored.Filled.List),
    Tab(Routes.CALENDAR, R.string.nav_calendar, Icons.Filled.DateRange),
    Tab(Routes.HABITS, R.string.nav_habits, Icons.Filled.Loop),
    Tab(Routes.MORE, R.string.nav_more, Icons.Filled.Apps),
)

/** A deep link coming from a notification, widget or launcher shortcut. */
data class DeepLink(val destination: String?, val taskId: Long, val token: Long)

@Composable
fun DayflowNavHost(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit, nav: NavHostController = rememberNavController()) {
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        when (link.destination) {
            IntentKeys.DEST_TASK -> if (link.taskId > 0) nav.navigate(Routes.task(link.taskId))
            IntentKeys.DEST_ADD_TASK -> nav.navigate(Routes.editor())
            IntentKeys.DEST_ASSISTANT -> nav.navigate(Routes.ASSISTANT)
            IntentKeys.DEST_FOCUS -> nav.navigate(Routes.focus())
            IntentKeys.DEST_SAVE_MY_DAY -> nav.navigate(Routes.saveMyDay())
            IntentKeys.DEST_HABITS -> nav.navigate(Routes.HABITS) { launchSingleTop = true }
        }
        onDeepLinkHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) { DashboardScreen(nav) }
            composable(Routes.TASKS) { TasksScreen(nav) }
            composable(Routes.CALENDAR) { CalendarScreen(nav) }
            composable(Routes.HABITS) { HabitsScreen(nav) }
            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.TASK, listOf(navArgument("id") { type = NavType.LongType })) {
                TaskDetailScreen(nav, it.arguments?.getLong("id") ?: 0)
            }
            composable(
                Routes.EDITOR,
                listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("voice") { type = NavType.BoolType; defaultValue = false },
                    navArgument("parent") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                val a = it.arguments
                TaskEditorScreen(
                    nav,
                    taskId = a?.getLong("id") ?: 0,
                    epochDay = a?.getLong("date")?.takeIf { d -> d != Long.MIN_VALUE },
                    startWithVoice = a?.getBoolean("voice") ?: false,
                    parentId = a?.getLong("parent")?.takeIf { p -> p > 0 },
                )
            }
            composable(Routes.TIMELINE, listOf(navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE })) {
                TimelineScreen(nav, it.arguments?.getLong("date")?.takeIf { d -> d != Long.MIN_VALUE })
            }
            composable(Routes.ASSISTANT) { AssistantScreen(nav) }
            composable(Routes.FOCUS, listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = 0L })) {
                FocusScreen(nav, it.arguments?.getLong("taskId")?.takeIf { id -> id > 0 })
            }
            composable(Routes.SAVE_MY_DAY, listOf(navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE })) {
                SaveMyDayScreen(nav, it.arguments?.getLong("date")?.takeIf { d -> d != Long.MIN_VALUE })
            }
            composable(Routes.STATS) { StatsScreen(nav) }
            composable(Routes.MEMORY) { MemoryScreen(nav) }
            composable(Routes.BALANCE) { BalanceScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(nav) }
            composable(Routes.ALARM_LOG) { AlarmLogScreen(nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
        }
    }
}
