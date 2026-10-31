package dev.habitcommit.widget

import dev.habitcommit.data.TodayState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared state the widgets observe.
 *
 * Glance keeps a widget session alive for ~45 seconds: an update inside that
 * window does not necessarily re-run provideGlance. Publishing the new state
 * here lets any live composition recompose immediately; changed state also
 * triggers an in-process widget update (see refreshAllWidgets).
 */
object WidgetState {
    val today = MutableStateFlow<TodayState?>(null)

    /** Returns true when the state actually changed. */
    fun publish(state: TodayState): Boolean {
        if (today.value == state) return false
        today.value = state
        return true
    }
}
