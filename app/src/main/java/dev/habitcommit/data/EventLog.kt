package dev.habitcommit.data

import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CheckEvent(
    val id: String,
    val checkId: String,
    val localDate: LocalDate,
    val value: Int,
    val occurredAt: Instant,
    val zoneId: String,
)

/**
 * Append-only event log. One file per install and month, so a second device can never
 * rewrite the same file and the git diff of a check-in stays a single line.
 */
class EventLog(private val journalRoot: File) {

    private val lock = Any()

    fun append(event: CheckEvent) {
        val file = fileFor(event.localDate)
        val line = event.toJsonLine(device = "local")
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
        }
    }

    fun readAll(): List<CheckEvent> {
        synchronized(lock) {
            if (!journalRoot.exists()) return emptyList()
            return journalRoot.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".jsonl") }
                .flatMap { file ->
                    file.useLines { lines ->
                        lines.mapNotNull { parseLine(it) }.toList()
                    }.asSequence()
                }
                .toList()
        }
    }

    fun hasAnyEvents(): Boolean {
        synchronized(lock) {
            if (!journalRoot.exists()) return false
            return journalRoot.walkTopDown().any { file ->
                file.isFile && file.name.endsWith(".jsonl") && file.length() > 0
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            if (journalRoot.exists()) journalRoot.deleteRecursively()
            journalRoot.mkdirs()
        }
    }

    private fun parseLine(line: String): CheckEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val json = JSONObject(trimmed)
            if (json.optInt("v", 1) != 1) return null
            if (json.optString("type", "checkin") != "checkin") return null
            // Binary metrics store 0/1, rating metrics store 0..max; clamp here
            // against the event format only, the repository clamps against the
            // check definition.
            val value = json.optInt("value", 1).coerceIn(0, CheckDef.MAX_RATING_MAX)
            CheckEvent(
                id = json.getString("id"),
                checkId = json.getString("check"),
                localDate = LocalDate.parse(json.getString("date")),
                value = value,
                occurredAt = Instant.parse(json.getString("at")),
                zoneId = json.optString("tz", ""),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun fileFor(date: LocalDate): File {
        val month = "%04d-%02d".format(date.year, date.monthValue)
        return File(File(journalRoot, month), "local.jsonl")
    }
}

/**
 * One journal line. The local log uses "local" as device; the GitHub copy uses
 * this install's random id so two phones writing the same month never touch the
 * same file.
 */
fun CheckEvent.toJsonLine(device: String): String = JSONObject().apply {
    put("v", 1)
    put("type", "checkin")
    put("id", id)
    put("check", checkId)
    put("date", localDate.toString())
    put("value", value)
    put("at", DateTimeFormatter.ISO_INSTANT.format(occurredAt))
    put("tz", zoneId)
    put("device", device)
}.toString()
