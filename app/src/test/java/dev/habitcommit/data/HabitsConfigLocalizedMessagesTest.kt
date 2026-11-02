package dev.habitcommit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that every validation failure goes through the injected
 * [ConfigMessages] instead of the English fallback.
 */
class HabitsConfigLocalizedMessagesTest {

    /**
     * Only the methods used by these tests are overridden; everything else
     * keeps the English fallback via interface delegation.
     */
    private object FakeMessages : ConfigMessages by EnglishConfigMessages {
        override fun invalidJson(detail: String) = "XX json <<$detail>>"
        override fun unsupportedSchema(found: Int, expected: Int) = "XX schema $found/$expected"
        override fun missingTimezone() = "XX timezone mancante"
        override fun metricsMissing() = "XX metrics mancanti"
        override fun invalidMetricType(id: String, type: String) = "XX type $id=$type"
        override fun maxOnlyForRating(id: String) = "XX max solo rating $id"
        override fun invalidMax(id: String, value: Int, min: Int, max: Int) = "XX max $id=$value [$min..$max]"
        override fun unknownPlanInWeekday(key: String, planId: String) = "XX weekday $key=$planId"
        override fun unknownMetricAdded(date: String, metricId: String) = "XX add $date=$metricId"
        override fun unknownMetricRemoved(date: String, metricId: String) = "XX remove $date=$metricId"
    }

    private fun parseError(json: String): String {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HabitsConfig.parse(json, FakeMessages)
        }
        return error.message.orEmpty()
    }

    private val validMetrics = """[{"id": "read"}]"""
    private val validPlans = """{"p": {"items": ["read"]}}"""

    @Test
    fun `malformed json uses the localized message with the detail`() {
        val message = parseError("{ not json")
        assertTrue("Messaggio: $message", message.startsWith("XX json <<"))
        assertFalse(message.contains("Invalid JSON"))
    }

    @Test
    fun `schema error includes found and expected versions`() {
        val message = parseError(
            """{"schema": 2, "timezone": "Europe/Rome", "metrics": $validMetrics, "plans": $validPlans}""",
        )
        assertEquals("XX schema 2/1", message)
    }

    @Test
    fun `missing timezone uses the localized message`() {
        assertEquals(
            "XX timezone mancante",
            parseError(
                """{"schema": 1, "metrics": $validMetrics, "plans": $validPlans}""",
            ),
        )
    }

    @Test
    fun `missing metrics uses the localized message`() {
        assertEquals(
            "XX metrics mancanti",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "plans": $validPlans}""",
            ),
        )
    }

    @Test
    fun `unknown metric type reports the metric id and the raw type`() {
        assertEquals(
            "XX type read=scale",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read", "type": "scale"}], "plans": $validPlans}""",
            ),
        )
    }

    @Test
    fun `max on a binary metric uses the max-only-for-rating message`() {
        assertEquals(
            "XX max solo rating read",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "read", "max": 5}], "plans": $validPlans}""",
            ),
        )
    }

    @Test
    fun `out of range rating max reports id value and bounds`() {
        assertEquals(
            "XX max focus=1 [2..10]",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "focus", "type": "rating", "max": 1}], "plans": {"p": {"items": ["focus"]}}}""",
            ),
        )
        assertEquals(
            "XX max focus=11 [2..10]",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": [{"id": "focus", "type": "rating", "max": 11}], "plans": {"p": {"items": ["focus"]}}}""",
            ),
        )
    }

    @Test
    fun `unknown plan in a weekday uses the localized message`() {
        assertEquals(
            "XX weekday mon=missing",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": $validMetrics, "plans": $validPlans, "schedule": {"byWeekday": {"mon": "missing"}}}""",
            ),
        )
    }

    @Test
    fun `override adding and removing unknown metrics use distinct localized messages`() {
        assertEquals(
            "XX add 2026-11-04=gym",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": $validMetrics, "plans": $validPlans, "schedule": {"overrides": {"2026-11-04": {"add": ["gym"]}}}}""",
            ),
        )
        assertEquals(
            "XX remove 2026-11-04=gym",
            parseError(
                """{"schema": 1, "timezone": "Europe/Rome", "metrics": $validMetrics, "plans": $validPlans, "schedule": {"overrides": {"2026-11-04": {"remove": ["gym"]}}}}""",
            ),
        )
    }

    @Test
    fun `default messages remain the English fallback`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HabitsConfig.parse("{ not json")
        }
        assertTrue(error.message.orEmpty().startsWith("Invalid JSON"))
    }
}
