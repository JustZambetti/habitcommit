package dev.habitcommit.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Initialises WorkManager on demand.
 *
 * The default androidx.startup initializer is removed in the manifest so that
 * starting the app (or a cold widget tap) does not pay for WorkManager until
 * GitHub sync is actually configured and requested.
 */
internal object WorkBootstrap {

    @Volatile
    private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
            } catch (e: IllegalStateException) {
                // Already initialised in this process; nothing to do.
            }
            initialized = true
        }
    }
}
