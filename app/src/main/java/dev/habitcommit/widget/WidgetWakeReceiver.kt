package dev.habitcommit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.habitcommit.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Warms the widget path when the user unlocks the phone.
 *
 * A cold tap otherwise pays for process start plus WorkManager/Glance session
 * startup before the first repaint (measured ~2-3 s on an emulator). Turning
 * the screen on happens one to two seconds before the first tap, so doing the
 * same work here in the background makes that tap hit a warm process (~100 ms).
 */
class WidgetWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT && intent.action != Intent.ACTION_SCREEN_ON) return
        if (BuildConfig.DEBUG) Log.d("HabitCommitWidget", "wake: ${intent.action}")
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshAllWidgets(appContext)
            } catch (e: Exception) {
                // A failed warm-up only means the next tap is slow, never lost.
                Log.w("HabitCommitWidget", "wake refresh failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
