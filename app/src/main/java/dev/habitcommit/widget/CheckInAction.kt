package dev.habitcommit.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import dev.habitcommit.BuildConfig
import dev.habitcommit.HabitCommitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val CHECK_ID_KEY = ActionParameters.Key<String>("check_id")
val CHECK_VALUE_KEY = ActionParameters.Key<Int>("check_value")

private const val TAG = "HabitCommitWidget"

/** Whole-row tap on a binary check: on if untouched, off if already done. */
class CheckInAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val started = SystemClock.elapsedRealtime()
        val checkId = parameters[CHECK_ID_KEY] ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "action start: $checkId")
        val repository = (context.applicationContext as HabitCommitApp).repository
        withContext(Dispatchers.IO) {
            repository.toggle(checkId)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "action toggled: $checkId")
        refreshAllWidgets(context, priority = glanceId, deferOthers = true)
        if (BuildConfig.DEBUG) Log.d(TAG, "action done: $checkId in ${SystemClock.elapsedRealtime() - started}ms")
    }
}

/**
 * Star tap on a rating check: stores the chosen value. The widget passes 0 when
 * the star tapped is the current value, so a second tap on the same star clears it.
 */
class SetValueAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val started = SystemClock.elapsedRealtime()
        val checkId = parameters[CHECK_ID_KEY] ?: return
        val value = parameters[CHECK_VALUE_KEY] ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "rating start: $checkId=$value")
        val repository = (context.applicationContext as HabitCommitApp).repository
        withContext(Dispatchers.IO) {
            repository.setValue(checkId, value)
        }
        refreshAllWidgets(context, priority = glanceId, deferOthers = true)
        if (BuildConfig.DEBUG) Log.d(TAG, "rating done: $checkId=$value in ${SystemClock.elapsedRealtime() - started}ms")
    }
}

/**
 * Re-reads today and repaints every pinned widget.
 *
 * [priority] is the widget that received the tap: it is rendered first so the
 * component the user is looking at updates before the others.
 */
suspend fun refreshAllWidgets(
    context: Context,
    priority: GlanceId? = null,
    deferOthers: Boolean = false,
) {
    val started = SystemClock.elapsedRealtime()
    val appContext = context.applicationContext
    val repository = (appContext as HabitCommitApp).repository
    val state = withContext(Dispatchers.IO) { repository.today() }
    val afterToday = SystemClock.elapsedRealtime()
    val changed = WidgetState.publish(state)
    if (BuildConfig.DEBUG) Log.d(TAG, "published state: ${state.doneCount}/${state.total} changed=$changed today=${afterToday - started}ms")
    // Nothing changed (e.g. the app resumed and re-read the same day): the RemoteViews
    // already show this state.
    if (!changed) return
    // Glance's updateAll() enqueues one expedited WorkManager SessionWorker per
    // widget, and those are rationed: after an idle period a tap could take
    // seconds to repaint. update(context, id) renders the widget in-process,
    // synchronously, so all pinned sizes repaint in a few dozen milliseconds.
    val manager = GlanceAppWidgetManager(appContext)
    val widgets = listOf(Today44Widget(), Today42Widget(), Today22Widget(), Today24Widget())
    val targets = widgets.map { widget ->
        widget to runCatching { manager.getGlanceIds(widget::class.java) }.getOrDefault(emptyList())
    }
    val priorityKey = priority?.toString()
    // Flat list of widget instances, the tapped one first.
    val instances = targets.flatMap { (widget, ids) -> ids.map { widget to it } }
    val ordered = instances.sortedByDescending { (_, id) -> id.toString() == priorityKey }
    suspend fun paint(batch: List<Pair<androidx.glance.appwidget.GlanceAppWidget, GlanceId>>) {
        batch.forEachIndexed { index, (widget, id) ->
            val one = SystemClock.elapsedRealtime()
            runCatching { widget.update(appContext, id) }
                .onFailure { Log.w(TAG, "update failed for $id: ${it.message}") }
            if (BuildConfig.DEBUG) Log.d(TAG, "widget update #${index + 1} ${widget.javaClass.simpleName} ${SystemClock.elapsedRealtime() - one}ms")
        }
    }
    if (deferOthers) {
        // Only the widget the user just tapped blocks the action; repainting the
        // other pinned instances would add ~0.6 s before the tap is visible.
        paint(ordered.take(1))
        if (BuildConfig.DEBUG) Log.d(TAG, "priority repaint ${SystemClock.elapsedRealtime() - started}ms")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { paint(ordered.drop(1)) }
    } else {
        paint(ordered)
    }
    if (BuildConfig.DEBUG) Log.d(TAG, "refreshAllWidgets total ${SystemClock.elapsedRealtime() - started}ms")
}
