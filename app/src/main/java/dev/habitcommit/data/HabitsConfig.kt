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

        fun parse(json: String): HabitsConfig {
            return try {
                parseValidated(json)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: org.json.JSONException) {
                throw IllegalArgumentException("JSON non valido alla riga ${e.message}", e)
            }
        }

        private fun parseValidated(json: String): HabitsConfig {
            val root = JSONObject(json)
            val schema = root.optInt("schema", 0)
            require(schema == 1) { "schema non supportato: $schema (atteso 1)" }

            val timezone = root.optString("timezone", "").trim()
            require(timezone.isNotEmpty()) { "timezone mancante" }
            try {
                ZoneId.of(timezone)
            } catch (e: Exception) {
                throw IllegalArgumentException("timezone non valido: $timezone")
            }

            val metrics = linkedMapOf<String, CheckDef>()
            // "checks" is the pre-rating name of the list; still accepted so
            // existing configs keep working.
            val metricsArray = root.optJSONArray("metrics")
                ?: root.optJSONArray("checks")
                ?: throw IllegalArgumentException(
                    if (root.has("checks")) {
                        "metrics deve essere una lista (chiave legacy: checks)"
                    } else if (root.has("metrics")) {
                        "metrics deve essere una lista"
                    } else {
                        "metrics mancante"
                    },
                )
            for (i in 0 until metricsArray.length()) {
                val item = metricsArray.getJSONObject(i)
                val id = item.optString("id").trim()
                require(idPattern.matches(id)) { "id metrica non valido: \"$id\"" }
                require(!metrics.containsKey(id)) { "metrica duplicata: $id" }
                val label = item.optString("label", id).trim().ifEmpty { id }
                val typeText = item.optString("type", CheckType.BINARY.wire)
                val type = CheckType.fromWire(typeText)
                    ?: throw IllegalArgumentException(
                        "tipo non valido per \"$id\": \"$typeText\" (atteso binary o rating)",
                    )
                val max = when {
                    item.has("max") -> {
                        require(type == CheckType.RATING) {
                            "\"max\" è ammesso solo per le metriche rating (metrica \"$id\")"
                        }
                        val parsed = item.optInt("max", -1)
                        require(parsed in CheckDef.MIN_RATING_MAX..CheckDef.MAX_RATING_MAX) {
                            "max non valido per \"$id\": $parsed " +
                                "(atteso ${CheckDef.MIN_RATING_MAX}..${CheckDef.MAX_RATING_MAX})"
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
            require(metrics.isNotEmpty()) { "metrics vuoto" }

            val plans = linkedMapOf<String, PlanDef>()
            val plansObject = root.optJSONObject("plans")
                ?: throw IllegalArgumentException(
                    if (root.has("plans")) "plans deve essere un oggetto" else "plans mancante",
                )
            for (planId in plansObject.keys()) {
                require(idPattern.matches(planId)) { "plan id non valido: \"$planId\"" }
                val planObject = plansObject.getJSONObject(planId)
                val label = planObject.optString("label", planId).trim().ifEmpty { planId }
                val itemsArray = planObject.optJSONArray("items") ?: org.json.JSONArray()
                val items = linkedSetOf<String>()
                for (i in 0 until itemsArray.length()) {
                    val checkId = itemsArray.getString(i)
                    require(metrics.containsKey(checkId)) {
                        "il piano \"$planId\" usa la metrica sconosciuta \"$checkId\""
                    }
                    items += checkId
                }
                plans[planId] = PlanDef(planId, label, items.toList())
            }
            require(plans.isNotEmpty()) { "plans vuoto" }

            val scheduleObject = root.optJSONObject("schedule")
            val byWeekday = linkedMapOf<DayOfWeek, String>()
            val overrides = linkedMapOf<LocalDate, DayOverride>()

            val weekdayObject = scheduleObject?.optJSONObject("byWeekday")
            if (weekdayObject != null) {
                for (key in weekdayObject.keys()) {
                    val day = weekdayKeys[key]
                        ?: throw IllegalArgumentException("giorno non valido in schedule.byWeekday: \"$key\"")
                    val planId = weekdayObject.getString(key)
                    require(plans.containsKey(planId)) {
                        "schedule.byWeekday.$key usa il piano sconosciuto \"$planId\""
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
                        throw IllegalArgumentException("data override non valida: \"$dateText\"")
                    }
                    val value = overridesObject.getJSONObject(dateText)
                    when {
                        value.has("plan") -> {
                            val planId = value.getString("plan")
                            require(plans.containsKey(planId)) {
                                "override $dateText usa il piano sconosciuto \"$planId\""
                            }
                            overrides[date] = DayOverride.UsePlan(planId)
                        }
                        else -> {
                            val add = value.optJSONArray("add").stringList()
                            val remove = value.optJSONArray("remove").stringList()
                            add.forEach {
                                require(metrics.containsKey(it)) {
                                    "override $dateText aggiunge la metrica sconosciuta \"$it\""
                                }
                            }
                            remove.forEach {
                                require(metrics.containsKey(it)) {
                                    "override $dateText rimuove la metrica sconosciuta \"$it\""
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
