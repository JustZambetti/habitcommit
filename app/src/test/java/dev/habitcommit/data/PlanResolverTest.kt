package dev.habitcommit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanResolverTest {

    private val config = HabitsConfig.parse(
        """
        {
          "schema": 1,
          "timezone": "Europe/Rome",
          "metrics": [
            {"id": "review-pr", "label": "Review PR"},
            {"id": "deep-work", "label": "Deep work 2h"},
            {"id": "client-call", "label": "Chiama cliente"},
            {"id": "gym", "label": "Allenamento"}
          ],
          "plans": {
            "office": {"label": "Ufficio", "items": ["review-pr", "deep-work", "client-call"]},
            "weekend": {"label": "Weekend", "items": ["gym"]},
            "travel": {"label": "Viaggio", "items": ["review-pr"]}
          },
          "schedule": {
            "byWeekday": {
              "mon": "office", "tue": "office", "wed": "office",
              "thu": "office", "fri": "office", "sat": "weekend", "sun": "weekend"
            },
            "overrides": {
              "2026-11-03": {"plan": "travel"},
              "2026-11-10": {"add": ["gym"], "remove": ["client-call"]}
            }
          }
        }
        """.trimIndent(),
    )

    private fun resolve(date: String, local: String? = null): ResolvedDay =
        requireNotNull(PlanResolver.resolve(config, LocalDate.parse(date), local))

    @Test
    fun `weekday schedule picks the matching plan`() {
        val monday = resolve("2026-10-26")
        assertEquals("office", monday.plan.id)
        assertEquals(PlanSource.WEEKDAY, monday.source)
        assertEquals(listOf("review-pr", "deep-work", "client-call"), monday.metrics.map { it.id })
        assertFalse(monday.modified)

        val saturday = resolve("2026-10-31")
        assertEquals("weekend", saturday.plan.id)
        assertEquals(listOf("gym"), saturday.metrics.map { it.id })
    }

    @Test
    fun `date override with plan wins over weekday`() {
        val day = resolve("2026-11-03")
        assertEquals("travel", day.plan.id)
        assertEquals(PlanSource.DATE_OVERRIDE, day.source)
        assertEquals(listOf("review-pr"), day.metrics.map { it.id })
    }

    @Test
    fun `date override with add and remove modifies the scheduled plan`() {
        val day = resolve("2026-11-10")
        assertEquals("office", day.plan.id)
        assertEquals(PlanSource.WEEKDAY, day.source)
        assertTrue(day.modified)
        assertEquals(listOf("review-pr", "deep-work", "gym"), day.metrics.map { it.id })
    }

    @Test
    fun `local override wins over date override`() {
        val day = resolve("2026-11-03", local = "weekend")
        assertEquals("weekend", day.plan.id)
        assertEquals(PlanSource.LOCAL_OVERRIDE, day.source)
    }

    @Test
    fun `unknown local override falls back to the schedule`() {
        val day = resolve("2026-11-03", local = "does-not-exist")
        assertEquals("travel", day.plan.id)
        assertEquals(PlanSource.DATE_OVERRIDE, day.source)
    }

    @Test
    fun `no schedule uses the first plan as default`() {
        val noSchedule = HabitsConfig.parse(
            """
            {
              "schema": 1,
              "timezone": "Europe/Rome",
              "metrics": [{"id": "read", "label": "Leggi"}],
              "plans": {"base": {"label": "Base", "items": ["read"]}}
            }
            """.trimIndent(),
        )
        val day = requireNotNull(PlanResolver.resolve(noSchedule, LocalDate.parse("2026-10-26")))
        assertEquals("base", day.plan.id)
        assertEquals(PlanSource.DEFAULT, day.source)
    }

    @Test
    fun `dst boundary still resolves the right weekday`() {
        // 25 October 2026 is the DST change in Europe/Rome (Sunday).
        val day = resolve("2026-10-25")
        assertEquals("weekend", day.plan.id)
    }

    @Test
    fun `empty plan yields no metrics`() {
        val empty = HabitsConfig.parse(
            """
            {
              "schema": 1,
              "timezone": "Europe/Rome",
              "metrics": [{"id": "read", "label": "Leggi"}],
              "plans": {"nothing": {"label": "Libero", "items": []}}
            }
            """.trimIndent(),
        )
        val day = requireNotNull(PlanResolver.resolve(empty, LocalDate.parse("2026-10-26")))
        assertTrue(day.metrics.isEmpty())
    }

    /** Minimal config with an optional schedule used by the edge-case tests below. */
    private fun customConfig(schedule: String = ""): HabitsConfig = HabitsConfig.parse(
        """
        {
          "schema": 1,
          "timezone": "Europe/Rome",
          "metrics": [
            {"id": "read", "label": "Leggi"},
            {"id": "gym", "label": "Palestra"},
            {"id": "focus", "label": "Focus", "type": "rating", "max": 7},
            {"id": "plain-rating", "label": "Default", "type": "rating"}
          ],
          "plans": {
            "base": {"label": "Base", "items": ["read"]},
            "full": {"label": "Full", "items": ["read", "gym"]}
          }
          ${if (schedule.isBlank()) "" else ", \"schedule\": $schedule"}
        }
        """.trimIndent(),
    )

    private fun resolveCustom(config: HabitsConfig, date: String, local: String? = null): ResolvedDay =
        requireNotNull(PlanResolver.resolve(config, LocalDate.parse(date), local))

    @Test
    fun `missing weekday falls back to the first plan`() {
        val config = customConfig("""{"byWeekday": {"mon": "full"}}""")
        // 2026-11-03 is a Tuesday, not scheduled.
        val day = resolveCustom(config, "2026-11-03")
        assertEquals("base", day.plan.id)
        assertEquals(PlanSource.DEFAULT, day.source)
        assertFalse(day.modified)
    }

    @Test
    fun `modify override builds on top of the default plan when the weekday is unscheduled`() {
        val config = customConfig(
            """{"byWeekday": {"mon": "full"}, "overrides": {"2026-11-03": {"add": ["gym"]}}}""",
        )
        val day = resolveCustom(config, "2026-11-03")
        assertEquals("base", day.plan.id)
        assertEquals(PlanSource.DEFAULT, day.source)
        assertTrue(day.modified)
        assertEquals(listOf("read", "gym"), day.metrics.map { it.id })
    }

    @Test
    fun `adding a metric already in the plan and removing a missing one is not a modification`() {
        val config = customConfig(
            """
            {"byWeekday": {"mon": "base"}, "overrides": {
              "2026-11-02": {"add": ["read"], "remove": ["gym"]}
            }}
            """.trimIndent(),
        )
        val day = resolveCustom(config, "2026-11-02")
        assertEquals("base", day.plan.id)
        assertFalse(day.modified)
        assertEquals(listOf("read"), day.metrics.map { it.id })
    }

    @Test
    fun `removing then re-adding the same metric keeps it once and marks the day modified`() {
        val config = customConfig(
            """
            {"byWeekday": {"mon": "full"}, "overrides": {
              "2026-11-02": {"remove": ["read"], "add": ["read"]}
            }}
            """.trimIndent(),
        )
        val day = resolveCustom(config, "2026-11-02")
        assertTrue(day.modified)
        assertEquals(listOf("gym", "read"), day.metrics.map { it.id })
    }

    @Test
    fun `only the metrics of the base plan are removed by a modify override`() {
        val config = customConfig(
            """
            {"byWeekday": {"mon": "full"}, "overrides": {
              "2026-11-02": {"remove": ["read"]}
            }}
            """.trimIndent(),
        )
        val day = resolveCustom(config, "2026-11-02")
        assertTrue(day.modified)
        assertEquals(listOf("gym"), day.metrics.map { it.id })
    }

    @Test
    fun `date modify does not duplicate a metric already present in the local override plan`() {
        val config = customConfig(
            """
            {"byWeekday": {"mon": "base"}, "overrides": {
              "2026-11-02": {"add": ["gym"]}
            }}
            """.trimIndent(),
        )
        val day = resolveCustom(config, "2026-11-02", local = "full")
        assertEquals("full", day.plan.id)
        assertEquals(PlanSource.LOCAL_OVERRIDE, day.source)
        assertFalse(day.modified)
        assertEquals(listOf("read", "gym"), day.metrics.map { it.id })
    }

    @Test
    fun `resolved rating metrics keep their type and max`() {
        val withRatings = HabitsConfig.parse(
            """
            {
              "schema": 1,
              "timezone": "Europe/Rome",
              "metrics": [
                {"id": "focus", "type": "rating", "max": 7},
                {"id": "plain-rating", "type": "rating"},
                {"id": "read"}
              ],
              "plans": {"ratings": {"items": ["read", "focus", "plain-rating"]}},
              "schedule": {"byWeekday": {"mon": "ratings"}}
            }
            """.trimIndent(),
        )
        val day = requireNotNull(PlanResolver.resolve(withRatings, LocalDate.parse("2026-11-02")))
        assertEquals(listOf("read", "focus", "plain-rating"), day.metrics.map { it.id })
        assertEquals(CheckType.RATING, day.metrics[1].type)
        assertEquals(7, day.metrics[1].maxValue)
        assertEquals(CheckDef.DEFAULT_RATING_MAX, day.metrics[2].maxValue)
        assertEquals(1, day.metrics[0].maxValue)
    }

    @Test
    fun `use-plan override pointing at a missing plan falls back`() {
        // The parser rejects this, but the resolver stays defensive against
        // configs built programmatically.
        val config = HabitsConfig(
            schema = 1,
            timezone = "Europe/Rome",
            metrics = mapOf("read" to CheckDef("read", "Leggi")),
            plans = mapOf("base" to PlanDef("base", "Base", listOf("read"))),
            schedule = ScheduleDef(
                byWeekday = emptyMap(),
                overrides = mapOf(
                    LocalDate.of(2026, 11, 3) to DayOverride.UsePlan("missing"),
                ),
            ),
        )
        val day = requireNotNull(PlanResolver.resolve(config, LocalDate.of(2026, 11, 3)))
        assertEquals("base", day.plan.id)
        assertEquals(PlanSource.DEFAULT, day.source)
    }

    @Test
    fun `no plans at all resolves to null`() {
        val config = HabitsConfig(
            schema = 1,
            timezone = "Europe/Rome",
            metrics = emptyMap(),
            plans = emptyMap(),
            schedule = ScheduleDef(emptyMap(), emptyMap()),
        )
        assertEquals(null, PlanResolver.resolve(config, LocalDate.of(2026, 11, 2)))
    }
}
