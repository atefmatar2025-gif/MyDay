@file:OptIn(ExperimentalMaterial3Api::class)

package com.myday.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myday.app.data.CategoryEntity

object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "work" to Icons.Rounded.Work,
        "study" to Icons.Rounded.School,
        "worship" to Icons.Rounded.Brightness2,
        "health" to Icons.Rounded.Favorite,
        "personal" to Icons.Rounded.Event,
        "family" to Icons.Rounded.FamilyRestroom,
        "shopping" to Icons.Rounded.ShoppingCart,
        "home" to Icons.Rounded.Home,
        "sport" to Icons.Rounded.FitnessCenter,
        "book" to Icons.Rounded.Book,
        "money" to Icons.Rounded.Payments,
        "travel" to Icons.Rounded.Flight,
        "code" to Icons.Rounded.Code,
        "car" to Icons.Rounded.DirectionsCar,
        "food" to Icons.Rounded.Restaurant,
        "idea" to Icons.Rounded.Lightbulb,
        "phone" to Icons.Rounded.Call,
        "star" to Icons.Rounded.Star,
    )

    fun get(key: String?): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Category

    val palette: List<Long> = listOf(
        0xFF2563EB, 0xFF7C3AED, 0xFF059669, 0xFFDC2626, 0xFFDB2777, 0xFFEA580C,
        0xFFCA8A04, 0xFF0891B2, 0xFF4F46E5, 0xFF65A30D, 0xFF0F766E, 0xFF64748B,
    )
}

fun CategoryEntity.color(): Color = Color(colorArgb)

@Composable
fun CategoryBadge(category: CategoryEntity, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val c = category.color()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(CategoryIcons.get(category.iconKey), contentDescription = null, tint = c, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = s)
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = s)
        }
        content()
    }
}

@Composable
fun SelectableColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "dot")
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(border).padding(3.dp)
            .clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
            .clip(CircleShape).background(color).clickable(onClick = onClick)
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
