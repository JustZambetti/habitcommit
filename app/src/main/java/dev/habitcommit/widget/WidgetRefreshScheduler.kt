package dev.habitcommit.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.habitcommit.HabitCommitApp
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        runBlocking {
            refreshAllWidgets(applicationContext)
            WidgetRefreshScheduler.ensureScheduled(applicationContext)
        }
        return Result.success()
    }
}

object WidgetRefreshScheduler {
    private const val UNIQUE_WORK = "habitcommit-widget-midnight"

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext as? HabitCommitApp ?: return
        val zone = app.repository.config().zone
        val now = ZonedDateTime.now(zone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).plusMinutes(1)
        val delay = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)

        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
