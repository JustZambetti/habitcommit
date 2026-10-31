package dev.habitcommit.sync

import android.content.Context
import java.util.UUID

/** Non-secret sync preferences. The PAT lives only in [TokenStore]. */
class SyncSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stable random id used in the remote file name (one file per install and month). */
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    fun target(): GithubTarget? {
        val owner = prefs.getString(KEY_OWNER, null) ?: return null
        val repo = prefs.getString(KEY_REPO, null) ?: return null
        val branch = prefs.getString(KEY_BRANCH, null) ?: GithubTarget.DEFAULT_BRANCH
        val dir = prefs.getString(KEY_JOURNAL_DIR, null) ?: GithubTarget.DEFAULT_JOURNAL_DIR
        return GithubTarget.parse(owner, repo, branch, dir).getOrNull()
    }

    fun saveTarget(target: GithubTarget) {
        prefs.edit()
            .putString(KEY_OWNER, target.owner)
            .putString(KEY_REPO, target.repo)
            .putString(KEY_BRANCH, target.branch)
            .putString(KEY_JOURNAL_DIR, target.journalDir)
            .apply()
    }

    fun clearTarget() {
        prefs.edit()
            .remove(KEY_OWNER)
            .remove(KEY_REPO)
            .remove(KEY_BRANCH)
            .remove(KEY_JOURNAL_DIR)
            .apply()
    }

    private companion object {
        const val PREFS = "habitcommit_sync"
        const val KEY_OWNER = "owner"
        const val KEY_REPO = "repo"
        const val KEY_BRANCH = "branch"
        const val KEY_JOURNAL_DIR = "journal_dir"
        const val KEY_DEVICE_ID = "device_id"
    }
}
