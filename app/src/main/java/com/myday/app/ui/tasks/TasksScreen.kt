@file:OptIn(ExperimentalMaterial3Api::class)

package com.myday.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.rounded.Mic
import com.myday.app.ui.quickadd.QuickAddSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.data.CategoryEntity
import com.myday.app.data.Priority
import com.myday.app.data.TaskEntity
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.TaskQueries
import com.myday.app.ui.components.EmptyState
import com.myday.app.ui.components.TaskCard
import com.myday.app.ui.theme.isAppInDarkTheme
import com.myday.app.util.Fmt
import java.time.LocalDate

enum class StatusTab(val label: String) {
    UPCOMING("القادمة"), OVERDUE("المتأخرة"), DONE("المنجزة"), FAVORITE("المهمة ⭐"), ALL("الكل")
}

enum class DateRange(val label: String) { ANY("أي وقت"), TODAY("اليوم"), WEEK("هذا الأسبوع"), MONTH("هذا الشهر") }

@Composable
fun TasksScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onOpenTask: (Long) -> Unit,
    onAddTask: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPrayer: () -> Unit,
) {
    var quickAdd by remember { mutableStateOf<Boolean?>(null) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val dark = isAppInDarkTheme(settings.themeMode)

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(StatusTab.UPCOMING) }
    var range by rememberSaveable { mutableStateOf(DateRange.ANY) }
    var priorities by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var categoryIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var smartSort by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val activeFilters = (if (range != DateRange.ANY) 1 else 0) + priorities.size + categoryIds.size

    val filtered = remember(tasks, query, tab, range, priorities, categoryIds, smartSort, now) {
        val today = LocalDate.now()
        val (from, to) = when (range) {
            DateRange.ANY -> Long.MIN_VALUE to Long.MAX_VALUE
            DateRange.TODAY -> Fmt.startOfDay(today) to Fmt.endOfDay(today)
            DateRange.WEEK -> Fmt.startOfDay(Fmt.startOfWeek(today)) to Fmt.startOfDay(Fmt.startOfWeek(today).plusDays(7))
            DateRange.MONTH -> Fmt.startOfDay(today.withDayOfMonth(1)) to Fmt.startOfDay(today.withDayOfMonth(1).plusMonths(1))
        }
        val q = query.trim()
        val list = tasks.filter { t ->
            val statusOk = when (tab) {
                StatusTab.UPCOMING -> !t.isCompleted && t.dueAt >= now
                StatusTab.OVERDUE -> TaskQueries.isOverdue(t, now)
                StatusTab.DONE -> t.isCompleted
                StatusTab.FAVORITE -> t.isFavorite
                StatusTab.ALL -> true
            }
            statusOk &&
                t.dueAt in from until to &&
                (priorities.isEmpty() || t.priority.level in priorities) &&
                (categoryIds.isEmpty() || t.categoryId in categoryIds) &&
                (q.isEmpty() || t.title.contains(q, ignoreCase = true) || t.description.contains(q, ignoreCase = true))
        }
        when {
            smartSort -> TaskQueries.smartSort(list, now)
            tab == StatusTab.DONE -> list.sortedByDescending { it.completedAt ?: it.dueAt }
            tab == StatusTab.OVERDUE -> list.sortedByDescending { it.dueAt }
            else -> list.sortedBy { it.dueAt }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { quickAdd = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) { Icon(Icons.Rounded.Mic, "أضف بصوتك") }
                FloatingActionButton(onClick = onAddTask) { Icon(Icons.Rounded.Add, "مهمة جديدة") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("المهام", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { smartSort = !smartSort }) {
                    Icon(Icons.Rounded.AutoAwesome, "ترتيب ذكي حسب الأولوية",
                        tint = if (smartSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenCategories) { Icon(Icons.Rounded.Category, "التصنيفات") }
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                        Icon(Icons.Rounded.FilterList, "فلترة")
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث في العناوين والأوصاف…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "مسح") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StatusTab.entries) { s ->
                    val count = when (s) {
                        StatusTab.OVERDUE -> tasks.count { TaskQueries.isOverdue(it, now) }
                        else -> null
                    }
                    FilterChip(
                        selected = tab == s, onClick = { tab = s },
                        label = { Text(if (count != null && count > 0) "${s.label} ($count)" else s.label) }
                    )
                }
            }
            if (smartSort) {
                Text(
                    "ترتيب مقترح: المتأخرة ثم الأعلى أولوية ثم الأقرب موعداً",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (filtered.isEmpty()) {
                val searching = query.isNotBlank() || activeFilters > 0
                EmptyState(
                    icon = if (searching) Icons.Rounded.SearchOff else Icons.Rounded.TaskAlt,
                    title = if (searching) "لا نتائج مطابقة" else when (tab) {
                        StatusTab.UPCOMING -> "لا مهام قادمة"
                        StatusTab.OVERDUE -> "لا مهام متأخرة 👏"
                        StatusTab.DONE -> "لم تُنجز مهام بعد"
                        StatusTab.FAVORITE -> "لا مهام مهمة"
                        StatusTab.ALL -> "لا مهام بعد"
                    },
                    subtitle = if (searching) "جرّب كلمة أخرى أو أزل بعض عوامل الفلترة."
                    else if (tab == StatusTab.FAVORITE) "اضغط النجمة على أي مهمة لإضافتها هنا."
                    else "أضف مهمة جديدة من الزر بالأسفل.",
                    actionLabel = if (searching) "مسح البحث والفلترة" else null,
                    onAction = if (searching) ({ query = ""; range = DateRange.ANY; priorities = emptySet(); categoryIds = emptySet() }) else null
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { t ->
                        TaskCard(
                            task = t, category = catMap[t.categoryId], now = now, use24h = settings.use24h, dark = dark,
                            onClick = { onOpenTask(t.id) },
                            onToggleComplete = { vm.toggleComplete(t) },
                            onToggleFavorite = { vm.toggleFavorite(t) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    quickAdd?.let { voice ->
        QuickAddSheet(vm, voice, onDismiss = { quickAdd = null }, onOpenDraft = onOpenDraft, onOpenPrayerSettings = onOpenPrayer)
    }

    if (showFilters) {
        FilterSheet(
            categories = categories,
            range = range, priorities = priorities, categoryIds = categoryIds,
            onRange = { range = it },
            onTogglePriority = { p -> priorities = if (p in priorities) priorities - p else priorities + p },
            onToggleCategory = { c -> categoryIds = if (c in categoryIds) categoryIds - c else categoryIds + c },
            onClear = { range = DateRange.ANY; priorities = emptySet(); categoryIds = emptySet() },
            onDismiss = { showFilters = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    categories: List<CategoryEntity>,
    range: DateRange,
    priorities: Set<Int>,
    categoryIds: Set<Long>,
    onRange: (DateRange) -> Unit,
    onTogglePriority: (Int) -> Unit,
    onToggleCategory: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("فلترة المهام", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text("التاريخ", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRange.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { onRange(r) }, label = { Text(r.label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("الأولوية", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.reversed().forEach { p ->
                    FilterChip(selected = p.level in priorities, onClick = { onTogglePriority(p.level) }, label = { Text(p.label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("التصنيف", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(selected = c.id in categoryIds, onClick = { onToggleCategory(c.id) }, label = { Text(c.name) })
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("مسح كل الفلاتر") }
        }
    }
}
