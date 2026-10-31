package dev.habitcommit.data

import java.time.LocalDate

enum class PlanSource {
    WEEKDAY,
    DATE_OVERRIDE,
    LOCAL_OVERRIDE,
    DEFAULT,
}

data class ResolvedDay(
    val date: LocalDate,
    val plan: PlanDef,
    val metrics: List<CheckDef>,
    val source: PlanSource,
    val modified: Boolean,
)

object PlanResolver {

    fun resolve(
        config: HabitsConfig,
        date: LocalDate,
        localOverridePlanId: String? = null,
    ): ResolvedDay? {
        val localPlan = localOverridePlanId?.let { config.plans[it] }
        val dateOverride = config.schedule.overrides[date]

        val (plan, source) = when {
            localPlan != null -> localPlan to PlanSource.LOCAL_OVERRIDE
            dateOverride is DayOverride.UsePlan -> {
                val overridden = config.plans[dateOverride.planId]
                if (overridden != null) overridden to PlanSource.DATE_OVERRIDE
                else fallback(config, date)
            }
            else -> {
                val scheduled = config.schedule.byWeekday[date.dayOfWeek]?.let { config.plans[it] }
                if (scheduled != null) scheduled to PlanSource.WEEKDAY
                else fallback(config, date)
            }
        } ?: return null

        val items = plan.items.toMutableList()
        var modified = false
        if (dateOverride is DayOverride.Modify) {
            val toRemove = dateOverride.remove.toSet()
            if (items.removeAll(toRemove)) modified = true
            dateOverride.add.forEach { checkId ->
                if (config.metrics.containsKey(checkId) && checkId !in items) {
                    items += checkId
                    modified = true
                }
            }
        }

        return ResolvedDay(
            date = date,
            plan = plan,
            metrics = items.mapNotNull { config.metrics[it] },
            source = source,
            modified = modified,
        )
    }

    private fun fallback(config: HabitsConfig, date: LocalDate): Pair<PlanDef, PlanSource>? {
        val first = config.plans.values.firstOrNull() ?: return null
        return first to PlanSource.DEFAULT
    }
}
