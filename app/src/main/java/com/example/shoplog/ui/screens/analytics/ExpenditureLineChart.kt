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
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shoplog.core.util.Money

@Composable
fun ExpenditureLineChart(
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

    val lineColor = MaterialTheme.colorScheme.primary
    val linePointColor = MaterialTheme.colorScheme.tertiary
    val gradientTopColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val gradientBottomColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
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
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Line Graph",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Expenditure Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Smooth Curve",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Highlighted Active Month Box
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

            // Line Chart Canvas
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
                val stepX = if (count > 1) width / (count - 1) else width

                val pts = points.mapIndexed { i, pt ->
                    val x = if (count == 1) width / 2f else i * stepX
                    val ratio = pt.totalCents.toFloat() / maxCents.toFloat()
                    val y = height - (ratio * height * 0.75f) - 10f
                    Offset(x, y)
                }

                // Average Spending Line
                if (avgCents > 0L && maxCents > 0L) {
                    val avgY = height - ((avgCents.toFloat() / maxCents.toFloat()) * height * 0.75f) - 10f
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                if (pts.isNotEmpty()) {
                    // Smooth Bezier Curve Path
                    val strokePath = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until pts.size - 1) {
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val controlX1 = p1.x + (p2.x - p1.x) / 2f
                            val controlY1 = p1.y
                            val controlX2 = p1.x + (p2.x - p1.x) / 2f
                            val controlY2 = p2.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                        }
                    }

                    // Gradient Fill Path under line
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(pts.last().x, height)
                        lineTo(pts.first().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientTopColor, gradientBottomColor)
                        )
                    )

                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Draw Data Points
                    pts.forEachIndexed { i, pt ->
                        val isSelected = i == selectedIndex
                        val radius = if (isSelected) 6.dp.toPx() else 3.5.dp.toPx()
                        val color = if (isSelected) linePointColor else lineColor

                        drawCircle(
                            color = Color.White,
                            radius = radius + 3.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = pt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Point Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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
