package dev.habitcommit.sync

import dev.habitcommit.data.CheckEvent
import dev.habitcommit.data.toJsonLine
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Pure helpers for the remote append-only journal, kept testable without Android. */
object RemoteJournal {

    fun eventLine(event: CheckEvent, deviceId: String): String = event.toJsonLine(deviceId)

    /**
     * Appends [line] as exactly one new line. An empty remote file gets no
     * leading blank line, and an existing file without a trailing newline is
     * repaired before the append.
     */
    fun append(existing: String, line: String): String {
        require(line.none { it == '\n' || it == '\r' }) { "una riga journal non può contenere newline" }
        if (existing.isEmpty()) return line + "\n"
        return buildString(existing.length + line.length + 1) {
            append(existing)
            if (!existing.endsWith("\n")) append('\n')
            append(line)
            append('\n')
        }
    }

    /** Dedupe key: the event UUID, so retries after a lost response never duplicate a value. */
    fun containsEvent(existing: String, eventId: String): Boolean {
        return existing.lineSequence().any { raw ->
            val text = raw.trim()
            if (text.isEmpty()) return@any false
            try {
                JSONObject(text).optString("id") == eventId
            } catch (e: Exception) {
                false
            }
        }
    }

    fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }

    fun validDeviceId(value: String): Boolean = try {
        UUID.fromString(value).toString() == value.lowercase()
    } catch (e: Exception) {
        false
    }
}
