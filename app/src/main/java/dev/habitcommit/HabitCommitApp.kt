package dev.habitcommit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import dev.habitcommit.data.CheckEvent
import dev.habitcommit.data.HabitRepository
import dev.habitcommit.sync.GithubSyncScheduler
import dev.habitcommit.sync.RemoteJournal
import dev.habitcommit.sync.SyncManager
import dev.habitcommit.sync.SyncOutbox
import dev.habitcommit.sync.SyncSettings
import dev.habitcommit.sync.TokenStore
import dev.habitcommit.sync.WorkBootstrap
import dev.habitcommit.widget.WidgetRefreshScheduler
import dev.habitcommit.widget.WidgetWakeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class HabitCommitApp : Application() {

    lateinit var repository: HabitRepository
        private set

    // Everything sync-related is lazy: a cold widget tap must not pay for
    // Keystore classes, status flows or WorkManager scheduling before it can
    // journal the value.
    val syncSettings: SyncSettings by lazy { SyncSettings(this) }
    val syncOutbox: SyncOutbox by lazy { SyncOutbox(this) }
    private val tokenStore: TokenStore by lazy { TokenStore(this) }
    val syncManager: SyncManager by lazy { SyncManager(this, syncSettings, syncOutbox) }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        val started = SystemClock.elapsedRealtime()
        super.onCreate()
        repository = HabitRepository(this)
        repository.ensureSeeded()
        if (BuildConfig.DEBUG) {
            Log.d(
                "HabitCommitPerf",
                "app onCreate: ${SystemClock.elapsedRealtime() - started}ms",
            )
        }
        // Schedule after startup so the widget action path is not blocked by it.
        syncScope.launch { WidgetRefreshScheduler.ensureScheduled(this@HabitCommitApp) }
        // These implicit broadcasts are not delivered to manifest receivers on
        // modern Android, so the warm-up only works while the process is alive
        // (cached or frozen) � which covers all but the first tap after boot.
        // SCREEN_ON fires while the keyguard is still up, earlier than
        // USER_PRESENT, so the process is warm by the time the user unlocks.
        registerReceiver(
            WidgetWakeReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        // A persisted sync job can start the process in the background, and
        // WorkManager's SystemJobService needs it initialised first. The check
        // is SharedPreferences only, so the no-sync tap path stays untouched.
        if (syncSettings.target() != null) {
            WorkBootstrap.ensure(this)
            // Also heals installs that connected before daily scheduling existed.
            GithubSyncScheduler.ensureDaily(this)
        }
    }

    /** Local-first queueing for GitHub; called on the tap path and kept light. */
    fun recordSyncEvent(event: CheckEvent) {
        try {
            val line = RemoteJournal.eventLine(event, syncSettings.deviceId)
            syncOutbox.add(event.id, event.localDate, line)
        } catch (e: Exception) {
            // A full disk must not lose the local check-in that was already written.
            Log.w("HabitCommitSync", "outbox add failed: ${e.javaClass.simpleName}")
            return
        }
        // Queued values are committed together by the daily sync run, so the
        // tap path never touches WorkManager.
    }

    fun isSyncConfigured(): Boolean =
        syncSettings.target() != null && tokenStore.hasToken()

    fun clearSyncOutbox() {
        syncOutbox.clear()
    }

    /**
     * Pulls habits.json from the configured repo when the local copy differs.
     *
     * The repo is the source of truth for plans. Attempts are throttled so that
     * repeated resumes or sync runs do not hit the API unless needed. Returns
     * true when the local config changed, so the caller can refresh UI/widgets.
     */
    suspend fun pullConfigFromGithub(force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSyncConfigured()) return@withContext false
            val now = Instant.now().epochSecond
            if (!force && now - syncSettings.configPullAt() < CONFIG_PULL_THROTTLE_SECONDS) {
                return@withContext false
            }
            syncSettings.setConfigPullAt(now)
            val remote = syncManager.downloadConfig().getOrNull() ?: return@withContext false
            if (remote == repository.configText()) return@withContext false
            repository.saveConfig(remote).fold(
                onSuccess = {
                    syncManager.noteConfigPulled()
                    true
                },
                onFailure = { false },
            )
        }

    private companion object {
        const val CONFIG_PULL_THROTTLE_SECONDS = 300L
    }
}
