package dev.habitcommit.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.habitcommit.data.CheckType
import dev.habitcommit.data.CheckUi
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.data.PlanSource
import dev.habitcommit.data.TodayState
import dev.habitcommit.sync.GithubSyncScheduler
import dev.habitcommit.sync.SyncManager
import dev.habitcommit.sync.SyncStatus
import dev.habitcommit.widget.Today22Receiver
import dev.habitcommit.widget.Today24Receiver
import dev.habitcommit.widget.Today42Receiver
import dev.habitcommit.widget.Today44Receiver
import dev.habitcommit.widget.refreshAllWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HabitApp(
    repository: HabitRepository,
    syncManager: SyncManager,
    route: String?,
    resumeTick: Int,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf<TodayState?>(null) }
    var showConfig by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val syncStatus by syncManager.status.collectAsState()

    fun refreshWidgets() {
        scope.launch { refreshAllWidgets(context) }
    }

    fun reload() {
        reloadKey++
        refreshWidgets()
    }

    // Reload on resume too: a check-in on the widget changed the journal while
    // this screen was in the background.
    LaunchedEffect(reloadKey, resumeTick) {
        today = withContext(Dispatchers.IO) { repository.today() }
    }

    LaunchedEffect(route) {
        if (route == "habitcommit://config") {
            showConfig = true
            onRouteConsumed()
        }
    }

    fun toggle(checkId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.toggle(checkId) }
            today = withContext(Dispatchers.IO) { repository.today() }
            refreshWidgets()
        }
    }

    fun rate(checkId: String, value: Int) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.setValue(checkId, value) }
            today = withContext(Dispatchers.IO) { repository.today() }
            refreshWidgets()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (showSync) {
            SyncScreen(
                syncManager = syncManager,
                repository = repository,
                onClose = { showSync = false },
                onConfigReplaced = {
                    showSync = false
                    reload()
                },
            )
        } else if (showConfig) {
            ConfigScreen(
                initialText = remember(showConfig) { repository.configText() },
                onClose = { showConfig = false },
                onSave = { text ->
                    val result = repository.saveConfig(text)
                    if (result.isSuccess) {
                        showConfig = false
                        reload()
                    }
                    result
                },
            )
        } else {
            val state = today
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Carico il piano di oggi…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                AppHeader(
                    state = state,
                    onOpenConfig = { showConfig = true },
                )

                if (state.demoMode) {
                    DemoBanner(
                        onClear = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.clearEvents() }
                                reloadKey++
                                refreshWidgets()
                            }
                        },
                    )
                }

                state.configError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    ErrorCard(error = error, onOpenConfig = { showConfig = true })
                }

                Spacer(Modifier.height(16.dp))
                TodayCard(
                    state = state,
                    onToggle = ::toggle,
                    onRate = ::rate,
                    onOpenConfig = { showConfig = true },
                )

                Spacer(Modifier.height(16.dp))
                WidgetCard(context)

                Spacer(Modifier.height(16.dp))
                GitHubCard(
                    status = syncStatus,
                    onOpen = { showSync = true },
                )

                Spacer(Modifier.height(16.dp))
                LocalConfigCard(
                    state = state,
                    onEdit = { showConfig = true },
                    onSeedDemo = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.seedDemo() }
                            reloadKey++
                            refreshWidgets()
                        }
                    },
                    onClear = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.clearEvents() }
                            reloadKey++
                            refreshWidgets()
                        }
                    },
                )

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "v0.1 smoke · local-first · nessun dato lascia il telefono",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

}

@Composable
private fun AppHeader(state: TodayState, onOpenConfig: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "HABITCOMMIT",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            Text(
                text = "Oggi",
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1).sp,
            )
            Text(
                text = formatDate(state.date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
        TextButton(onClick = onOpenConfig) {
            Text("Config")
        }
    }
}

@Composable
private fun DemoBanner(onClear: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DEMO",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Dati di esempio locali",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 12.sp,
            )
            TextButton(onClick = onClear) {
                Text("Azzera")
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onOpenConfig: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "La config non è valida",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.sp,
            )
            TextButton(onClick = onOpenConfig) {
                Text("Apri habits.json")
            }
        }
    }
}

