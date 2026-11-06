package dev.habitcommit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * Widget strings are rendered by the app, not by the launcher, so a locale
 * change (system or per-app) leaves the pinned widgets in the old language
 * until something else repaints them. Rebuild them right away instead.
 */
class LocaleChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        thread(name = "habitcommit-locale") {
            try {
                refreshAllWidgets(appContext)
            } catch (e: Exception) {
                Log.w("HabitCommitWidget", "locale refresh failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
