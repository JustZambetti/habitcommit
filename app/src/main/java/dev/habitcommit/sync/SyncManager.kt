package dev.habitcommit.sync

import android.content.Context
import dev.habitcommit.R
import dev.habitcommit.data.CheckEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

data class SyncStatus(
    val configured: Boolean,
    val repoLabel: String?,
    val branch: String?,
    val journalDir: String?,
    val pending: Int,
    val lastMessage: String?,
    val lastOk: Boolean,
    val lastSyncAt: Instant?,
)

/**
 * Owns sync state and the security-sensitive token. UI and worker talk to this
 * class only; neither touches the Keystore or the outbox file directly.
 */
class SyncManager(
    context: Context,
    val settings: SyncSettings,
    val outbox: SyncOutbox,
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tokenStore = TokenStore(appContext)

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun isConfigured(): Boolean = settings.target() != null && tokenStore.hasToken()

    /** Called after a value is journaled locally. Never performs network I/O. */
    fun onEventRecorded(event: CheckEvent) {
        val line = RemoteJournal.eventLine(event, settings.deviceId)
        outbox.add(event.id, event.localDate, line)
        refresh()
        if (isConfigured()) GithubSyncScheduler.request(appContext)
    }

    fun saveConnection(target: GithubTarget, token: String?): Result<Unit> {
        return try {
            if (!token.isNullOrBlank()) tokenStore.save(token)
            require(tokenStore.hasToken()) { appContext.getString(R.string.error_token_required) }
            settings.saveTarget(target)
            prefs.edit()
                .putString(
                    KEY_MESSAGE,
                    appContext.getString(
                        R.string.sync_saved,
                        target.repoFullName,
                        target.branch,
                        target.journalDir,
                    ),
                )
                .putBoolean(KEY_OK, true)
                .remove(KEY_SYNC_AT)
                .apply()
            refresh()
            GithubSyncScheduler.request(appContext)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(
                    e.message ?: appContext.getString(R.string.invalid_configuration),
                ),
            )
        }
    }

    fun disconnect() {
        tokenStore.clear()
        settings.clearTarget()
        prefs.edit()
            .putString(KEY_MESSAGE, appContext.getString(R.string.sync_disconnected))
            .putBoolean(KEY_OK, true)
            .remove(KEY_SYNC_AT)
            .apply()
        refresh()
    }

    fun verify(target: GithubTarget, token: String): Result<GithubClient.RepoInfo> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException(appContext.getString(R.string.error_token_required)))
        }
        return GithubClient(appContext.resources, token.trim(), target).verify()
    }

    fun downloadConfig(): Result<String> {
        val target = settings.target()
            ?: return Result.failure(
                IllegalStateException(appContext.getString(R.string.error_sync_not_configured)),
            )
        val token = tokenStore.load()
            ?: return Result.failure(
                IllegalStateException(appContext.getString(R.string.sync_token_missing)),
            )
        return GithubClient(appContext.resources, token, target).downloadConfig()
    }

    fun token(): String? = tokenStore.load()

    /** Marks that habits.json was replaced by the repo copy. */
    fun noteConfigPulled() {
        prefs.edit()
            .putString(KEY_MESSAGE, appContext.getString(R.string.config_pulled))
            .putBoolean(KEY_OK, true)
            .apply()
        refresh()
    }

    fun recordSuccess(pendingLeft: Int) {
        prefs.edit()
            .putString(
                KEY_MESSAGE,
                if (pendingLeft > 0) {
                    appContext.getString(R.string.sync_syncing_remaining, pendingLeft)
                } else {
                    appContext.getString(R.string.sync_all_synced)
                },
            )
            .putBoolean(KEY_OK, true)
            .putLong(KEY_SYNC_AT, Instant.now().epochSecond)
            .apply()
        refresh()
    }

    fun recordFailure(message: String) {
        prefs.edit()
            .putString(KEY_MESSAGE, message.take(240))
            .putBoolean(KEY_OK, false)
            .apply()
        refresh()
    }

    fun refresh() {
        _status.value = readStatus()
    }

    private fun readStatus(): SyncStatus {
        val target = settings.target()
        return SyncStatus(
            configured = target != null && tokenStore.hasToken(),
            repoLabel = target?.repoFullName,
            branch = target?.branch,
            journalDir = target?.journalDir,
            pending = outbox.count(),
            lastMessage = prefs.getString(KEY_MESSAGE, null),
            lastOk = prefs.getBoolean(KEY_OK, true),
            lastSyncAt = prefs.getLong(KEY_SYNC_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochSecond),
        )
    }

    private companion object {
        const val PREFS = "habitcommit_sync_status"
        const val KEY_MESSAGE = "last_message"
        const val KEY_OK = "last_ok"
        const val KEY_SYNC_AT = "last_sync_at"
    }
}
