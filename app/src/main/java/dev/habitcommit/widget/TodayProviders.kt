package dev.habitcommit.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.habitcommit.BuildConfig
import dev.habitcommit.HabitCommitApp

private const val TAG = "HabitCommitWidget"

abstract class BaseTodayProvider(private val size: WidgetSize) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val started = SystemClock.elapsedRealtime()
        val state = (context.applicationContext as HabitCommitApp).repository.today()
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, WidgetViews.build(context, id, size, state))
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onUpdate $size ${appWidgetIds.size} widgets in ${SystemClock.elapsedRealtime() - started}ms")
        }
    }
}

class Today44Receiver : BaseTodayProvider(WidgetSize.LARGE)
class Today42Receiver : BaseTodayProvider(WidgetSize.MEDIUM)
class Today22Receiver : BaseTodayProvider(WidgetSize.SMALL)
class Today24Receiver : BaseTodayProvider(WidgetSize.TALL)

private val ALL_PROVIDERS = listOf(
    Today44Receiver::class.java to WidgetSize.LARGE,
    Today42Receiver::class.java to WidgetSize.MEDIUM,
    Today22Receiver::class.java to WidgetSize.SMALL,
    Today24Receiver::class.java to WidgetSize.TALL,
)

/**
 * Repaints every pinned widget from the current journal.
 *
 * Building RemoteViews is cheap and synchronous, so this stays on the caller's
 * thread: after a tap the value is on screen before the broadcast returns.
 */
fun refreshAllWidgets(context: Context) {
    val started = SystemClock.elapsedRealtime()
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val pinned = ALL_PROVIDERS.mapNotNull { (provider, size) ->
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(appContext, provider))
        }.getOrDefault(IntArray(0))
        if (ids.isEmpty()) null else Triple(provider, size, ids)
    }
    // Screen-on and boot broadcasts are frequent; skip parsing the journal when
    // no widget is pinned.
    if (pinned.isEmpty()) return
    val state = (appContext as HabitCommitApp).repository.today()
    var updated = 0
    pinned.forEach { (_, size, ids) ->
        ids.forEach { id ->
            runCatching { manager.updateAppWidget(id, WidgetViews.build(appContext, id, size, state)) }
                .onFailure { Log.w(TAG, "update failed for $id: ${it.message}") }
            updated++
        }
    }
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "refreshAllWidgets ${SystemClock.elapsedRealtime() - started}ms ($updated widgets)")
    }
}
