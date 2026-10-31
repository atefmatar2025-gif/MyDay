@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myday.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.data.CategoryEntity
import com.myday.app.ui.AppViewModel
import com.myday.app.ui.components.CategoryBadge
import com.myday.app.ui.components.CategoryIcons
import com.myday.app.ui.components.ConfirmDialog
import com.myday.app.ui.components.SelectableColorDot

@Composable
fun CategoriesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val counts = remember(tasks) { tasks.filter { !it.isCompleted }.groupingBy { it.categoryId }.eachCount() }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التصنيفات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = CategoryEntity(name = "", colorArgb = CategoryIcons.palette.random(), iconKey = "star") },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("تصنيف جديد") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { editing = c }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(c, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        val n = counts[c.id] ?: 0
                        Text(if (n == 0) "لا مهام نشطة" else "$n مهام نشطة", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = c }) { Icon(Icons.Rounded.Edit, "تعديل") }
                    IconButton(onClick = { deleting = c }) { Icon(Icons.Rounded.Delete, "حذف") }
                }
            }
        }
    }

    editing?.let { c ->
        CategoryDialog(initial = c, onDismiss = { editing = null }) { updated ->
            vm.saveCategory(updated)
            editing = null
        }
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "حذف التصنيف؟",
            text = "سيُحذف تصنيف \"${c.name}\". المهام التابعة له لن تُحذف بل تصبح بلا تصنيف.",
            confirmLabel = "حذف", destructive = true,
            onConfirm = { vm.deleteCategory(c) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun CategoryDialog(initial: CategoryEntity, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.colorArgb) }
    var icon by remember { mutableStateOf(initial.iconKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "تصنيف جديد" else "تعديل التصنيف") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("الاسم") }, singleLine = true)
                Spacer(Modifier.height(14.dp))
                Text("اللون", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.palette.forEach { c -> SelectableColorDot(Color(c), color == c) { color = c } }
                }
                Spacer(Modifier.height(14.dp))
                Text("الأيقونة", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.forEach { (key, vec) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) { Icon(vec, null, tint = if (sel) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name, colorArgb = color, iconKey = icon)) }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
