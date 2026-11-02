package dev.habitcommit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitsConfigTest {

    private fun assertInvalid(json: String, messagePart: String) {
        try {
            HabitsConfig.parse(json)
            fail("Atteso errore di validazione")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Atteso \"$messagePart\" in \"${e.message}\"",
                e.message.orEmpty().contains(messagePart),
            )
        }
    }

    @Test
    fun `parses metrics plans and overrides`() {
        val config = HabitsConfig.parse(
            """
            {
              "schema": 1,
              "timezone": "Europe/Rome",
              "metrics": [
                {"id": "read", "label": "Leggi"},
                {"id": "gym", "type": "binary"}
              ],
              "plans": {"base": {"label": "Base", "items": ["read", "gym", "read"]}},
              "schedule": {
                "byWeekday": {"mon": "base"},
                "overrides": {
                  "2026-11-03": {"plan": "base"},
                  "2026-11-04": {"add": ["gym"], "remove": ["read"]}
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Europe/Rome", config.timezone)
        assertEquals("Leggi", config.metrics.getValue("read").label)
        assertEquals("gym", config.metrics.getValue("gym").label)
        assertEquals(listOf("read", "gym"), config.plans.getValue("base").items)
        assertEquals("base", config.schedule.byWeekday[DayOfWeek.MONDAY])
        assertTrue(config.schedule.overrides[LocalDate.parse("2026-11-03")] is DayOverride.UsePlan)
        val modify = config.schedule.overrides[LocalDate.parse("2026-11-04")]
        assertEquals(DayOverride.Modify(add = listOf("gym"), remove = listOf("read")), modify)
    }

    @Test
    fun `parses rating metrics with default and custom max`() {
        val config = HabitsConfig.parse(
            """
            {
              "schema": 1,
              "timezone": "Europe/Rome",
              "metrics": [
                {"id": "focus", "label": "Focus", "type": "rating"},
                {"id": "mood", "label": "Umore", "type": "rating", "max": 7},
                {"id": "read", "label": "Leggi"}
              ],
              "plans": {"base": {"label": "Base", "items": ["focus", "mood", "read"]}}
            }
            """.trimIndent(),
        )

        val focus = config.metrics.getValue("focus")
        assertEquals(CheckType.RATING, focus.type)
        assertEquals(CheckDef.DEFAULT_RATING_MAX, focus.max)
        assertEquals(5, focus.maxValue)
        assertEquals(CheckType.RATING, config.metrics.getValue("mood").type)
        assertEquals(7, config.metrics.getValue("mood").maxValue)
        val read = config.metrics.getValue("read")
        assertEquals(CheckType.BINARY, read.type)
        assertEquals(1, read.maxValue)
    }

    @Test
    fun `accepts the legacy checks key`() {
        val config = HabitsConfig.parse(
            """{"schema": 1, "timezone": "Europe/Rome", "checks": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}}""",
        )
        assertEquals(1, config.metrics.size)
        assertEquals(CheckType.BINARY, config.metrics.getValue("read").type)
    }

    @Test
    fun `rejects unknown check type`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read", "type": "scale"}], "plans": {"p": {"items": ["read"]}}}""",
            "Invalid type",
        )
    }

    @Test
    fun `rejects rating max out of range`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "focus", "type": "rating", "max": 1}], "plans": {"p": {"items": ["focus"]}}}""",
            "Invalid max",
        )
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "focus", "type": "rating", "max": 11}], "plans": {"p": {"items": ["focus"]}}}""",
            "Invalid max",
        )
    }

    @Test
    fun `rejects max on a binary check`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read", "max": 5}], "plans": {"p": {"items": ["read"]}}}""",
            "only allowed on rating metrics",
        )
    }

    @Test
    fun `malformed json reports a friendly error`() {
        assertInvalid("{ not json", "Invalid JSON")
    }

    @Test
    fun `wrong field type reports a friendly error`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": "read", "plans": {"p": {"items": []}}}""",
            "metrics must be a list",
        )
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": []}""",
            "plans must be an object",
        )
    }

    @Test
    fun `rejects unsupported schema`() {
        assertInvalid(
            """{"schema": 2, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}}""",
            "Unsupported schema",
        )
    }

    @Test
    fun `rejects invalid timezone`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Mars/Olympus", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}}""",
            "Invalid timezone",
        )
    }

    @Test
    fun `rejects invalid check id`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "Review PR"}], "plans": {"p": {"items": []}}}""",
            "Invalid metric id",
        )
    }

    @Test
    fun `rejects duplicate check ids`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}, {"id": "read"}], "plans": {"p": {"items": []}}}""",
            "Duplicate metric",
        )
    }

    @Test
    fun `rejects plan referencing unknown check`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["gym"]}}}""",
            "unknown metric \"gym\"",
        )
    }

    @Test
    fun `rejects empty plans`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {}}""",
            "No plans defined",
        )
    }

    @Test
    fun `rejects unknown plan in weekday schedule`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}, "schedule": {"byWeekday": {"mon": "missing"}}}""",
            "unknown plan",
        )
    }

    @Test
    fun `rejects invalid weekday key`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}, "schedule": {"byWeekday": {"monday": "p"}}}""",
            "Invalid day",
        )
    }

    @Test
    fun `rejects invalid override date`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}, "schedule": {"overrides": {"next monday": {"plan": "p"}}}}""",
            "Invalid override date",
        )
    }

    @Test
    fun `rejects override adding unknown check`() {
        assertInvalid(
            """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read"}], "plans": {"p": {"items": ["read"]}}, "schedule": {"overrides": {"2026-11-04": {"add": ["gym"]}}}}""",
            "unknown metric \"gym\"",
        )
    }
}
