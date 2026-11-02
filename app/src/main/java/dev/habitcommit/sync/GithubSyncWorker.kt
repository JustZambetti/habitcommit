package dev.habitcommit.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.R
import dev.habitcommit.widget.refreshAllWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Flushes the outbox one commit per recorded value. Runs only with network
 * available; failures never touch the local journal, which is already durable.
 */
class GithubSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? HabitCommitApp ?: return@withContext Result.success()
        val manager = app.syncManager
        val target = manager.settings.target() ?: return@withContext Result.success()
        val token = manager.token() ?: run {
            manager.recordFailure(applicationContext.getString(R.string.sync_token_missing))
            return@withContext Result.failure()
        }

        // Plans live in the repo: refresh them even when there is nothing to push.
        if (app.pullConfigFromGithub()) {
            refreshAllWidgets(applicationContext)
        }

        val pending = manager.outbox.pending()
        if (pending.isEmpty()) {
            manager.recordSuccess(0)
            return@withContext Result.success()
        }

        val client = GithubClient(applicationContext.resources, token, target)
        val batch = pending.take(MAX_PER_RUN)
        // One commit per journal file for the whole batch, not one per value.
        val result = client.appendEvents(batch, manager.settings.deviceId)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            val message = error?.message ?: applicationContext.getString(R.string.sync_failed)
            manager.recordFailure(message)
            return@withContext if ((error as? GithubClient.GithubException)?.retryable == true) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
        batch.forEach { manager.outbox.remove(it.id) }

        val remaining = manager.outbox.count()
        manager.recordSuccess(remaining)
        // Retry continues the same unique work after the backoff; requesting new
        // work from inside a running unique job would be dropped by KEEP.
        if (remaining > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_PER_RUN = 200
    }
}

object GithubSyncScheduler {

    private const val UNIQUE_WORK = "habitcommit-github-sync"
    private const val UNIQUE_DAILY = "habitcommit-github-daily"
    private const val TAG = "github-sync"

    /** Local hour of the daily flush; the phone may run it later if asleep or offline. */
    private const val DAILY_HOUR = 22

    /** Idempotent: KEEP means a queued/running sync absorbs this request. */
    fun request(context: Context) {
        WorkBootstrap.ensure(context)
        val request = OneTimeWorkRequestBuilder<GithubSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Flushes the outbox once a day instead of committing on every tap. The
     * first run is delayed to [DAILY_HOUR] so a day's values travel together.
     */
    fun ensureDaily(context: Context) {
        WorkBootstrap.ensure(context)
        val request = PeriodicWorkRequestBuilder<GithubSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextDailyRun(), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelDaily(context: Context) {
        WorkBootstrap.ensure(context)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_DAILY)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun delayUntilNextDailyRun(): Long {
        val now = ZonedDateTime.now()
        var next = now.withHour(DAILY_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
