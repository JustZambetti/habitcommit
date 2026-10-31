package dev.habitcommit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.BuildConfig
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Repaints the widgets just after local midnight so the date and plan roll over.
 *
 * An AlarmManager alarm is used instead of WorkManager: WorkManager is
 * initialised by androidx.startup on every process start, which made a cold
 * widget tap pay for it even though only GitHub sync uses it.
 */
object WidgetRefreshScheduler {

    private const val ACTION_REFRESH = "dev.habitcommit.action.REFRESH_WIDGETS"

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext as? HabitCommitApp ?: return
        val zone = app.repository.config().zone
        val now = ZonedDateTime.now(zone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).plusMinutes(1)
        val triggerAt = now.plus(Duration.between(now, nextMidnight)).toInstant().toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            // Inexact on purpose: a minute of drift after midnight is fine, and
            // exact alarms would need the user to grant SCHEDULE_EXACT_ALARM.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
        } catch (e: Exception) {
            Log.w("HabitCommitWidget", "widget refresh scheduling failed: ${e.javaClass.simpleName}")
        }
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val started = android.os.SystemClock.elapsedRealtime()
        refreshAllWidgets(context)
        WidgetRefreshScheduler.ensureScheduled(context)
        if (BuildConfig.DEBUG) {
            Log.d("HabitCommitWidget", "midnight refresh in ${android.os.SystemClock.elapsedRealtime() - started}ms")
        }
    }
}
