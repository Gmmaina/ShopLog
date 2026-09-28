package com.example.shoplog.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shoplog.core.util.Money

@Composable
fun ExpenditureBarChart(
    points: List<LineChartPoint>,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    onPointClick: ((LineChartPoint) -> Unit)? = null
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points) {
        mutableIntStateOf(points.lastIndex.coerceAtLeast(0))
    }

    val selectedPoint = points.getOrNull(selectedIndex) ?: points.last()
    val maxCents = remember(points) {
        points.maxOfOrNull { it.totalCents }?.coerceAtLeast(1L) ?: 1L
    }
    val avgCents = remember(points) {
        if (points.isNotEmpty()) points.sumOf { it.totalCents } / points.size else 0L
    }

    val barColor = MaterialTheme.colorScheme.primary
    val barColorSelected = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Bar Graph",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monthly Spending Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Bar View",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Highlighted Active Point Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = selectedPoint.fullDateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Money.format(selectedPoint.totalCents, currencySymbol),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (avgCents > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Average",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Money.format(avgCents, currencySymbol),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bar Canvas
            val canvasHeight = 160.dp
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val sectionWidth = size.width / points.size
                            val tappedIndex = (offset.x / sectionWidth).toInt().coerceIn(0, points.lastIndex)
                            selectedIndex = tappedIndex
                            onPointClick?.invoke(points[tappedIndex])
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val count = points.size
                val barWidth = (width / count) * 0.45f
                val step = width / count

                // Average Spending Line
                if (avgCents > 0L && maxCents > 0L) {
                    val avgY = height - ((avgCents.toFloat() / maxCents.toFloat()) * height * 0.82f)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Draw Bars
                points.forEachIndexed { i, pt ->
                    val x = (i * step) + (step / 2f) - (barWidth / 2f)
                    val ratio = pt.totalCents.toFloat() / maxCents.toFloat()
                    val barHeight = (ratio * height * 0.82f).coerceAtLeast(12f)
                    val y = height - barHeight
                    val isSelected = i == selectedIndex

                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    drawRoundRect(
                        color = if (isSelected) barColorSelected else barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Point Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                points.forEachIndexed { i, pt ->
                    val isSelected = i == selectedIndex
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
