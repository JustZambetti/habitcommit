package dev.habitcommit.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 11, 6)

    private fun streak(vararg doneDays: LocalDate): Int =
        Streak.count(today) { it in doneDays }

    @Test
    fun `counts consecutive days ending today`() {
        assertEquals(
            3,
            streak(today, today.minusDays(1), today.minusDays(2)),
        )
    }

    @Test
    fun `today not recorded still keeps the streak from yesterday`() {
        assertEquals(2, streak(today.minusDays(1), today.minusDays(2)))
    }

    @Test
    fun `a gap resets the streak`() {
        assertEquals(
            1,
            streak(today, today.minusDays(2), today.minusDays(3)),
        )
    }

    @Test
    fun `two missed days clear the streak`() {
        assertEquals(0, streak(today.minusDays(2), today.minusDays(3)))
    }

    @Test
    fun `no activity means no streak`() {
        assertEquals(0, streak())
    }

    @Test
    fun `a day whose values were later cleared is inactive`() {
        // The caller derives activity from the latest per-metric values, so a
        // check followed by an uncheck presents the day as inactive.
        val values = mapOf("gym" to 0)
        val active: (LocalDate) -> Boolean = { date ->
            date == today.minusDays(1) || (date == today && (values["gym"] ?: 0) > 0)
        }
        assertEquals(1, Streak.count(today, active))
    }
}
