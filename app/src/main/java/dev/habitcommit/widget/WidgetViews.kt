package dev.habitcommit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import dev.habitcommit.MainActivity
import dev.habitcommit.R
import dev.habitcommit.data.CheckType
import dev.habitcommit.data.CheckUi
import dev.habitcommit.data.TodayState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class WidgetSize { LARGE, MEDIUM, SMALL, TALL }

/**
 * Builds the widget RemoteViews for one instance.
 *
 * This is deliberately plain RemoteViews instead of Glance: a tap is handled by
 * [WidgetActionReceiver] and the new view tree is applied in the same
 * broadcast, so the value appears immediately. Glance routes actions and
 * updates through a WorkManager session, which cost seconds on a cold process.
 */
object WidgetViews {

    fun build(
        context: Context,
        appWidgetId: Int,
        size: WidgetSize,
        state: TodayState,
    ): RemoteViews {
        val layout = when (size) {
            WidgetSize.LARGE -> R.layout.widget_today_44
            WidgetSize.MEDIUM -> R.layout.widget_today_42
            WidgetSize.SMALL -> R.layout.widget_today_22
            WidgetSize.TALL -> R.layout.widget_today_24
        }
        val views = RemoteViews(context.packageName, layout)
        val openApp = openAppPendingIntent(context)
        val openConfig = openConfigPendingIntent(context)

        views.setOnClickPendingIntent(R.id.header, openApp)

        // Static labels are set here too: per-app locale can differ from the
        // launcher's, and layout resources would otherwise use the system one.
        if (size == WidgetSize.SMALL) {
            views.setTextViewText(R.id.today_label, context.getString(R.string.widget_today_upper))
            views.setTextViewText(R.id.open, context.getString(R.string.widget_open_app_upper))
        } else if (size != WidgetSize.MEDIUM) {
            views.setTextViewText(R.id.open, context.getString(R.string.widget_open_app))
        }
        if (size == WidgetSize.LARGE) {
            views.setTextViewText(R.id.config, context.getString(R.string.widget_config))
            views.setTextViewText(R.id.chip, context.getString(R.string.widget_plan_file))
            views.setTextViewText(R.id.tomorrow_label, context.getString(R.string.widget_tomorrow))
        }
        if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) {
            views.setTextViewText(R.id.saved, context.getString(R.string.widget_saved))
        }

        val showDate = size != WidgetSize.SMALL
        if (showDate) {
            val label = context.getString(R.string.widget_today_upper)
            views.setTextViewText(R.id.date, "$label · ${widgetDate(state.date)}")
        }
        views.setTextViewText(R.id.plan, state.plan.label)
        views.setTextViewText(R.id.count, "${state.doneCount}/${state.total}")

        if (size == WidgetSize.LARGE) {
            val extra = state.metrics.size - VISIBLE_ROWS[WidgetSize.LARGE]!!
            if (extra > 0) {
                views.setTextViewText(R.id.extra, context.getString(R.string.widget_extra_in_app, extra))
            } else {
                views.setViewVisibility(R.id.extra, View.GONE)
            }
        }

        when (size) {
            WidgetSize.LARGE, WidgetSize.MEDIUM, WidgetSize.TALL -> {
                val max = VISIBLE_ROWS[size] ?: 4
                val compact = size != WidgetSize.LARGE
                bindRows(context, views, appWidgetId, state, max, compact, separated = size == WidgetSize.LARGE)
                val cell = when (size) {
                    WidgetSize.LARGE -> R.layout.widget_heat_cell
                    else -> R.layout.widget_heat_cell_compact
                }
                bindHeat(views, state.heat.takeLast(HEAT_CELLS[size] ?: 12), cell)
                views.setTextViewText(R.id.streak, context.getString(R.string.widget_streak_abbr, state.streak))
            }
            WidgetSize.SMALL -> {
                if (state.metrics.isEmpty()) {
                    views.setTextViewText(R.id.plan, context.getString(R.string.widget_empty))
                    views.setTextViewText(R.id.percent, "—")
                } else {
                    val percent = (state.progress * 100).roundToInt()
                    views.setTextViewText(R.id.percent, "$percent%")
                }
                bindHeat(views, state.heat.takeLast(9), R.layout.widget_heat_cell_small)
                val streak = context.resources.getQuantityString(
                    R.plurals.widget_streak_row,
                    state.streak,
                    state.streak,
                )
                views.setTextViewText(R.id.streak, streak)
            }
        }

