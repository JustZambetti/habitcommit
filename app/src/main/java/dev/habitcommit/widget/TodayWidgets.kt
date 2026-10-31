package dev.habitcommit.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.habitcommit.BuildConfig
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.MainActivity
import dev.habitcommit.data.CheckType
import dev.habitcommit.data.CheckUi
import dev.habitcommit.data.TodayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class WidgetSize { LARGE, MEDIUM, SMALL, TALL }

abstract class BaseTodayWidget(private val size: WidgetSize) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as HabitCommitApp).repository
        val initial = withContext(Dispatchers.IO) { repository.today() }
        val appContext = context.applicationContext
        if (WidgetState.today.value == null) WidgetState.publish(initial)
        provideContent {
            // A long-lived Glance session keeps composing from this flow, so a
            // check-in repaints immediately instead of waiting for a new session.
            val state = WidgetState.today.collectAsState().value ?: initial
            if (BuildConfig.DEBUG) android.util.Log.d(
                "HabitCommitWidget",
                "compose $size ${state.doneCount}/${state.total}",
            )
            when (size) {
                WidgetSize.LARGE -> TodayWidgetLarge(appContext, state)
                WidgetSize.MEDIUM -> TodayWidgetMedium(appContext, state)
                WidgetSize.SMALL -> TodayWidgetSmall(state)
                WidgetSize.TALL -> TodayWidgetTall(state)
            }
        }
    }
}

class Today44Widget : BaseTodayWidget(WidgetSize.LARGE)
class Today42Widget : BaseTodayWidget(WidgetSize.MEDIUM)
class Today22Widget : BaseTodayWidget(WidgetSize.SMALL)
class Today24Widget : BaseTodayWidget(WidgetSize.TALL)

class Today44Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Today44Widget()
}

class Today42Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Today42Widget()
}

class Today22Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Today22Widget()
}

class Today24Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Today24Widget()
}

@Composable
private fun TodayWidgetLarge(context: Context, state: TodayState) {
    val openApp = actionStartActivity<MainActivity>()
    val openConfig = configAction(context)

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(WidgetColors.Background))
            .cornerRadius(24.dp)
            .padding(14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(state, openApp, visibleCount = 5)

            Separator(
                modifier = GlanceModifier.padding(top = 7.dp, bottom = 2.dp),
            )

            Spacer(GlanceModifier.defaultWeight())
            CheckList(state.metrics, max = 5, compact = false)
            Spacer(GlanceModifier.defaultWeight())

            StreakRow(state, heatCells = state.heat.takeLast(14), compact = false)
            TomorrowRow(
                state = state,
                modifier = GlanceModifier.padding(top = 5.dp),
            )

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                WidgetButton(
                    label = "Configura",
                    action = openConfig,
                    solid = false,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton(
                    label = "Apri app",
                    action = openApp,
                    solid = true,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun TodayWidgetMedium(context: Context, state: TodayState) {
    val openApp = actionStartActivity<MainActivity>()

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(WidgetColors.Background))
            .cornerRadius(22.dp)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().clickable(openApp)) {
                    Text(
                        text = "OGGI · ${widgetDate(state.date)}",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Faint),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = state.plan.label,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Text),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${state.doneCount}/${state.total}",
                    modifier = GlanceModifier.clickable(openApp),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Text),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(3.dp))
            CheckList(state.metrics, max = 4, compact = true)

            Spacer(GlanceModifier.defaultWeight())
            StreakRow(state, heatCells = state.heat.takeLast(12), compact = true)
        }
    }
}

