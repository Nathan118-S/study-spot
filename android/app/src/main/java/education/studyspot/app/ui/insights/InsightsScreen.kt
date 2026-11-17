package education.studyspot.app.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.logic.Insights
import education.studyspot.app.logic.WeekBucket
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.IconStatTile
import education.studyspot.app.ui.components.currentLocale
import education.studyspot.app.ui.components.LoadingBox
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

/**
 * Insights tab (ios/StudySpot/Views/Insights/InsightsView.swift): stat tiles,
 * the weekly completion trend (area + line) and completions per week (bars,
 * current week highlighted). Charts are drawn with Canvas; tap a week to see
 * its value.
 */
@Composable
fun InsightsScreen(navigator: AppNavigator, contentPadding: PaddingValues) {
    val repo = LocalAppContainer.current.repository
    val assignments by repo.assignments.collectAsStateWithLifecycle()
    val isLoading by repo.isLoading.collectAsStateWithLifecycle()
    val hasLoaded by repo.hasLoaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = StudySpotTheme.colors
    var refreshing by remember { mutableStateOf(false) }

    val weekly = remember(assignments) { Insights.weekly(assignments) }
    val stats = Insights.stats(weekly)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; repo.reloadData(); refreshing = false } },
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Track your assignment completion progress over time.", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconStatTile("Total completed", "${stats.totalCompleted}", Icons.Filled.CheckCircle, colors.success, Modifier.weight(1f))
                IconStatTile("This week", "${stats.thisWeek}", Icons.Filled.EventAvailable, colors.accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconStatTile("Avg per week", String.format(currentLocale(), "%.1f", stats.averagePerWeek), Icons.Filled.MonitorHeart, colors.secondary, Modifier.weight(1f))
                IconStatTile("Best week", "${stats.bestWeek}", Icons.Filled.WorkspacePremium, Color(0xFFE6B400), Modifier.weight(1f))
            }
            if (isLoading && !hasLoaded) {
                LoadingBox()
            } else {
                ChartCard(
                    "Weekly completion trend",
                    "Assignments marked complete each week (last ${Insights.WEEKS} weeks).",
                    weekly,
                ) { selected, onSelect -> TrendChart(weekly, selected, onSelect) }
                ChartCard(
                    "Completions per week",
                    "Bar view of the same trend, highlighting the current week.",
                    weekly,
                ) { selected, onSelect -> BarChart(weekly, selected, onSelect) }
                if (weekly.all { it.completed == 0 }) {
                    Text(
                        "No completed assignments in the last ${Insights.WEEKS} weeks yet. Finish one to start your trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    caption: String,
    weekly: List<WeekBucket>,
    chart: @Composable (selected: Int?, onSelect: (Int?) -> Unit) -> Unit,
) {
    val colors = StudySpotTheme.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    val summary = weekly.joinToString(", ") { "week of ${it.label}: ${it.completed}" }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.secondary)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            val sel = selected?.let { weekly.getOrNull(it) }
            Text(
                if (sel != null) "Week of ${sel.label}: ${sel.completed} completed" else "Tap a week to see its value.",
                style = MaterialTheme.typography.labelMedium,
                color = if (sel != null) colors.text else colors.mutedText,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(Modifier.fillMaxWidth().height(220.dp).padding(top = 6.dp).semantics { contentDescription = "$title. $summary" }) {
                chart(selected) { selected = if (it == selected) null else it }
            }
        }
    }
}

private class ChartGeometry(val left: Float, val top: Float, val right: Float, val bottom: Float, val yMax: Int) {
    fun y(v: Number) = bottom - (v.toFloat() / yMax) * (bottom - top)
}

private fun niceMax(maxValue: Int): Int = when {
    maxValue <= 4 -> 4
    else -> (ceil(maxValue / 4.0) * 4).toInt()
}

