package dev.habitcommit.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    text = "STORICO",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Metriche passate",
                    fontSize = 30.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = (-1).sp,
                )
                state?.let {
                    Text(
                        text = "Ultimi ${range} giorni · ${it.zone.id}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            TextButton(onClick = onClose) { Text("Chiudi") }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 30, 90).forEach { option ->
                FilterChip(
                    selected = range == option,
                    onClick = { range = option },
                    label = { Text("$option g", fontSize = 12.sp) },
                )
            }
        }

        val current = state
        if (current == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Carico lo storico…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            return@Column
        }

        if (current.metrics.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Nessun dato: registra qualche valore dal widget e torna qui.",
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
            text = "Quadrato vuoto: non in piano quel giorno. Chiaro: in piano ma non registrato. " +
                "Pieno: fatto. Per i rating il colore sale con il voto.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "ULTIMI GIORNI",
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
                        append("${metric.activeDays}/${metric.plannedDays} giorni")
                        metric.average?.let { append(" · ★ ${"%.1f".format(Locale.ITALIAN, it)}") }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            MetricStrip(metric = metric, days = state.days)
        }
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
            text = "oggi",
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

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
private val dayTitle = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

private fun formatShortDate(date: LocalDate): String = date.format(shortDate)

private fun formatDayTitle(date: LocalDate): String {
    val label = date.format(dayTitle)
    return label.replaceFirstChar { it.titlecase(Locale.ITALIAN) }
}
