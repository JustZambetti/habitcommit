package dev.habitcommit.data

import java.time.LocalDate

/**
 * Local demo data so the first run shows a meaningful streak and heatmap.
 * It never touches GitHub and can be removed from the app with "Azzera dati".
 */
object DemoSeeder {

    fun seed(repository: HabitRepository) {
        val config = repository.config()
        val today = LocalDate.now(config.zone)

        for (offset in 1..12) {
            val date = today.minusDays(offset.toLong())
            val day = repository.resolvedDay(date) ?: continue
            if (day.metrics.isEmpty()) continue

            val count = when (offset % 5) {
                0 -> day.metrics.size
                1 -> (day.metrics.size - 1).coerceAtLeast(1)
                2 -> (day.metrics.size / 2).coerceAtLeast(1)
                3 -> 1
                else -> (day.metrics.size - 2).coerceAtLeast(1)
            }
            day.metrics.take(count).forEachIndexed { index, check ->
                repository.appendDemoEvent(
                    checkId = check.id,
                    date = date,
                    hour = 7 + index,
                    minute = (10 + offset * 3 + index * 7) % 60,
                    value = demoValue(check, offset + index),
                )
            }
        }

        val todayPlan = repository.resolvedDay(today) ?: return
        val preferred = listOf("review-pr", "reading")
            .mapNotNull { id -> todayPlan.metrics.firstOrNull { it.id == id } }
            .ifEmpty { todayPlan.metrics.take(2) }
        preferred.forEachIndexed { index, check ->
            repository.appendDemoEvent(
                checkId = check.id,
                date = today,
                hour = 7,
                minute = 12 + index * 29,
                value = demoValue(check, index + 2),
            )
        }
    }

    /** Ratings get a believable spread of stars; binary metrics stay on/off. */
    private fun demoValue(check: CheckDef, seed: Int): Int {
        if (check.type != CheckType.RATING) return 1
        val span = (check.maxValue - 1).coerceAtLeast(1)
        return 2 + (seed % span)
    }
}
