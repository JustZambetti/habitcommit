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

    @Test
    fun foldsManyLinesIntoOneBlockLikeTheBatchAppend() {
        val lines = listOf("e-1", "e-2", "e-3").map { RemoteJournal.eventLine(event(it), "d") }
        val content = lines.fold("") { text, line -> RemoteJournal.append(text, line) }

        assertEquals(lines.joinToString("\n", postfix = "\n"), content)
        val physicalLines = content.split("\n").dropLastWhile { it.isEmpty() }
        assertEquals(3, physicalLines.size)
        physicalLines.forEach { assertTrue(RemoteJournal.containsEvent(it + "\n", JSONObject(it).getString("id"))) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLineContainingNewline() {
        RemoteJournal.append("", RemoteJournal.eventLine(event(), "d") + "\nsecond")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLineContainingCarriageReturn() {
        RemoteJournal.append("existing\n", "line\rbroken")
    }

    @Test
    fun repairsCrLfFileWithoutAddingAnExtraNewline() {
        val line = RemoteJournal.eventLine(event(), "d")
        // A CRLF file already ends with '\n', so it must not gain a blank line.
        assertEquals("old\r\n$line\n", RemoteJournal.append("old\r\n", line))
    }

    @Test
    fun dedupeMatchesTheExactIdAcrossMultipleLines() {
        val content = buildString {
            append(RemoteJournal.eventLine(event("e-1"), "d"))
            append('\n')
            append(RemoteJournal.eventLine(event("e-2"), "d"))
            append('\n')
        }
        assertTrue(RemoteJournal.containsEvent(content, "e-1"))
        assertTrue(RemoteJournal.containsEvent(content, "e-2"))
        assertFalse(RemoteJournal.containsEvent(content, "e-"))
        assertFalse(RemoteJournal.containsEvent(content, "e-3"))
    }

    @Test
    fun dedupeToleratesBlankPaddedAndCorruptLines() {
        val content =
            "\n  ${RemoteJournal.eventLine(event("e-2"), "d")}  \n{i am not json}\nlast line without newline"
        assertTrue(RemoteJournal.containsEvent(content, "e-2"))
        assertFalse(RemoteJournal.containsEvent(content, "e-1"))
        assertFalse(RemoteJournal.containsEvent("", "e-1"))
    }

    @Test
    fun parsesValidAndRejectsInvalidDates() {
        assertEquals(LocalDate.of(2026, 11, 2), RemoteJournal.parseDate("2026-11-02"))
        assertEquals(null, RemoteJournal.parseDate("2026-13-01"))
        assertEquals(null, RemoteJournal.parseDate("next monday"))
        assertEquals(null, RemoteJournal.parseDate(""))
    }

    @Test
    fun validatesCanonicalDeviceIds() {
        val uuid = "7c4f6c2e-3a1b-4d5e-8f90-123456789abc"
        assertTrue(RemoteJournal.validDeviceId(uuid))
        // Uppercase is accepted because the comparison is against the lowercase form.
        assertTrue(RemoteJournal.validDeviceId(uuid.uppercase()))
        assertFalse(RemoteJournal.validDeviceId("7c4f6c2e3a1b4d5e8f90123456789abc"))
        assertFalse(RemoteJournal.validDeviceId("1-1-1-1-1"))
        assertFalse(RemoteJournal.validDeviceId(""))
        assertFalse(RemoteJournal.validDeviceId("not-a-device"))
    }
}
