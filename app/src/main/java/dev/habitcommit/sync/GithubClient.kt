package dev.habitcommit.sync

import android.content.res.Resources
import android.util.Base64
import dev.habitcommit.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A repository visible to the PAT, as returned by GET /user/repos. */
data class RepoSummary(
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
)

/**
 * Minimal client for the GitHub Contents and repository APIs.
 *
 * The host is hardcoded: the PAT can never be sent to another server. Pending
 * values are batched into one commit per per-install, per-month JSONL file on
 * each sync run, so two devices never rewrite the same file.
 */
class GithubClient(
    private val resources: Resources,
    private val token: String,
    private val target: GithubTarget,
) {

    data class RepoInfo(
        val fullName: String,
        val isPrivate: Boolean,
        val defaultBranch: String,
        val canPush: Boolean,
    )

    private data class RemoteFile(val text: String, val sha: String)

    private data class Response(val code: Int, val body: String)

    fun verify(): Result<RepoInfo> = try {
        val response = request("GET", "/repos/${segments(target.repoFullName)}")
        when (response.code) {
            200 -> {
                val json = JSONObject(response.body)
                val canPush = json.optJSONObject("permissions")?.optBoolean("push", false) ?: false
                Result.success(
                    RepoInfo(
                        fullName = json.optString("full_name", target.repoFullName),
                        isPrivate = json.optBoolean("private", false),
                        defaultBranch = json.optString("default_branch", target.branch),
                        canPush = canPush,
                    ),
                )
            }
            else -> Result.failure(GithubException(errorFor(response), retryable = response.code >= 500))
        }
    } catch (e: Exception) {
        Result.failure(GithubException(networkError(e), retryable = true))
    }

    /**
     * Appends [events] once, grouped by journal file so a sync run leaves at
     * most one commit per month file. Retries read-modify-write on a concurrent
     * update.
     */
    fun appendEvents(events: List<PendingEvent>, deviceId: String): Result<Unit> {
        val byPath = events.groupBy { target.journalPath(it.localDate, deviceId) }
        for ((path, group) in byPath) {
            val result = appendBatch(path, group)
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    private fun appendBatch(path: String, events: List<PendingEvent>): Result<Unit> {
        val dates = events.map { it.localDate }.distinct().sorted()
        val range = if (dates.size == 1) {
            dates.first().toString()
        } else {
            "${dates.first()}..${dates.last()}"
        }
        // Notes are never part of a commit message: only the date range and size.
        val message = "habitcommit: journal $range (${events.size} entries)"

        var attempt = 0
        while (attempt < MAX_CONFLICT_RETRIES) {
            attempt++
            val existing = when (val read = getFile(path)) {
                is ReadResult.Ok -> read.file
                is ReadResult.Failed -> return Result.failure(read.error)
            }
            val missing = events.filterNot { event ->
                existing != null && RemoteJournal.containsEvent(existing.text, event.id)
            }
            if (missing.isEmpty()) return Result.success(Unit)
            val updated = missing.fold(existing?.text ?: "") { text, event ->
                RemoteJournal.append(text, event.line)
            }
            val write = putFile(path, updated, existing?.sha, message)
            when {
                write.isSuccess -> return Result.success(Unit)
                write.exceptionOrNull() is ConflictException -> continue
                else -> return Result.failure(
                    write.exceptionOrNull() ?: GithubException(resources.getString(R.string.error_commit_rejected)),
                )
            }
        }
        return Result.failure(
            GithubException(resources.getString(R.string.error_journal_conflict), retryable = true),
        )
    }

    /** Reads habits.json from the repo root. The caller validates it before saving. */
    fun downloadConfig(): Result<String> {
        return when (val read = getFile(GithubTarget.CONFIG_PATH)) {
            is ReadResult.Failed -> Result.failure(read.error)
            is ReadResult.Ok -> {
                val file = read.file ?: return Result.failure(
                    GithubException(
                        resources.getString(
                            R.string.error_config_missing,
                            GithubTarget.CONFIG_PATH,
                            target.branch,
                        ),
                    ),
                )
                Result.success(file.text)
            }
        }
    }

    private sealed class ReadResult {
        data class Ok(val file: RemoteFile?) : ReadResult()
        data class Failed(val error: Exception) : ReadResult()
    }

    private fun getFile(path: String): ReadResult {
        return try {
            val response = request("GET", "/repos/${segments(target.repoFullName)}/contents/${segments(path)}?ref=${encode(target.branch)}")
            when (response.code) {
                200 -> {
                    val json = JSONObject(response.body)
                    if (json.has("content") && json.optString("encoding") == "base64") {
                        val size = json.optLong("size", 0)
                        if (size > MAX_CONTENT_BYTES) {
                            return ReadResult.Failed(
                                GithubException(resources.getString(R.string.error_file_too_large)),
                            )
                        }
                        val raw = json.getString("content").replace("\n", "").replace("\r", "")
                        val bytes = Base64.decode(raw, Base64.DEFAULT)
                        ReadResult.Ok(RemoteFile(String(bytes, Charsets.UTF_8), json.getString("sha")))
                    } else {
                        // Directories and submodules return no content; both are errors here.
                        ReadResult.Failed(
                            GithubException(resources.getString(R.string.error_not_a_file, path)),
                        )
                    }
                }
                404 -> ReadResult.Ok(null)
                else -> ReadResult.Failed(GithubException(errorFor(response), retryable = response.code >= 500))
            }
        } catch (e: Exception) {
            ReadResult.Failed(GithubException(networkError(e), retryable = true))
        }
    }

    private fun putFile(path: String, text: String, sha: String?, message: String): Result<Unit> {
        return try {
            val body = JSONObject().apply {
                put("message", message)
                put("content", Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                put("branch", target.branch)
                if (sha != null) put("sha", sha)
            }
            val response = request(
                "PUT",
                "/repos/${segments(target.repoFullName)}/contents/${segments(path)}",
                body.toString(),
            )
            when (response.code) {
                200, 201 -> Result.success(Unit)
                409 -> Result.failure(ConflictException("commit in conflitto"))
                422 -> Result.failure(ConflictException("sha del file non aggiornato"))
                else -> Result.failure(GithubException(errorFor(response), retryable = response.code >= 500))
            }
        } catch (e: Exception) {
            Result.failure(GithubException(networkError(e), retryable = true))
        }
    }

    private fun request(method: String, path: String, body: String? = null): Response =
        httpRequest(token, method, path, body)

    private fun errorFor(response: Response): String {
        val message = try {
            JSONObject(response.body).optString("message")
        } catch (e: Exception) {
            ""
        }
        val detail = message.take(200).ifBlank { "HTTP ${response.code}" }
        return when (response.code) {
            401 -> resources.getString(R.string.error_token_invalid)
            403 -> resources.getString(R.string.error_permissions)
            404 -> resources.getString(R.string.error_repo_not_found, detail)
            else -> detail
        }
    }

    private fun networkError(e: Exception): String =
        resources.getString(R.string.error_network, e.javaClass.simpleName)

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun segments(path: String): String = GithubTarget.pathSegments(path).joinToString("/") { encode(it) }

    private class ConflictException(message: String) : Exception(message)

    class GithubException(message: String, val retryable: Boolean = false) : Exception(message)

    companion object {

        private const val API_BASE = "https://api.github.com"
        private const val MAX_CONTENT_BYTES = 1_000_000L
        private const val MAX_CONFLICT_RETRIES = 4

        private fun httpRequest(token: String, method: String, path: String, body: String? = null): Response {
            val connection = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "HabitCommit")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return Response(code, text)
            } finally {
                connection.disconnect()
            }
        }

        /**
         * Repositories the PAT can see, most recently updated first.
         *
         * A fine-grained PAT scoped to a single repository only lists that one;
         * listing a wider set needs the token to cover those repositories too
         * (Contents: Read and write + Metadata: Read stays the narrowest useful set).
         * Only the first page (100 repos) is fetched.
         */
        fun listRepositories(resources: Resources, token: String): Result<List<RepoSummary>> = try {
            val path = "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member"
            val response = httpRequest(token, "GET", path)
            if (response.code == 200) {
                val array = JSONArray(response.body)
                val repos = buildList {
                    for (i in 0 until array.length()) {
                        val json = array.optJSONObject(i) ?: continue
                        val fullName = json.optString("full_name")
                        val names = fullName.split('/', limit = 2)
                        if (names.size != 2) continue
                        add(
                            RepoSummary(
                                owner = names[0],
                                name = names[1],
                                fullName = fullName,
                                isPrivate = json.optBoolean("private", false),
                                defaultBranch = json.optString("default_branch", GithubTarget.DEFAULT_BRANCH),
                            ),
                        )
                    }
                }
                Result.success(repos)
            } else {
                Result.failure(
                    GithubException(
                        staticErrorFor(resources, response),
                        retryable = response.code >= 500,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(
                GithubException(
                    resources.getString(R.string.error_network, e.javaClass.simpleName),
                    retryable = true,
                ),
            )
        }

        private fun staticErrorFor(resources: Resources, response: Response): String {
            val message = try {
                JSONObject(response.body).optString("message")
            } catch (e: Exception) {
                ""
            }
            val detail = message.take(200).ifBlank { "HTTP ${response.code}" }
            return when (response.code) {
                401 -> resources.getString(R.string.error_token_invalid)
                403 -> resources.getString(R.string.error_permissions)
                404 -> resources.getString(R.string.error_repo_not_found, detail)
                else -> detail
            }
        }
    }
}
