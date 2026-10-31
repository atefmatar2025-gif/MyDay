package com.myday.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.calendar.CalendarScreen
import com.myday.app.ui.categories.CategoriesScreen
import com.myday.app.ui.detail.TaskDetailScreen
import com.myday.app.ui.editor.EditorScreen
import com.myday.app.ui.home.HomeScreen
import com.myday.app.ui.permissions.PermissionsScreen
import com.myday.app.ui.settings.SettingsScreen
import com.myday.app.ui.stats.StatsScreen
import com.myday.app.ui.tasks.TasksScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val PERMISSIONS = "permissions"
    const val WELCOME = "welcome"
    const val DETAIL = "task/{id}"
    const val EDITOR = "editor?taskId={taskId}&copyFrom={copyFrom}&date={date}"

    fun detail(id: Long) = "task/$id"
    fun newTask(date: LocalDate? = null) = if (date != null) "editor?date=${date.toEpochDay()}" else "editor"
    fun editTask(id: Long) = "editor?taskId=$id"
    fun copyTask(id: Long) = "editor?copyFrom=$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "الرئيسية", Icons.Rounded.Home),
    TopLevel(Routes.TASKS, "المهام", Icons.Rounded.TaskAlt),
    TopLevel(Routes.CALENDAR, "التقويم", Icons.Rounded.CalendarMonth),
    TopLevel(Routes.STATS, "الإحصائيات", Icons.Rounded.Insights),
    TopLevel(Routes.SETTINGS, "الإعدادات", Icons.Rounded.Settings),
)

@Composable
fun MyDayRoot(settings: AppSettings, openTaskRequest: MutableStateFlow<Long?>) {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val startRoute = remember { if (settings.onboardingDone) Routes.HOME else Routes.WELCOME }

    // رسائل التطبيق (مع زر تراجع)
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val r = snackbar.showSnackbar(
                    message = m.text, actionLabel = m.actionLabel,
                    withDismissAction = m.actionLabel == null,
                    duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
                if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
            }
        }
    }
    // فتح مهمة من إشعار
    LaunchedEffect(Unit) {
        openTaskRequest.collect { id ->
            if (id != null && settings.onboardingDone) {
                nav.navigate(Routes.detail(id)) { launchSingleTop = true }
                openTaskRequest.value = null
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.97f) },
        ) {
            composable(Routes.WELCOME) {
                PermissionsScreen(vm, settings, onboarding = true) {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
                }
            }
            composable(Routes.HOME) {
                HomeScreen(
                    vm, settings,
                    onOpenTask = { nav.navigate(Routes.detail(it)) },
                    onAddTask = { nav.navigate(Routes.newTask()) },
                    onSeeAll = { nav.navigateTopLevel(Routes.TASKS) },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) }
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(
                    vm, settings,
                    onOpenTask = { nav.navigate(Routes.detail(it)) },
                    onAddTask = { nav.navigate(Routes.newTask()) },
                    onOpenCategories = { nav.navigate(Routes.CATEGORIES) }
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    vm, settings,
                    onOpenTask = { nav.navigate(Routes.detail(it)) },
                    onAddTask = { nav.navigate(Routes.newTask(it)) }
                )
            }
            composable(Routes.STATS) { StatsScreen(vm) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm, settings,
                    onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) }
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(vm, settings, onboarding = false) { nav.popBackStack() }
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                TaskDetailScreen(
                    taskId = id, vm = vm, settings = settings,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editTask(it)) },
                    onCopy = { nav.navigate(Routes.copyTask(it)) }
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("copyFrom") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                )
            ) {
                EditorScreen(
                    appVm = vm, settings = settings,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        // بعد الحفظ: نعود، ولمهمة جديدة نفتح تفاصيلها
                        val wasNew = nav.currentBackStackEntry?.arguments?.getLong("taskId", -1L) == -1L
                        nav.popBackStack()
                        if (wasNew) vm.message("تم حفظ المهمة ✓")
                    }
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
