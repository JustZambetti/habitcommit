package dev.habitcommit.sync

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class PendingEvent(
    val id: String,
    val localDate: LocalDate,
    /** Serialized journal line, already containing this install's device id. */
    val line: String,
)

/**
 * Durable queue of values recorded locally but not yet confirmed by GitHub.
 * Local-first: the widget never waits for the network. Each entry survives
 * process death because it is appended to a file before sync is scheduled.
 */
class SyncOutbox(context: Context) {

    private val file = File(File(context.filesDir, "sync"), "outbox.jsonl")
    private val lock = Any()

    fun count(): Int = synchronized(lock) { readLocked().size }

    fun pending(): List<PendingEvent> = synchronized(lock) { readLocked() }

    fun add(id: String, date: LocalDate, line: String) {
        synchronized(lock) {
            if (readLocked().any { it.id == id }) return
            val item = JSONObject().apply {
                put("id", id)
                put("date", date.toString())
                put("line", line)
            }
            file.parentFile?.mkdirs()
            file.appendText(item.toString() + "\n")
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            val remaining = readLocked().filterNot { it.id == id }
            writeLocked(remaining)
        }
    }

    fun clear() {
        synchronized(lock) {
            file.delete()
        }
    }

    private fun readLocked(): List<PendingEvent> {
        if (!file.isFile) return emptyList()
        val seen = HashSet<String>()
        return file.useLines { lines ->
            lines.mapNotNull { raw ->
                val text = raw.trim()
                if (text.isEmpty()) return@mapNotNull null
                try {
                    val json = JSONObject(text)
                    val id = json.getString("id")
                    val date = RemoteJournal.parseDate(json.getString("date")) ?: return@mapNotNull null
                    val line = json.getString("line")
                    if (!seen.add(id)) null else PendingEvent(id, date, line)
                } catch (e: Exception) {
                    null
                }
            }.toList()
        }
    }

    private fun writeLocked(items: List<PendingEvent>) {
        file.parentFile?.mkdirs()
        if (items.isEmpty()) {
            file.delete()
            return
        }
        file.writeText(
            items.joinToString(separator = "") { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("date", item.localDate.toString())
                    put("line", item.line)
                }.toString() + "\n"
            },
        )
    }
}
