package dev.habitcommit.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.habitcommit.BuildConfig
import dev.habitcommit.R
import dev.habitcommit.data.CheckType
import dev.habitcommit.data.CheckUi
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.data.TodayState
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
    var showSync by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDemoClear by remember { mutableStateOf(false) }
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

    // The widget's secondary button now opens Settings; plans are edited in the
    // GitHub repo, not in the app.
    LaunchedEffect(route) {
        if (route == "habitcommit://config") {
            showSettings = true
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

    fun clearMetrics() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearEvents() }
            reloadKey++
            refreshWidgets()
        }
    }

    fun seedDemo() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.seedDemo() }
            reloadKey++
            refreshWidgets()
        }
    }

    // System back closes the top-most overlay instead of leaving the app.
    BackHandler(
        enabled = showSync || showHistory || showWidgets ||
            showData || showInfo || showSettings,
    ) {
        when {
            showSync -> showSync = false
            showHistory -> showHistory = false
            showWidgets -> showWidgets = false
            showData -> showData = false
            showInfo -> showInfo = false
            showSettings -> showSettings = false
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
        } else if (showHistory) {
            HistoryScreen(
                repository = repository,
                onClose = { showHistory = false },
            )
        } else if (showWidgets) {
            WidgetScreen(context = context, onClose = { showWidgets = false })
        } else if (showData) {
            DataScreen(
                onSeedDemo = {
                    seedDemo()
                    showData = false
                },
                onClear = {
                    clearMetrics()
                    showData = false
                },
                onClose = { showData = false },
            )
        } else if (showInfo) {
            InfoScreen(onClose = { showInfo = false })
        } else if (showSettings) {
            SettingsScreen(
                status = syncStatus,
                onOpenSync = { showSync = true },
                onOpenWidgets = { showWidgets = true },
                onOpenData = { showData = true },
                onOpenInfo = { showInfo = true },
                onClose = { showSettings = false },
            )
        } else {
            val state = today
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.loading_plan),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    onOpenHistory = { showHistory = true },
                    onOpenSettings = { showSettings = true },
                )

                if (state.demoMode) {
                    DemoBanner(onClear = { confirmDemoClear = true })
                }

                state.configError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    ErrorCard(error = error)
                }

                Spacer(Modifier.height(16.dp))
                TodayCard(
                    state = state,
                    onToggle = ::toggle,
                    onRate = ::rate,
                )

                // Sync only surfaces when it needs attention; setup lives in Settings.
                if (syncStatus.configured && (syncStatus.pending > 0 || !syncStatus.lastOk)) {
                    Spacer(Modifier.height(12.dp))
                    SyncStatusRow(status = syncStatus, onOpen = { showSync = true })
                }

                Spacer(Modifier.height(24.dp))

                if (confirmDemoClear) {
                    AlertDialog(
                        onDismissRequest = { confirmDemoClear = false },
                        title = { Text(stringResource(R.string.demo_clear_title)) },
                        text = { Text(stringResource(R.string.demo_clear_body)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmDemoClear = false
                                    clearMetrics()
                                },
                            ) { Text(stringResource(R.string.action_clear)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDemoClear = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
                }
            }
        }
    }

}

@Composable
private fun AppHeader(
    state: TodayState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.today_title),
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
        TextButton(onClick = onOpenHistory) {
            Text(stringResource(R.string.history_title))
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings_title))
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
                text = stringResource(R.string.demo_badge),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.demo_local),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 12.sp,
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.config_invalid_title),
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.config_invalid_hint),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TodayCard(
    state: TodayState,
    onToggle: (String) -> Unit,
    onRate: (String, Int) -> Unit,
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
                        text = stringResource(R.string.plan_today),
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
                }
                Text(
                    text = "${state.doneCount}/${state.total}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp,
                )
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
                    text = stringResource(R.string.no_metrics_for_plan),
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
                    text = pluralStringResource(R.plurals.streak_days, state.streak, state.streak),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.saved_locally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            TomorrowRow(state)
        }
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
                    text = stringResource(R.string.tomorrow),
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
private fun SettingsScreen(
    status: SyncStatus,
    onOpenSync: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenData: () -> Unit,
    onOpenInfo: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onClose = onClose)
        Spacer(Modifier.height(14.dp))
        SettingsRow(
            title = stringResource(R.string.sync_card_title),
            subtitle = if (status.configured) {
                "${status.repoLabel} · ${status.branch}"
            } else {
                stringResource(R.string.not_connected)
            },
            onClick = onOpenSync,
        )
        SettingsRow(
            title = stringResource(R.string.widget_card_title),
            subtitle = stringResource(R.string.settings_widget_sub),
            onClick = onOpenWidgets,
        )
        SettingsRow(
            title = stringResource(R.string.settings_data),
            subtitle = stringResource(R.string.settings_data_sub),
            onClick = onOpenData,
        )
        SettingsRow(
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_about_sub),
            onClick = onOpenInfo,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun WidgetScreen(context: Context, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.widget_card_title), onClose = onClose)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.widget_card_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(14.dp))
        WidgetFormatRow(
            title = stringResource(R.string.widget_format_plan),
            description = stringResource(R.string.widget_44_description),
            context = context,
            receiver = Today44Receiver::class.java,
        )
        WidgetFormatRow(
            title = stringResource(R.string.widget_format_compact),
            description = stringResource(R.string.widget_42_description),
            context = context,
            receiver = Today42Receiver::class.java,
        )
        WidgetFormatRow(
            title = stringResource(R.string.widget_format_vertical),
            description = stringResource(R.string.widget_24_description),
            context = context,
            receiver = Today24Receiver::class.java,
        )
        WidgetFormatRow(
            title = stringResource(R.string.widget_format_focus),
            description = stringResource(R.string.widget_22_description),
            context = context,
            receiver = Today22Receiver::class.java,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.widget_pin_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WidgetFormatRow(
    title: String,
    description: String,
    context: Context,
    receiver: Class<*>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { pinWidget(context, receiver) }) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

@Composable
private fun DataScreen(
    onSeedDemo: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmSeed by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_data), onClose = onClose)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_data_sub),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { confirmSeed = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_reload_demo))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.action_clear_metrics),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmSeed) {
        AlertDialog(
            onDismissRequest = { confirmSeed = false },
            title = { Text(stringResource(R.string.data_seed_title)) },
            text = { Text(stringResource(R.string.data_seed_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSeed = false
                        onSeedDemo()
                    },
                ) { Text(stringResource(R.string.action_reload_demo)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSeed = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.data_clear_title)) },
            text = { Text(stringResource(R.string.data_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun InfoScreen(onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_about), onClose = onClose)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_author),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_github),
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { uriHandler.openUri("https://github.com/JustZambetti/habitcommit") }
                .padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_license),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SyncStatusRow(status: SyncStatus, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = if (status.lastOk) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status.lastMessage ?: stringResource(R.string.sync_card_title),
                modifier = Modifier.weight(1f),
                color = if (status.lastOk) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (status.pending > 0) {
                    pluralStringResource(R.plurals.pending_count, status.pending, status.pending)
                } else {
                    stringResource(R.string.sync_manage)
                },
                color = if (status.lastOk) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.7).sp,
        )
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.close))
        }
    }
}

private fun pinWidget(context: Context, receiver: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.widget_pin_unsupported),
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun formatDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }
}
