package dev.habitcommit.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class CheckType(val wire: String) {
    BINARY("binary"),
    RATING("rating"),
    ;

    companion object {
        fun fromWire(value: String): CheckType? = entries.firstOrNull { it.wire == value }
    }
}

data class CheckDef(
    val id: String,
    val label: String,
    val type: CheckType = CheckType.BINARY,
    val max: Int = DEFAULT_RATING_MAX,
) {
    /** Highest storable value: 1 for binary metrics, the configured max for ratings. */
    val maxValue: Int get() = if (type == CheckType.RATING) max else 1

    companion object {
        const val DEFAULT_RATING_MAX = 5
        const val MIN_RATING_MAX = 2
        const val MAX_RATING_MAX = 10
    }
}

data class PlanDef(
    val id: String,
    val label: String,
    val items: List<String>,
)

sealed interface DayOverride {
    data class UsePlan(val planId: String) : DayOverride
    data class Modify(val add: List<String>, val remove: List<String>) : DayOverride
}

data class ScheduleDef(
    val byWeekday: Map<DayOfWeek, String>,
    val overrides: Map<LocalDate, DayOverride>,
)

data class HabitsConfig(
    val schema: Int,
    val timezone: String,
    val metrics: Map<String, CheckDef>,
    val plans: Map<String, PlanDef>,
    val schedule: ScheduleDef,
) {
    val zone: ZoneId by lazy { ZoneId.of(timezone) }

    companion object {
        private val idPattern = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val weekdayKeys = mapOf(
            "mon" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY,
        )

        fun parse(
            json: String,
            messages: ConfigMessages = EnglishConfigMessages,
        ): HabitsConfig {
            return try {
                parseValidated(json, messages)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: org.json.JSONException) {
                throw IllegalArgumentException(messages.invalidJson(e.message.orEmpty()), e)
            }
        }

        private fun parseValidated(json: String, messages: ConfigMessages): HabitsConfig {
            val root = JSONObject(json)
            val schema = root.optInt("schema", 0)
            require(schema == 1) { messages.unsupportedSchema(schema, 1) }

            val timezone = root.optString("timezone", "").trim()
            require(timezone.isNotEmpty()) { messages.missingTimezone() }
            try {
                ZoneId.of(timezone)
            } catch (e: Exception) {
                throw IllegalArgumentException(messages.invalidTimezone(timezone))
            }

            val metrics = linkedMapOf<String, CheckDef>()
            // "checks" is the pre-rating name of the list; still accepted so
            // existing configs keep working.
            val metricsArray = root.optJSONArray("metrics")
                ?: root.optJSONArray("checks")
                ?: throw IllegalArgumentException(
                    when {
                        root.has("checks") -> messages.metricsNotList(legacyKey = true)
                        root.has("metrics") -> messages.metricsNotList(legacyKey = false)
                        else -> messages.metricsMissing()
                    },
                )
            for (i in 0 until metricsArray.length()) {
                val item = metricsArray.getJSONObject(i)
                val id = item.optString("id").trim()
                require(idPattern.matches(id)) { messages.invalidMetricId(id) }
                require(!metrics.containsKey(id)) { messages.duplicateMetric(id) }
                val label = item.optString("label", id).trim().ifEmpty { id }
                val typeText = item.optString("type", CheckType.BINARY.wire)
                val type = CheckType.fromWire(typeText)
                    ?: throw IllegalArgumentException(messages.invalidMetricType(id, typeText))
                val max = when {
                    item.has("max") -> {
                        require(type == CheckType.RATING) { messages.maxOnlyForRating(id) }
                        val parsed = item.optInt("max", -1)
                        require(parsed in CheckDef.MIN_RATING_MAX..CheckDef.MAX_RATING_MAX) {
                            messages.invalidMax(id, parsed, CheckDef.MIN_RATING_MAX, CheckDef.MAX_RATING_MAX)
                        }
                        parsed
                    }
                    else -> CheckDef.DEFAULT_RATING_MAX
                }
                metrics[id] = CheckDef(
                    id = id,
                    label = label,
                    type = type,
                    max = max,
                )
            }
            require(metrics.isNotEmpty()) { messages.metricsEmpty() }

            val plans = linkedMapOf<String, PlanDef>()
            val plansObject = root.optJSONObject("plans")
                ?: throw IllegalArgumentException(
                    if (root.has("plans")) messages.plansNotObject() else messages.plansMissing(),
                )
            for (planId in plansObject.keys()) {
                require(idPattern.matches(planId)) { messages.invalidPlanId(planId) }
                val planObject = plansObject.getJSONObject(planId)
                val label = planObject.optString("label", planId).trim().ifEmpty { planId }
                val itemsArray = planObject.optJSONArray("items") ?: org.json.JSONArray()
                val items = linkedSetOf<String>()
                for (i in 0 until itemsArray.length()) {
                    val checkId = itemsArray.getString(i)
                    require(metrics.containsKey(checkId)) {
                        messages.unknownMetricInPlan(planId, checkId)
                    }
                    items += checkId
                }
                plans[planId] = PlanDef(planId, label, items.toList())
            }
            require(plans.isNotEmpty()) { messages.plansEmpty() }

            val scheduleObject = root.optJSONObject("schedule")
            val byWeekday = linkedMapOf<DayOfWeek, String>()
            val overrides = linkedMapOf<LocalDate, DayOverride>()

            val weekdayObject = scheduleObject?.optJSONObject("byWeekday")
            if (weekdayObject != null) {
                for (key in weekdayObject.keys()) {
                    val day = weekdayKeys[key]
                        ?: throw IllegalArgumentException(messages.invalidWeekday(key))
                    val planId = weekdayObject.getString(key)
                    require(plans.containsKey(planId)) {
                        messages.unknownPlanInWeekday(key, planId)
                    }
                    byWeekday[day] = planId
                }
            }

            val overridesObject = scheduleObject?.optJSONObject("overrides")
            if (overridesObject != null) {
                for (dateText in overridesObject.keys()) {
                    val date = try {
                        LocalDate.parse(dateText)
                    } catch (e: Exception) {
                        throw IllegalArgumentException(messages.invalidOverrideDate(dateText))
                    }
                    val value = overridesObject.getJSONObject(dateText)
                    when {
                        value.has("plan") -> {
                            val planId = value.getString("plan")
                            require(plans.containsKey(planId)) {
                                messages.unknownPlanInOverride(dateText, planId)
                            }
                            overrides[date] = DayOverride.UsePlan(planId)
                        }
                        else -> {
                            val add = value.optJSONArray("add").stringList()
                            val remove = value.optJSONArray("remove").stringList()
                            add.forEach {
                                require(metrics.containsKey(it)) {
                                    messages.unknownMetricAdded(dateText, it)
                                }
                            }
                            remove.forEach {
                                require(metrics.containsKey(it)) {
                                    messages.unknownMetricRemoved(dateText, it)
                                }
                            }
                            overrides[date] = DayOverride.Modify(add = add, remove = remove)
                        }
                    }
                }
            }

            return HabitsConfig(
                schema = schema,
                timezone = timezone,
                metrics = metrics,
                plans = plans,
                schedule = ScheduleDef(byWeekday, overrides),
            )
        }

        private fun org.json.JSONArray?.stringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) add(getString(i))
            }
        }
    }
}
