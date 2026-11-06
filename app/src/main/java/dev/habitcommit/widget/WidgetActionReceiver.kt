package dev.habitcommit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.habitcommit.BuildConfig
import dev.habitcommit.HabitCommitApp
import kotlin.concurrent.thread

/**
 * Handles widget taps directly, without Glance or WorkManager.
 *
 * The broadcast starts the app process if needed, then the journal append and
 * the RemoteViews update both happen before onReceive returns, so the launcher
 * shows the new value in the same frame the tap completes.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val checkId = intent.getStringExtra(EXTRA_CHECK_ID) ?: return
        val action = intent.action ?: return
        if (action != ACTION_TOGGLE && action != ACTION_SET) return
        val value = intent.getIntExtra(EXTRA_VALUE, 0)
        val started = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        val repository = (appContext as HabitCommitApp).repository
        // The journal and the widget render both touch disk and parse history;
        // keep them off the main thread while still finishing this broadcast.
        val pending = goAsync()
        thread(name = "habitcommit-widget") {
            try {
                when (action) {
                    ACTION_TOGGLE -> repository.toggle(checkId)
                    ACTION_SET -> repository.setValue(checkId, value)
                }
                if (BuildConfig.DEBUG) {
                    val processAge = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                    Log.d(
                        TAG,
                        "recorded $checkId in ${SystemClock.elapsedRealtime() - started}ms, " +
                            "process age ${processAge}ms",
                    )
                }
                refreshAllWidgets(appContext)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "action done: $checkId in ${SystemClock.elapsedRealtime() - started}ms")
                }
            } catch (e: Exception) {
                Log.w(TAG, "action failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "HabitCommitWidget"
        const val ACTION_TOGGLE = "dev.habitcommit.action.TOGGLE"
        const val ACTION_SET = "dev.habitcommit.action.SET"
        const val EXTRA_CHECK_ID = "check_id"
        const val EXTRA_VALUE = "check_value"
    }
}
