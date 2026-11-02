package dev.habitcommit.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val pending = manager.outbox.pending()
        if (pending.isEmpty()) {
            manager.recordSuccess(0)
            return@withContext Result.success()
        }

        val client = GithubClient(applicationContext.resources, token, target)
        val batch = pending.take(MAX_PER_RUN)
        for (event in batch) {
            val result = client.appendEvent(event, manager.settings.deviceId)
            if (result.isSuccess) {
                manager.outbox.remove(event.id)
                continue
            }
            val error = result.exceptionOrNull()
            val message = error?.message ?: applicationContext.getString(R.string.sync_failed)
            manager.recordFailure(message)
            return@withContext if ((error as? GithubClient.GithubException)?.retryable == true) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

        val remaining = manager.outbox.count()
        manager.recordSuccess(remaining)
        // Retry continues the same unique work after the backoff; requesting new
        // work from inside a running unique job would be dropped by KEEP.
        if (remaining > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_PER_RUN = 25
    }
}

object GithubSyncScheduler {

    private const val UNIQUE_WORK = "habitcommit-github-sync"
    private const val TAG = "github-sync"

    /** Idempotent: KEEP means a queued/running sync absorbs this request. */
    fun request(context: Context) {
        WorkBootstrap.ensure(context)
        val request = OneTimeWorkRequestBuilder<GithubSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