/** Axes, recessive grid and labels shared by both charts. */
private fun DrawScope.frame(
    weekly: List<WeekBucket>,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    grid: Color,
    xCenter: (Int, ChartGeometry) -> Float,
): ChartGeometry {
    val yMax = niceMax(weekly.maxOfOrNull { it.completed } ?: 0)
    val leftPad = 28.dp.toPx()
    val bottomPad = 22.dp.toPx()
    val g = ChartGeometry(leftPad, 8.dp.toPx(), size.width, size.height - bottomPad, yMax)
    for (i in 0..4) {
        val v = yMax * i / 4
        val y = g.y(v)
        drawLine(
            grid, Offset(g.left, y), Offset(g.right, y), strokeWidth = 1f,
            pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        val t = measurer.measure("$v", labelStyle)
        drawText(t, topLeft = Offset(g.left - t.size.width - 6.dp.toPx(), y - t.size.height / 2))
    }
    weekly.forEachIndexed { i, b ->
        // Label every other week on narrow screens to avoid collisions.
        if (weekly.size > 6 && size.width < 600.dp.toPx() && (weekly.size - 1 - i) % 2 != 0) return@forEachIndexed
        val t = measurer.measure(b.label, labelStyle)
        drawText(t, topLeft = Offset(xCenter(i, g) - t.size.width / 2, g.bottom + 4.dp.toPx()))
    }
    return g
}

private fun Modifier.tapWeek(count: Int, leftPadDp: Float, onSelect: (Int?) -> Unit) = pointerInput(count) {
    detectTapGestures { p ->
        val left = leftPadDp * density
        val slot = (size.width - left) / count
        val i = ((p.x - left) / slot).toInt()
        onSelect(i.takeIf { p.x >= left && it in 0 until count })
    }
}

@Composable
private fun TrendChart(weekly: List<WeekBucket>, selected: Int?, onSelect: (Int?) -> Unit) {
    val colors = StudySpotTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.mutedText)
    val valueStyle = TextStyle(fontSize = 11.sp, color = colors.text, fontWeight = FontWeight.SemiBold)
    Canvas(Modifier.fillMaxSize().tapWeek(weekly.size, 28f, onSelect)) {
        if (weekly.isEmpty()) return@Canvas
        val xAt = { i: Int, g: ChartGeometry ->
            val slot = (g.right - g.left) / weekly.size
            g.left + slot * i + slot / 2
        }
        val g = frame(weekly, measurer, labelStyle, colors.border, xAt)
        val pts = weekly.mapIndexed { i, b -> Offset(xAt(i, g), g.y(b.completed)) }
        // Monotone-ish smoothing: horizontal control points never overshoot vertically.
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]
                val mid = (a.x + b.x) / 2
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, g.bottom)
            lineTo(pts.first().x, g.bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.35f), colors.accent.copy(alpha = 0f)), startY = g.top, endY = g.bottom))
        drawPath(line, colors.accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        selected?.let { i ->
            val p = pts.getOrNull(i) ?: return@let
            drawLine(colors.mutedText.copy(alpha = 0.5f), Offset(p.x, g.top), Offset(p.x, g.bottom), strokeWidth = 1.dp.toPx())
        }
        // Markers: current week always, plus the selected one; 2px surface ring.
        listOfNotNull(pts.lastIndex, selected).distinct().forEach { i ->
            val p = pts.getOrNull(i) ?: return@forEach
            drawCircle(colors.surface, radius = 6.dp.toPx(), center = p)
            drawCircle(colors.accent, radius = 4.dp.toPx(), center = p)
            val t = measurer.measure("${weekly[i].completed}", valueStyle)
            drawText(t, topLeft = Offset((p.x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), max(0f, p.y - t.size.height - 8.dp.toPx())))
        }
    }
}

@Composable
private fun BarChart(weekly: List<WeekBucket>, selected: Int?, onSelect: (Int?) -> Unit) {
    val colors = StudySpotTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.mutedText)
    val valueStyle = TextStyle(fontSize = 11.sp, color = colors.text, fontWeight = FontWeight.SemiBold)
    Canvas(Modifier.fillMaxSize().tapWeek(weekly.size, 28f, onSelect)) {
        if (weekly.isEmpty()) return@Canvas
        val xAt = { i: Int, g: ChartGeometry ->
            val slot = (g.right - g.left) / weekly.size
            g.left + slot * i + slot / 2
        }
        val g = frame(weekly, measurer, labelStyle, colors.border, xAt)
        val slot = (g.right - g.left) / weekly.size
        val barW = (slot * 0.6f).coerceAtMost(36.dp.toPx())
        val radius = 4.dp.toPx()
        weekly.forEachIndexed { i, b ->
            if (b.completed == 0) return@forEachIndexed
            val x = xAt(i, g) - barW / 2
            val top = g.y(b.completed)
            val current = i == weekly.lastIndex
            val color = if (current || i == selected) colors.accent else colors.accent.copy(alpha = 0.45f)
            // Rounded data end, square at the baseline.
            drawRoundRect(color, topLeft = Offset(x, top), size = Size(barW, g.bottom - top), cornerRadius = CornerRadius(radius))
            val square = minOf(radius, (g.bottom - top) / 2)
            drawRect(color, topLeft = Offset(x, g.bottom - square), size = Size(barW, square))
            if (current || i == selected) {
                val t = measurer.measure("${b.completed}", valueStyle)
                drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2, max(0f, top - t.size.height - 4.dp.toPx())))
            }
        }
    }
}