@Composable
private fun TodayWidgetSmall(state: TodayState) {
    val openApp = actionStartActivity<MainActivity>()
    val percent = (state.progress * 100).roundToInt()

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(WidgetColors.Background))
            .cornerRadius(22.dp)
            .padding(10.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "OGGI",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Faint),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = state.plan.label,
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Text),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${state.doneCount}/${state.total}",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Text),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Spacer(GlanceModifier.height(2.dp))
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = GlanceModifier
                        .size(42.dp)
                        .cornerRadius(21.dp)
                        .background(ColorProvider(WidgetColors.Green)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$percent%",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.White),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.height(3.dp))
            HeatRow(
                levels = state.heat.takeLast(9),
                cellSize = 6.dp,
                centered = true,
            )

            // Checks are a set, not a queue: there is no "next" item to check
            // with a single button, so the small widget is a status overview.
            Text(
                text = "${state.streak} giorni di fila",
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                style = TextStyle(
                    color = ColorProvider(WidgetColors.GreenDeep),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )

            Spacer(GlanceModifier.defaultWeight())
            WidgetButton(
                label = "APRI APP",
                action = openApp,
                solid = true,
                modifier = GlanceModifier.fillMaxWidth(),
                height = 32.dp,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TodayWidgetTall(state: TodayState) {
    val openApp = actionStartActivity<MainActivity>()

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(WidgetColors.Background))
            .cornerRadius(22.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = "OGGI · ${widgetDate(state.date)}",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.Faint),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.plan.label,
                    modifier = GlanceModifier.defaultWeight().clickable(openApp),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Text),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = "${state.doneCount}/${state.total}",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Text),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }

            Separator(modifier = GlanceModifier.padding(top = 7.dp, bottom = 4.dp))
            CheckList(state.metrics, max = 4, compact = true)

            Spacer(GlanceModifier.defaultWeight())
            StreakRow(state, heatCells = state.heat.takeLast(9), compact = true)
            Spacer(GlanceModifier.height(6.dp))
            WidgetButton(
                label = "Apri app",
                action = openApp,
                solid = false,
                modifier = GlanceModifier.fillMaxWidth(),
                height = 36.dp,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WidgetHeader(state: TodayState, openApp: Action, visibleCount: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(
            modifier = GlanceModifier.defaultWeight().clickable(openApp),
        ) {
            Text(
                text = "OGGI · ${widgetDate(state.date)}",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.Faint),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = state.plan.label,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.Text),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(3.dp))
            Row(
                modifier = GlanceModifier
                    .background(ColorProvider(WidgetColors.TerracottaSoft))
                    // Material 3 chip shape: 8dp corners, medium label.
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "PIANO · habits.json",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Terracotta),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${state.doneCount}/${state.total}",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.Text),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (state.metrics.size > visibleCount) {
                Text(
                    text = "+${state.metrics.size - visibleCount} in app",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Faint),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CheckList(metrics: List<CheckUi>, max: Int, compact: Boolean) {
    val visible = metrics.take(max)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (visible.isEmpty()) {
            Text(
                text = "Nessuna metrica per oggi",
                modifier = GlanceModifier.padding(vertical = 6.dp),
                style = TextStyle(
                    color = ColorProvider(WidgetColors.Muted),
                    fontSize = if (compact) 10.sp else 12.sp,
                ),
            )
        }
        visible.forEachIndexed { index, check ->
            CheckRow(check = check, compact = compact)
            if (!compact && index < visible.lastIndex) Separator()
        }
    }
}

@Composable
private fun CheckRow(check: CheckUi, compact: Boolean) {
    // Whole rows are the tap target; keep them tall enough to avoid mis-taps.
    val rowHeight = if (compact) 28.dp else 36.dp
    val rating = check.def.type == CheckType.RATING
    val base = GlanceModifier.fillMaxWidth().height(rowHeight)
    // Binary rows toggle anywhere; rating rows only respond on the stars, since
    // each star is a different value.
    val modifier = if (rating) {
        base
    } else {
        base.clickable(
            actionRunCallback<CheckInAction>(
                actionParametersOf(CHECK_ID_KEY to check.def.id),
            ),
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = check.def.label,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(WidgetColors.Text),
                fontSize = if (compact) 13.sp else 15.sp,
                // Material 3 list rows use body text (regular weight) with the
                // emphasis carried by the trailing control, not the label.
                fontWeight = FontWeight.Normal,
            ),
            maxLines = 1,
        )
        if (rating) {
            Spacer(GlanceModifier.width(if (compact) 4.dp else 6.dp))
            RatingStars(check = check, rowHeight = rowHeight, compact = compact)
        } else {
            check.timeLabel?.let { time ->
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = time,
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.Faint),
                        fontSize = if (compact) 8.5.sp else 9.5.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
            // Checkbox on the right: keeps the tap target away from the screen edge
            // and the system back-swipe zone. The whole row stays clickable.
            // Material 3 geometry: 2dp corners, 2dp outline when unchecked, solid
            // primary fill with a checkmark when checked. Glance 1.2 has no border
            // modifier, so the outline is an outer rounded box behind an inner fill.
            CheckboxBox(done = check.done, compact = compact)
        }
    }
}

