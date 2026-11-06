package dev.habitcommit.data

import android.content.Context
import dev.habitcommit.HabitCommitApp
import dev.habitcommit.R
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class CheckUi(
    val def: CheckDef,
    /** 0 = untouched, 1 = done for binary metrics, 1..max for rating metrics. */
    val value: Int,
    val timeLabel: String?,
) {
    val done: Boolean get() = value > 0
}

data class HistoryMetric(
    val def: CheckDef,
    /** One entry per day in [HistoryState.days]; null when the metric was not in that day's plan. */
    val values: List<Int?>,
    val activeDays: Int,
) {
    /** Mean of the recorded values above zero; null for binary metrics or when never recorded. */
    val average: Double?
        get() = if (def.type != CheckType.RATING) {
            null
        } else {
            values.mapNotNull { it?.takeIf { value -> value > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()
        }
}

data class HistoryDay(
    val date: LocalDate,
    val planLabel: String,
    val done: Int,
    val total: Int,
)

data class HistoryState(
    val days: List<LocalDate>,
    val metrics: List<HistoryMetric>,
    val recentDays: List<HistoryDay>,
    val zone: ZoneId,
)

data class TodayState(
    val date: LocalDate,
    val plan: PlanDef,
    val metrics: List<CheckUi>,
    val doneCount: Int,
    val total: Int,
    val streak: Int,
    val heat: List<Int>,
    val tomorrowPlan: PlanDef?,
    val tomorrowPreview: String,
    val demoMode: Boolean,
    val configError: String?,
    val zone: ZoneId,
) {
    val zoneId: String get() = zone.id
    val progress: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
}

class HabitRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val configFile = File(context.filesDir, "habits.json")
    private val eventLog = EventLog(File(context.filesDir, "journal"))
    private val configMessages = ResourceConfigMessages(context.resources)

    private val configLock = Any()

    @Volatile
    private var cachedConfig: HabitsConfig? = null

    /**
     * Immutable snapshot of the journal for the current process. A widget tap
     * never re-parses every month on the main thread; [setValue] appends to the
     * snapshot so subsequent refreshes stay cheap.
     */
    @Volatile
    private var eventsCache: List<CheckEvent>? = null

    private fun events(): List<CheckEvent> =
        eventsCache ?: eventLog.readAll().also { eventsCache = it }

    @Volatile
    var configError: String? = null
        private set

    /** Sample config in the device language, falling back to English. */
    private fun sampleAssetName(): String = when (Locale.getDefault().language) {
        "it" -> "sample_habits_it.json"
        "ro" -> "sample_habits_ro.json"
        else -> "sample_habits.json"
    }

    private fun readSample(): String =
        context.assets.open(sampleAssetName()).bufferedReader().use { it.readText() }

    fun ensureSeeded() {
        synchronized(configLock) {
            if (configFile.exists()) return
            val sample = readSample()
            configFile.writeAtomically(sample)
            cachedConfig = null

            if (!eventLog.hasAnyEvents()) {
                DemoSeeder.seed(this)
                prefs.edit().putBoolean(KEY_DEMO_MODE, true).apply()
            }
        }
    }

    fun config(): HabitsConfig {
        cachedConfig?.let { return it }
        synchronized(configLock) {
            cachedConfig?.let { return it }
            if (!configFile.exists()) {
                configFile.writeAtomically(readSample())
            }
            val text = configFile.readText()
            return try {
                HabitsConfig.parse(text, configMessages).also {
                    cachedConfig = it
                    configError = null
                }
            } catch (e: Exception) {
                configError = e.message ?: context.getString(R.string.invalid_configuration)
                HabitsConfig.parse(readSample(), configMessages)
            }
        }
    }

    fun configText(): String = synchronized(configLock) {
        if (configFile.exists()) configFile.readText() else ""
    }

    fun saveConfig(text: String): Result<Unit> {
        return try {
            val parsed = HabitsConfig.parse(text, configMessages)
            synchronized(configLock) {
                configFile.writeAtomically(text)
                cachedConfig = parsed
                configError = null
            }
            prefs.edit().putBoolean(KEY_DEMO_MODE, false).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(
                    e.message ?: context.getString(R.string.invalid_configuration),
                ),
            )
        }
    }

    fun today(date: LocalDate? = null): TodayState {
        val config = config()
        val zone = config.zone
        val day = date ?: LocalDate.now(zone)
        val resolved = PlanResolver.resolve(config, day)
            ?: error(context.getString(R.string.error_no_plan))

        val latest = latestEvents(events())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

        val metrics = resolved.metrics.map { check ->
            val event = latest[day to check.id]
            val value = (event?.value ?: 0).coerceIn(0, check.maxValue)
            CheckUi(
                def = check,
                value = value,
                // An untouched check shows no time: the timestamp marks the last
                // value it was given.
                timeLabel = if (value > 0) event?.occurredAt?.let(timeFormatter::format) else null,
            )
        }

        val tomorrow = PlanResolver.resolve(config, day.plusDays(1), null)
        val tomorrowPreview = tomorrow?.metrics
            ?.take(3)
            ?.joinToString(" · ") { it.label }
            ?: ""

        return TodayState(
            date = day,
            plan = resolved.plan,
            metrics = metrics,
            doneCount = metrics.count { it.done },
            total = metrics.size,
            streak = computeStreak(config, day, latest),
            heat = computeHeat(config, day, latest),
            tomorrowPlan = tomorrow?.plan,
            tomorrowPreview = tomorrowPreview,
            demoMode = prefs.getBoolean(KEY_DEMO_MODE, false),
            configError = configError,
            zone = zone,
        )
    }

    /** Per-metric values and per-day totals for the last [days] days, oldest first. */
    fun history(days: Int = 30, today: LocalDate? = null): HistoryState {
        val config = config()
        val zone = config.zone
        val end = today ?: LocalDate.now(zone)
        val dates = (days - 1 downTo 0).map { end.minusDays(it.toLong()) }
        val latest = latestEvents(events())
        val plans = dates.associateWith { PlanResolver.resolve(config, it) }

        val metrics = config.metrics.values.mapNotNull { def ->
            val values = dates.map { date ->
                val resolved = plans[date] ?: return@map null
                if (resolved.metrics.none { it.id == def.id }) return@map null
                (latest[date to def.id]?.value ?: 0).coerceIn(0, def.maxValue)
            }
            if (values.all { it == null }) {
                null
            } else {
                HistoryMetric(
                    def = def,
                    values = values,
                    activeDays = values.count { (it ?: 0) > 0 },
                )
            }
        }

        // The detail list stays readable: last week when the range is longer.
        val recentDays = dates.takeLast(7).map { date ->
            val resolved = plans[date]
            val planned = resolved?.metrics.orEmpty()
            HistoryDay(
                date = date,
                planLabel = resolved?.plan?.label ?: "—",
                done = planned.count { (latest[date to it.id]?.value ?: 0) > 0 },
                total = planned.size,
            )
        }

        return HistoryState(days = dates, metrics = metrics, recentDays = recentDays, zone = zone)
    }

    /**
     * Stores [value] for [checkId] on the given day, clamped to the check's
     * range (0..1 for binary metrics, 0..max for ratings). Appends a single event
     * so the journal stays an immutable log of what was true at each tap.
     */
    @Synchronized
    fun setValue(checkId: String, value: Int, date: LocalDate? = null): Int {
        val config = config()
        val def = requireNotNull(config.metrics[checkId]) { "Check sconosciuto: $checkId" }
        val day = date ?: LocalDate.now(config.zone)
        val stored = value.coerceIn(0, def.maxValue)
        val event = CheckEvent(
            id = UUID.randomUUID().toString(),
            checkId = checkId,
            localDate = day,
            value = stored,
            occurredAt = Instant.now(),
            zoneId = config.zone.id,
        )
        eventLog.append(event)
        eventsCache = eventsCache?.plus(event)
        // Queue for GitHub but never block the tap on the network.
        (context.applicationContext as? HabitCommitApp)?.recordSyncEvent(event)
        return stored
    }

    /** Binary toggle used by whole-row taps: on when untouched or cleared, off when done. */
    @Synchronized
    fun toggle(checkId: String, date: LocalDate? = null): Boolean {
        val config = config()
        require(config.metrics.containsKey(checkId)) { "Check sconosciuto: $checkId" }
        val day = date ?: LocalDate.now(config.zone)
        val latest = latestEvents(events())[day to checkId]
        val newValue = if ((latest?.value ?: 0) > 0) 0 else 1
        setValue(checkId, newValue, day)
        return newValue == 1
    }

    fun clearEvents() {
        eventLog.clear()
        eventsCache = emptyList()
        // Cleared values must not be re-uploaded on the next sync.
        (context.applicationContext as? HabitCommitApp)?.clearSyncOutbox()
        prefs.edit().putBoolean(KEY_DEMO_MODE, false).apply()
    }

    /** Replaces the local journal with a fresh demo sample (never appends to it). */
    fun seedDemo() {
        eventLog.clear()
        eventsCache = emptyList()
        (context.applicationContext as? HabitCommitApp)?.clearSyncOutbox()
        DemoSeeder.seed(this)
        prefs.edit().putBoolean(KEY_DEMO_MODE, true).apply()
    }

    internal fun resolvedDay(date: LocalDate): ResolvedDay? =
        PlanResolver.resolve(config(), date)

    internal fun appendDemoEvent(
        checkId: String,
        date: LocalDate,
        hour: Int,
        minute: Int,
        value: Int = 1,
    ) {
        val config = config()
        val instant = date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(config.zone)
            .toInstant()
        val event = CheckEvent(
            id = UUID.randomUUID().toString(),
            checkId = checkId,
            localDate = date,
            value = value,
            occurredAt = instant,
            zoneId = config.zone.id,
        )
        eventLog.append(event)
        eventsCache = eventsCache?.plus(event)
    }

    private fun latestEvents(events: List<CheckEvent>): Map<Pair<LocalDate, String>, CheckEvent> {
        return events
            .groupBy { it.localDate to it.checkId }
            .mapValues { (_, values) ->
                values.maxWithOrNull(
                    compareBy<CheckEvent> { it.occurredAt }.thenBy { it.id },
                )!!
            }
    }

    /**
     * A day counts only for the metrics in that day's plan and only with their
     * latest value, so a metric that was checked and later unchecked does not
     * keep the streak alive.
     */
    private fun computeStreak(
        config: HabitsConfig,
        today: LocalDate,
        latest: Map<Pair<LocalDate, String>, CheckEvent>,
    ): Int = Streak.count(today) { date ->
        val planned = PlanResolver.resolve(config, date)?.metrics.orEmpty()
        planned.any { (latest[date to it.id]?.value ?: 0) > 0 }
    }

    private fun computeHeat(
        config: HabitsConfig,
        today: LocalDate,
        latest: Map<Pair<LocalDate, String>, CheckEvent>,
    ): List<Int> {
        return (13 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val resolved = PlanResolver.resolve(config, date) ?: return@map 0
            if (resolved.metrics.isEmpty()) return@map 0
            // Any value above zero counts as done; the star level is kept in the
            // journal for future analytics but does not skew the heatmap.
            val done = resolved.metrics.count { (latest[date to it.id]?.value ?: 0) > 0 }
            when {
                done == 0 -> 0
                done == resolved.metrics.size -> 4
                done * 4 >= resolved.metrics.size * 3 -> 3
                done * 2 >= resolved.metrics.size -> 2
                else -> 1
            }
        }
    }

    companion object {
        private const val PREFS = "habitcommit"
        private const val KEY_DEMO_MODE = "demo_mode"
    }
}
