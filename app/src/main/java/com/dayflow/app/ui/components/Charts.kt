package com.dayflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dayflow.app.ui.theme.Spacing

/*
 * Lightweight Canvas charts (no third-party chart library). Each chart exposes a textual
 * description of its data to TalkBack via `contentDescription`.
 * Layout is RTL: the first item is drawn on the right, matching Arabic reading order.
 */

data class BarDatum(val label: String, val value: Float, val color: Color? = null)

@Composable
fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) },
) {
    val max = (data.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
    val description = data.joinToString("، ") { "${it.label}: ${valueFormatter(it.value)}" }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { d ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (d.value > 0) {
                        Text(valueFormatter(d.value), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f, fill = true), contentAlignment = Alignment.BottomCenter) {
                        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                            val h = size.height * (d.value / max)
                            drawRoundRect(track, size = size, cornerRadius = CornerRadius(10f, 10f), alpha = 0.35f)
                            if (h > 0) {
                                drawRoundRect(
                                    d.color ?: barColor, topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.forEach {
                Text(it.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Line chart for trends (e.g. completed tasks per day over a month). */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
    description: String,
    startLabel: String = "",
    endLabel: String = "",
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val max = values.max().coerceAtLeast(1f)
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (values.size == 1) {
                drawCircle(color, 6f, Offset(size.width / 2, size.height * (1 - values[0] / max)))
                return@Canvas
            }
            val stepX = size.width / (values.size - 1)
            // RTL: index 0 (oldest) on the right.
            fun point(i: Int) = Offset(size.width - i * stepX, size.height * (1 - values[i] / max) * 0.92f + size.height * 0.04f)
            val path = Path().apply {
                moveTo(point(0).x, point(0).y)
                for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(point(values.lastIndex).x, size.height)
                lineTo(point(0).x, size.height)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.15f))
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            values.indices.forEach { drawCircle(color, 4.5f, point(it)) }
        }
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(endLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Horizontal bars with labels; optional goal marker per row (used by Life Balance). */
data class HBarDatum(val label: String, val value: Float, val valueText: String, val color: Color, val goal: Float? = null)

@Composable
fun HorizontalBars(data: List<HBarDatum>, modifier: Modifier = Modifier) {
    val max = (data.flatMap { listOfNotNull(it.value, it.goal) }.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        data.forEach { d ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(d.color)
                    Text(d.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text(d.valueText, style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        val w = size.width * (d.value / max)
                        // RTL: grow from the right edge.
                        drawRoundRect(d.color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(8f, 8f))
                        d.goal?.let { g ->
                            val x = size.width - size.width * (g / max)
                            drawLine(goalColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
                        }
                    }
                }
            }
        }
    }
}

/** Compact mini bars (e.g. 7-day habit history). */
@Composable
fun DotsRow(filled: List<Boolean>, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        filled.forEach { f ->
            Box(
                Modifier.width(14.dp).height(14.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (f) color else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