@Composable
private fun TodayCard(
    state: TodayState,
    onToggle: (String) -> Unit,
    onRate: (String, Int) -> Unit,
    onOpenConfig: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.extraLarge,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PIANO DI OGGI",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = state.plan.label,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.7).sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    SourcePill(state)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${state.doneCount}/${state.total}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        text = "${(state.progress * 100).roundToInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            if (state.metrics.isEmpty()) {
                Text(
                    text = "Nessuna metrica per questo piano.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            state.metrics.forEachIndexed { index, check ->
                CheckRow(
                    check = check,
                    onToggle = { onToggle(check.def.id) },
                    onRate = { value -> onRate(check.def.id, value) },
                )
                if (index < state.metrics.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Heatmap(levels = state.heat, cellSize = 10.dp, gap = 2.dp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.streak} giorni di fila",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "salvato in locale ✓",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            TomorrowRow(state)

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpenConfig,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Modifica habits.json")
            }
        }
    }
}

@Composable
private fun SourcePill(state: TodayState) {
    val label = when (state.source) {
        PlanSource.DATE_OVERRIDE -> "OVERRIDE DI OGGI"
        PlanSource.WEEKDAY -> "PIANO DEL GIORNO · HABITS.JSON"
        PlanSource.DEFAULT -> "PIANO PREDEFINITO"
        PlanSource.LOCAL_OVERRIDE -> "PIANO · HABITS.JSON"
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun CheckRow(check: CheckUi, onToggle: () -> Unit, onRate: (Int) -> Unit) {
    // Material 3 list item: label on the leading side, trailing control.
    val rating = check.def.type == CheckType.RATING
    val base = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .padding(horizontal = 4.dp, vertical = 2.dp)
    Row(
        modifier = if (rating) base else base.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = check.def.label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
        )
        if (rating) {
            RatingStars(value = check.value, max = check.def.maxValue, onRate = onRate)
        } else {
            check.timeLabel?.let { time ->
                Text(
                    text = time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = check.done,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun RatingStars(value: Int, max: Int, onRate: (Int) -> Unit) {
    // Tapping the current star clears the rating; each star is its own 32dp tap target.
    Row {
        for (star in 1..max) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onRate(if (star == value) 0 else star) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (star <= value) "★" else "☆",
                    fontSize = 21.sp,
                    color = if (star <= value) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}

@Composable
private fun Heatmap(
    levels: List<Int>,
    cellSize: Dp,
    gap: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .size(cellSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(HeatColors[level.coerceIn(0, HeatColors.lastIndex)]),
            )
        }
    }
}

@Composable
private fun TomorrowRow(state: TodayState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DOMANI",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.tomorrowPlan?.label ?: "—",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = state.tomorrowPreview,
                modifier = Modifier.weight(1.4f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WidgetCard(context: Context) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Widget sulla home",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ogni tap sul widget aggiorna subito i dati. Aggiungilo alla home:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetPinButton(
                    context = context,
                    label = "4×4",
                    receiver = Today44Receiver::class.java,
                    modifier = Modifier.weight(1f),
                )
                WidgetPinButton(
                    context = context,
                    label = "4×2",
                    receiver = Today42Receiver::class.java,
                    modifier = Modifier.weight(1f),
                )
                WidgetPinButton(
                    context = context,
                    label = "2×4",
                    receiver = Today24Receiver::class.java,
                    modifier = Modifier.weight(1f),
                )
                WidgetPinButton(
                    context = context,
                    label = "2×2",
                    receiver = Today22Receiver::class.java,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Se il launcher non mostra la richiesta: tieni premuto sulla home → Widget → HabitCommit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun WidgetPinButton(
    context: Context,
    label: String,
    receiver: Class<*>,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { pinWidget(context, receiver) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, maxLines = 1)
    }
}

private fun pinWidget(context: Context, receiver: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    } else {
        Toast.makeText(
            context,
            "Il launcher non supporta l'aggiunta automatica: usa Widget → HabitCommit.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

@Composable
private fun GitHubCard(status: SyncStatus, onOpen: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync GitHub",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (status.configured) {
                            "${status.repoLabel} · ${status.branch} · ${status.journalDir}/"
                        } else {
                            "Non collegata"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                if (status.pending > 0) {
                    Text(
                        text = "${status.pending} in coda",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = status.lastMessage
                    ?: "Collega un repo privato per tenere la cronologia in git. I valori restano locali finché non c'è rete.",
                color = if (status.configured && !status.lastOk) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(if (status.configured) "Gestisci sync" else "Collega repo")
                }
                if (status.configured) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { GithubSyncScheduler.request(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Sincronizza")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalConfigCard(
    state: TodayState,
    onEdit: () -> Unit,
    onSeedDemo: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "habits.json",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Metriche, piani e schedule",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = state.zoneId,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Il piano di oggi viene risolto da questo file. Con la sync attiva ogni valore registrato viene accodato e poi committato sul tuo repo GitHub.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Modifica config")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onSeedDemo, modifier = Modifier.weight(1f)) {
                    Text("Ricarica demo")
                }
            }
            TextButton(onClick = onClear) {
                Text("Azzera tutte le metriche locali")
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    initialText: String,
    onClose: () -> Unit,
    onSave: (String) -> Result<Unit>,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "habits.json",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Configurazione locale · validazione allo schema 1",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onClose) {
                Text("Chiudi")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Incolla qui il contenuto del file. Controllo subito ID, metriche, piani, schedule e override.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            isError = error != null,
            label = { Text("JSON") },
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            OutlinedButton(
                onClick = {
                    text = initialText
                    error = null
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Ripristina")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val result = onSave(text)
                    result.onFailure {
                        error = it.message ?: "Configurazione non valida"
                    }
                },
                modifier = Modifier.weight(2f),
            ) {
                Text("Salva e ricarica")
            }
        }
    }
}

private val italianDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

private fun formatDate(date: LocalDate): String {
    return date.format(italianDate).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
}
