package dev.habitcommit.sync

import dev.habitcommit.data.CheckEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RemoteJournalTest {

    private fun event(id: String = "e-1") = CheckEvent(
        id = id,
        checkId = "reading",
        localDate = LocalDate.of(2026, 10, 25),
        value = 3,
        occurredAt = Instant.parse("2026-10-25T08:30:00Z"),
        zoneId = "Europe/Rome",
    )

    @Test
    fun appendsOneLineToEmptyFile() {
        val line = RemoteJournal.eventLine(event(), "device-1")
        assertEquals(line + "\n", RemoteJournal.append("", line))
    }

    @Test
    fun repairsMissingTrailingNewline() {
        val line = RemoteJournal.eventLine(event(), "device-1")
        assertEquals("old\n$line\n", RemoteJournal.append("old", line))
        assertEquals("old\n$line\n", RemoteJournal.append("old\n", line))
    }

    @Test
    fun lineCarriesDeviceAndNoNotes() {
        val json = JSONObject(RemoteJournal.eventLine(event(), "device-1"))
        assertEquals("device-1", json.getString("device"))
        assertEquals("reading", json.getString("check"))
        assertEquals(3, json.getInt("value"))
        assertFalse(json.has("note"))
    }

    @Test
    fun detectsEventAlreadyUploaded() {
        val content = RemoteJournal.append("", RemoteJournal.eventLine(event("e-1"), "d"))
        assertTrue(RemoteJournal.containsEvent(content, "e-1"))
        assertFalse(RemoteJournal.containsEvent(content, "e-2"))
        assertFalse(RemoteJournal.containsEvent("not json\n", "e-1"))
    }
}
