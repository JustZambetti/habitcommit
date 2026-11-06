package dev.habitcommit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.sync.GithubSyncScheduler
import kotlin.concurrent.thread

/**
 * Alarms do not survive a reboot, and the launcher may not call onUpdate until
 * something else changes. Re-arm the midnight refresh and repaint the widgets
 * with the new day's plan as soon as the phone finishes booting.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        thread(name = "habitcommit-boot") {
            try {
                WidgetRefreshScheduler.ensureScheduled(appContext)
                refreshAllWidgets(appContext)
                if ((appContext as? HabitCommitApp)?.isSyncConfigured() == true) {
                    GithubSyncScheduler.ensureDaily(appContext)
                }
            } catch (e: Exception) {
                Log.w("HabitCommitWidget", "boot refresh failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