@Composable
private fun CheckboxBox(done: Boolean, compact: Boolean) {
    // Material 3 checkbox is 18dp; the large widget uses 20dp for legibility.
    val boxSize = if (compact) 18.dp else 20.dp
    Box(
        modifier = GlanceModifier
            .size(boxSize)
            .cornerRadius(2.dp)
            .background(
                ColorProvider(if (done) WidgetColors.Green else WidgetColors.OnSurfaceVariant),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // Padding alone does not inset a background in Glance, so the
            // inner fill is sized explicitly, leaving a 2dp ring behind.
            modifier = GlanceModifier
                .size(if (done) boxSize else boxSize - 4.dp)
                .cornerRadius(if (done) 2.dp else 1.dp)
                .background(
                    ColorProvider(if (done) WidgetColors.Green else WidgetColors.Background),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text(
                    text = "✓",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.White),
                        fontSize = if (compact) 11.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RatingStars(check: CheckUi, rowHeight: Dp, compact: Boolean) {
    // One tap target per star. Tapping the current value clears the rating, so
    // a mis-tap is recoverable without a long press or the app.
    val cellWidth = if (compact) 15.dp else 18.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..check.def.maxValue) {
            val target = if (star == check.value) 0 else star
            val action = actionRunCallback<SetValueAction>(
                actionParametersOf(
                    CHECK_ID_KEY to check.def.id,
                    CHECK_VALUE_KEY to target,
                ),
            )
            Box(
                modifier = GlanceModifier
                    .size(cellWidth, rowHeight)
                    .clickable(action),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (star <= check.value) "★" else "☆",
                    style = TextStyle(
                        color = ColorProvider(
                            if (star <= check.value) WidgetColors.Green else WidgetColors.Outline,
                        ),
                        fontSize = if (compact) 13.sp else 16.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StreakRow(state: TodayState, heatCells: List<Int>, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeatRow(
            levels = heatCells,
            cellSize = if (compact) 5.5.dp else 7.dp,
            centered = false,
        )
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = "${state.streak} gg",
            style = TextStyle(
                color = ColorProvider(WidgetColors.GreenDeep),
                fontSize = if (compact) 8.5.sp else 9.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .size(4.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(WidgetColors.Green)),
        ) {}
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = "salvato ✓",
            style = TextStyle(
                color = ColorProvider(WidgetColors.Faint),
                fontSize = if (compact) 8.sp else 8.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun HeatRow(levels: List<Int>, cellSize: Dp, centered: Boolean) {
    // A Glance Row/Column can hold at most 10 direct children, so long
    // heatmaps are split in balanced rows and each cell owns its own gap.
    val chunkSize = if (levels.size <= 10) levels.size else (levels.size + 1) / 2
    val rows = levels.chunked(chunkSize.coerceAtLeast(1))
    Column(
        modifier = if (centered) GlanceModifier.fillMaxWidth() else GlanceModifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = if (centered) GlanceModifier.fillMaxWidth() else GlanceModifier,
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { level ->
                    Box(
                        modifier = GlanceModifier
                            .size(cellSize + 2.dp, cellSize)
                            .padding(end = 2.dp)
                            .cornerRadius(1.5.dp)
                            .background(
                                ColorProvider(
                                    WidgetColors.Heat[
                                        level.coerceIn(0, WidgetColors.Heat.lastIndex)
                                    ],
                                ),
                            ),
                    ) {}
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(GlanceModifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun TomorrowRow(state: TodayState, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Domani",
            style = TextStyle(
                color = ColorProvider(WidgetColors.Muted),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = state.tomorrowPlan?.label ?: "—",
            style = TextStyle(
                color = ColorProvider(WidgetColors.Terracotta),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = state.tomorrowPreview,
            style = TextStyle(
                color = ColorProvider(WidgetColors.Faint),
                fontSize = 8.sp,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetButton(
    label: String,
    action: Action,
    solid: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    height: Dp = 40.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) {
    // Material 3 button: 40dp tall, fully rounded (pill), labelLarge-ish
    // medium weight. Filled buttons use primary; secondary buttons use the
    // filled-tonal variant (primaryContainer / onPrimaryContainer).
    Box(
        modifier = modifier
            .height(height)
            .cornerRadius(height / 2)
            .background(
                ColorProvider(if (solid) WidgetColors.Green else WidgetColors.PrimaryContainer),
            )
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(
                    if (solid) WidgetColors.White else WidgetColors.OnPrimaryContainer,
                ),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Separator(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorProvider(WidgetColors.Divider)),
    ) {}
}

private fun configAction(context: Context): Action {
    return actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("habitcommit://config")
        },
    )
}

private fun widgetDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))
        .uppercase(Locale.ITALIAN)
}
