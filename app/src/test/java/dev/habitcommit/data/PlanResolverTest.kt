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
}
