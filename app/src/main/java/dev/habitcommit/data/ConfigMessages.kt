package dev.habitcommit.data

import android.content.res.Resources
import dev.habitcommit.R

/**
 * User-facing validation messages for `habits.json`. The parser itself stays
 * Android-free; the app supplies a resource-backed implementation so errors
 * follow the device language.
 */
interface ConfigMessages {
    fun invalidJson(detail: String): String
    fun unsupportedSchema(found: Int, expected: Int): String
    fun missingTimezone(): String
    fun invalidTimezone(value: String): String
    fun metricsMissing(): String
    fun metricsNotList(legacyKey: Boolean): String
    fun invalidMetricId(id: String): String
    fun duplicateMetric(id: String): String
    fun invalidMetricType(id: String, type: String): String
    fun maxOnlyForRating(id: String): String
    fun invalidMax(id: String, value: Int, min: Int, max: Int): String
    fun metricsEmpty(): String
    fun plansMissing(): String
    fun plansNotObject(): String
    fun invalidPlanId(id: String): String
    fun unknownMetricInPlan(planId: String, metricId: String): String
    fun plansEmpty(): String
    fun invalidWeekday(key: String): String
    fun unknownPlanInWeekday(key: String, planId: String): String
    fun invalidOverrideDate(value: String): String
    fun unknownPlanInOverride(date: String, planId: String): String
    fun unknownMetricAdded(date: String, metricId: String): String
    fun unknownMetricRemoved(date: String, metricId: String): String
}

/** English fallback, also used by JVM tests where no Resources exist. */
object EnglishConfigMessages : ConfigMessages {
    override fun invalidJson(detail: String) = "Invalid JSON: $detail"
    override fun unsupportedSchema(found: Int, expected: Int) =
        "Unsupported schema: $found (expected $expected)"
    override fun missingTimezone() = "Missing timezone"
    override fun invalidTimezone(value: String) = "Invalid timezone: $value"
    override fun metricsMissing() = "Missing metrics"
    override fun metricsNotList(legacyKey: Boolean) =
        if (legacyKey) "metrics must be a list (legacy key: checks)" else "metrics must be a list"
    override fun invalidMetricId(id: String) = "Invalid metric id: \"$id\""
    override fun duplicateMetric(id: String) = "Duplicate metric: $id"
    override fun invalidMetricType(id: String, type: String) =
        "Invalid type for \"$id\": \"$type\" (expected binary or rating)"
    override fun maxOnlyForRating(id: String) =
        "\"max\" is only allowed on rating metrics (metric \"$id\")"
    override fun invalidMax(id: String, value: Int, min: Int, max: Int) =
        "Invalid max for \"$id\": $value (expected $min..$max)"
    override fun metricsEmpty() = "No metrics defined"
    override fun plansMissing() = "Missing plans"
    override fun plansNotObject() = "plans must be an object"
    override fun invalidPlanId(id: String) = "Invalid plan id: \"$id\""
    override fun unknownMetricInPlan(planId: String, metricId: String) =
        "Plan \"$planId\" uses unknown metric \"$metricId\""
    override fun plansEmpty() = "No plans defined"
    override fun invalidWeekday(key: String) = "Invalid day in schedule.byWeekday: \"$key\""
    override fun unknownPlanInWeekday(key: String, planId: String) =
        "schedule.byWeekday.$key uses unknown plan \"$planId\""
    override fun invalidOverrideDate(value: String) = "Invalid override date: \"$value\""
    override fun unknownPlanInOverride(date: String, planId: String) =
        "Override $date uses unknown plan \"$planId\""
    override fun unknownMetricAdded(date: String, metricId: String) =
        "Override $date adds unknown metric \"$metricId\""
    override fun unknownMetricRemoved(date: String, metricId: String) =
        "Override $date removes unknown metric \"$metricId\""
}

/** Messages resolved from the app resources, in the device language. */
class ResourceConfigMessages(private val resources: Resources) : ConfigMessages {
    override fun invalidJson(detail: String) = resources.getString(R.string.config_error_invalid_json, detail)
    override fun unsupportedSchema(found: Int, expected: Int) =
        resources.getString(R.string.config_error_schema, found, expected)
    override fun missingTimezone() = resources.getString(R.string.config_error_timezone_missing)
    override fun invalidTimezone(value: String) =
        resources.getString(R.string.config_error_timezone_invalid, value)
    override fun metricsMissing() = resources.getString(R.string.config_error_metrics_missing)
    override fun metricsNotList(legacyKey: Boolean) = resources.getString(
        if (legacyKey) R.string.config_error_metrics_not_list_legacy else R.string.config_error_metrics_not_list,
    )
    override fun invalidMetricId(id: String) =
        resources.getString(R.string.config_error_metric_id, id)
    override fun duplicateMetric(id: String) =
        resources.getString(R.string.config_error_metric_duplicate, id)
    override fun invalidMetricType(id: String, type: String) =
        resources.getString(R.string.config_error_metric_type, id, type)
    override fun maxOnlyForRating(id: String) =
        resources.getString(R.string.config_error_max_binary, id)
    override fun invalidMax(id: String, value: Int, min: Int, max: Int) =
        resources.getString(R.string.config_error_max_range, id, value, min, max)
    override fun metricsEmpty() = resources.getString(R.string.config_error_metrics_empty)
    override fun plansMissing() = resources.getString(R.string.config_error_plans_missing)
    override fun plansNotObject() = resources.getString(R.string.config_error_plans_not_object)
    override fun invalidPlanId(id: String) =
        resources.getString(R.string.config_error_plan_id, id)
    override fun unknownMetricInPlan(planId: String, metricId: String) =
        resources.getString(R.string.config_error_plan_metric, planId, metricId)
    override fun plansEmpty() = resources.getString(R.string.config_error_plans_empty)
    override fun invalidWeekday(key: String) =
        resources.getString(R.string.config_error_weekday, key)
    override fun unknownPlanInWeekday(key: String, planId: String) =
        resources.getString(R.string.config_error_weekday_plan, key, planId)
    override fun invalidOverrideDate(value: String) =
        resources.getString(R.string.config_error_override_date, value)
    override fun unknownPlanInOverride(date: String, planId: String) =
        resources.getString(R.string.config_error_override_plan, date, planId)
    override fun unknownMetricAdded(date: String, metricId: String) =
        resources.getString(R.string.config_error_override_add, date, metricId)
    override fun unknownMetricRemoved(date: String, metricId: String) =
        resources.getString(R.string.config_error_override_remove, date, metricId)
}
