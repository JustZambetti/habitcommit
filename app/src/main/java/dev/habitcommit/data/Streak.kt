package dev.habitcommit.data

import java.time.LocalDate

/**
 * Consecutive days with at least one recorded value, ending today or yesterday.
 * A day counts only when the daily plan has metrics and the latest value of at
 * least one of them is above zero, so unchecking a metric also ends a streak.
 */
internal object Streak {

    fun count(today: LocalDate, dayActive: (LocalDate) -> Boolean): Int {
        var cursor = if (dayActive(today)) today else today.minusDays(1)
        var streak = 0
        while (dayActive(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