        if (size == WidgetSize.LARGE) {
            views.setTextViewText(R.id.tomorrow_plan, state.tomorrowPlan?.label ?: "—")
            views.setTextViewText(R.id.tomorrow_preview, state.tomorrowPreview)
            views.setOnClickPendingIntent(R.id.config, openConfig)
        }
        // The 4×2 layout has no button: its header already opens the app.
        if (size != WidgetSize.MEDIUM) {
            views.setOnClickPendingIntent(R.id.open, openApp)
        }
        return views
    }

    private val VISIBLE_ROWS = mapOf(
        WidgetSize.LARGE to 5,
        WidgetSize.MEDIUM to 4,
        WidgetSize.TALL to 4,
    )

    private val HEAT_CELLS = mapOf(
        WidgetSize.LARGE to 14,
        WidgetSize.MEDIUM to 12,
        WidgetSize.TALL to 9,
    )

    private fun bindRows(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        state: TodayState,
        max: Int,
        compact: Boolean,
        separated: Boolean,
    ) {
        val visible = state.metrics.take(max)
        // Launchers may reapply the same RemoteViews instead of inflating
        // again; without this, rows would be appended on every update.
        views.removeAllViews(R.id.rows)
        if (visible.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.widget_row_empty)
            views.addView(R.id.rows, empty)
            return
        }
        visible.forEachIndexed { index, check ->
            val row = if (check.def.type == CheckType.RATING) {
                ratingRow(context, appWidgetId, check, compact)
            } else {
                binaryRow(context, appWidgetId, check, compact)
            }
            views.addView(R.id.rows, row)
            if (separated && index < visible.lastIndex) {
                views.addView(R.id.rows, RemoteViews(context.packageName, R.layout.widget_row_divider))
            }
        }
    }

    private fun binaryRow(
        context: Context,
        appWidgetId: Int,
        check: CheckUi,
        compact: Boolean,
    ): RemoteViews {
        val layout = if (compact) R.layout.widget_row_binary_compact else R.layout.widget_row_binary
        val row = RemoteViews(context.packageName, layout)
        row.setTextViewText(R.id.row_label, check.def.label)
        row.setTextViewText(R.id.row_time, check.timeLabel ?: "")
        row.setTextViewText(R.id.row_check, if (check.done) "✓" else "")
        row.setInt(
            R.id.row_check, "setBackgroundResource",
            if (check.done) R.drawable.widget_check_on else R.drawable.widget_check_off,
        )
        row.setOnClickPendingIntent(
            R.id.row_root,
            togglePendingIntent(context, appWidgetId, check.def.id),
        )
        row.setContentDescription(
            R.id.row_root,
            context.getString(
                if (check.done) R.string.widget_desc_binary_done else R.string.widget_desc_binary_open,
                check.def.label,
            ),
        )
        return row
    }

    private fun ratingRow(
        context: Context,
        appWidgetId: Int,
        check: CheckUi,
        compact: Boolean,
    ): RemoteViews {
        val layout = if (compact) R.layout.widget_row_rating_compact else R.layout.widget_row_rating
        val row = RemoteViews(context.packageName, layout)
        row.setTextViewText(R.id.row_label, check.def.label)
        val starLayout = if (compact) R.layout.widget_star_compact else R.layout.widget_star
        row.removeAllViews(R.id.row_stars)
        for (star in 1..check.def.maxValue) {
            val starView = RemoteViews(context.packageName, starLayout)
            val filled = star <= check.value
            starView.setTextViewText(R.id.star, if (filled) "★" else "☆")
            starView.setTextColor(
                R.id.star,
                if (filled) WidgetColors.Green else WidgetColors.Outline,
            )
            // Tapping the value already set clears the rating.
            val target = if (star == check.value) 0 else star
            starView.setOnClickPendingIntent(
                R.id.star,
                setValuePendingIntent(context, appWidgetId, check.def.id, target),
            )
            starView.setContentDescription(
                R.id.star,
                context.getString(
                    R.string.widget_desc_rating,
                    check.def.label,
                    star,
                    check.def.maxValue,
                ),
            )
            row.addView(R.id.row_stars, starView)
        }
        return row
    }

    private fun bindHeat(views: RemoteViews, levels: List<Int>, cellLayout: Int) {
        views.removeAllViews(R.id.heat)
        levels.forEach { level ->
            val cell = RemoteViews(views.getPackage(), cellLayout)
            cell.setInt(
                R.id.heat_cell,
                "setBackgroundResource",
                WidgetColors.heatDrawables[level.coerceIn(0, 4)],
            )
            views.addView(R.id.heat, cell)
        }
    }

    private fun togglePendingIntent(context: Context, appWidgetId: Int, checkId: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_TOGGLE
            data = "habitcommit://toggle/$appWidgetId/$checkId".toUri()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(WidgetActionReceiver.EXTRA_CHECK_ID, checkId)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setValuePendingIntent(
        context: Context,
        appWidgetId: Int,
        checkId: String,
        value: Int,
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_SET
            data = "habitcommit://set/$appWidgetId/$checkId/$value".toUri()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(WidgetActionReceiver.EXTRA_CHECK_ID, checkId)
            putExtra(WidgetActionReceiver.EXTRA_VALUE, value)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openConfigPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "habitcommit://config".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetDate(date: LocalDate): String {
        val locale = Locale.getDefault()
        return date.format(DateTimeFormatter.ofPattern("d MMM", locale))
            .uppercase(locale)
    }
}
