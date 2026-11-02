package dev.habitcommit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.habitcommit.R
import dev.habitcommit.data.CheckType
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.data.HistoryDay
import dev.habitcommit.data.HistoryMetric
import dev.habitcommit.data.HistoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Past values per metric and per day. Read-only: editing stays in the widget or Today. */
@Composable
fun HistoryScreen(repository: HabitRepository, onClose: () -> Unit) {
    var range by remember { mutableIntStateOf(30) }
    var state by remember { mutableStateOf<HistoryState?>(null) }

    LaunchedEffect(range) {
        state = withContext(Dispatchers.IO) { repository.history(range) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history_kicker),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = stringResource(R.string.history_heading),
                    fontSize = 30.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = (-1).sp,
                )
                state?.let {
                    Text(
                        text = stringResource(R.string.history_range, range, it.zone.id),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 30, 90).forEach { option ->
                FilterChip(
                    selected = range == option,
                    onClick = { range = option },
                    label = {
                        Text(stringResource(R.string.range_chip, option), fontSize = 12.sp)
                    },
                )
            }
        }

        val current = state
        if (current == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.history_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            return@Column
        }

        if (current.metrics.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            return@Column
        }

        Spacer(Modifier.height(14.dp))
        current.metrics.forEach { metric ->
            MetricHistoryCard(metric = metric, state = current)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.history_legend),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.history_recent),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        current.recentDays.reversed().forEach { day ->
            DayHistoryRow(day = day, state = current)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricHistoryCard(metric: HistoryMetric, state: HistoryState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = metric.def.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        append(
                            stringResource(
                                R.string.history_metric_days,
                                metric.activeDays,
                                metric.plannedDays,
                            ),
                        )
                        metric.average?.let {
                            append(" · ")
                            append(
                                stringResource(
                                    R.string.history_average,
                                    "%.1f".format(Locale.getDefault(), it),
                                ),
                            )
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (metric.def.type == CheckType.RATING) {
                RatingChart(metric = metric, days = state.days)
            } else {
                MetricStrip(metric = metric, days = state.days)
            }
        }
    }
}

/**
 * Line chart for rating metrics: one dot per recorded day, at its score.
 * Recorded values on adjacent days are joined; a gap or an unrecorded day
 * breaks the line. Planned-but-unrecorded days get a faint dot at the baseline.
 */
@Composable
private fun RatingChart(metric: HistoryMetric, days: List<LocalDate>) {
    val count = metric.values.size
    val max = metric.def.maxValue.coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val fixedCell = 16.dp
    val chartHeight = 64.dp
    val scrollable = count > 14
    // Constrain the whole column, not just the canvas: StripLabels uses a
    // weighted row and needs a bounded width to lay out on one line.
    val containerModifier = if (scrollable) {
        Modifier
            .horizontalScroll(rememberScrollState(initial = Int.MAX_VALUE))
            .width(fixedCell * count)
    } else {
        Modifier
    }

    Column(containerModifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val step = size.width / count.coerceAtLeast(1)
            fun x(index: Int): Float = step * (index + 0.5f)
            val topPad = with(density) { 7.dp.toPx() }
            val bottomPad = with(density) { 5.dp.toPx() }
            val baseline = size.height - bottomPad
            val usable = (baseline - topPad).coerceAtLeast(1f)
            fun y(value: Int): Float =
                baseline - usable * value.toFloat().div(max)

            val stroke = with(density) { 1.5.dp.toPx() }
            val dotRadius = with(density) { 3.dp.toPx() }
            val faintRadius = with(density) { 1.5.dp.toPx() }

            drawLine(
                color = muted.copy(alpha = 0.5f),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = with(density) { 1.dp.toPx() },
            )

            var previousIndex: Int? = null
            var previousValue = 0
            metric.values.forEachIndexed { index, value ->
                if (value != null && value > 0) {
                    val point = Offset(x(index), y(value))
                    val previous = previousIndex
                    if (previous != null && previous == index - 1) {
                        drawLine(
                            color = color.copy(alpha = 0.8f),
                            start = Offset(x(previous), y(previousValue)),
                            end = point,
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(color = color, radius = dotRadius, center = point)
                    previousIndex = index
                    previousValue = value
                } else {
                    if (value != null) {
                        // Planned but not recorded.
                        drawCircle(
                            color = muted,
                            radius = faintRadius,
                            center = Offset(x(index), baseline),
                        )
                    }
                    previousIndex = null
                }
            }
        }
        StripLabels(days)
    }
}

@Composable
private fun MetricStrip(metric: HistoryMetric, days: List<LocalDate>) {
    val gap = 3.dp
    if (metric.values.size <= 14) {
        // Two weeks fit on a phone, so the cells stretch to fill the card.
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                metric.values.forEach { value ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .then(cellBackground(metric, value)),
                    )
                }
            }
            StripLabels(days)
        }
    } else {
        // Longer ranges scroll; they open with today at the right edge, under
        // the "oggi" label.
        val cell = 16.dp
        val stripWidth = cell * metric.values.size + gap * (metric.values.size - 1)
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState(initial = Int.MAX_VALUE))
                .width(stripWidth),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                metric.values.forEach { value ->
                    Box(
                        modifier = Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(3.dp))
                            .then(cellBackground(metric, value)),
                    )
                }
            }
            StripLabels(days)
        }
    }
}

@Composable
private fun StripLabels(days: List<LocalDate>) {
    Spacer(Modifier.height(5.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatShortDate(days.first()),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
        Text(
            text = stringResource(R.string.history_today),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun cellBackground(metric: HistoryMetric, value: Int?): Modifier =
    when {
        value == null -> Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(3.dp),
        )
        else -> Modifier.background(cellColor(metric, value))
    }

@Composable
private fun cellColor(metric: HistoryMetric, value: Int): Color {
    if (value <= 0) return MaterialTheme.colorScheme.surfaceVariant
    return when (metric.def.type) {
        CheckType.RATING -> {
            val fraction = (value.toFloat() / metric.def.maxValue).coerceIn(0f, 1f)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * fraction)
        }
        CheckType.BINARY -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun DayHistoryRow(day: HistoryDay, state: HistoryState) {
    val index = state.days.indexOf(day.date)
    Column(modifier = Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDayTitle(day.date),
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            Text(
                text = day.planLabel,
                modifier = Modifier.weight(1.3f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${day.done}/${day.total}",
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
        }
        if (index < 0) return@Column
        val parts = state.metrics.mapNotNull { metric ->
            val value = metric.values.getOrNull(index) ?: return@mapNotNull null
            when (metric.def.type) {
                CheckType.RATING ->
                    "${metric.def.label} ${if (value > 0) "$value/${metric.def.maxValue}" else "—"}"
                CheckType.BINARY ->
                    "${metric.def.label} ${if (value > 0) "✓" else "—"}"
            }
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = parts.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun formatDayTitle(date: LocalDate): String {
    val locale = Locale.getDefault()
    val label = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    return label.replaceFirstChar { it.titlecase(locale) }
}
