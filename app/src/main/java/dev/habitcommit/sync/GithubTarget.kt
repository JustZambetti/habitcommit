package dev.habitcommit.sync

import java.time.LocalDate
import java.util.Locale

/**
 * Where the journal lives inside a GitHub repository.
 *
 * The token is scoped by repository, never by a directory: GitHub fine-grained
 * PATs cannot restrict access to a path inside a repo. The app only ever calls
 * the configured journal path, but server-side enforcement (if any) has to come
 * from a push ruleset on the repository itself.
 */
data class GithubTarget(
    val owner: String,
    val repo: String,
    val branch: String,
    val journalDir: String,
) {
    val repoFullName: String get() = "$owner/$repo"

    fun journalPath(date: LocalDate, deviceId: String): String {
        val month = String.format(Locale.ROOT, "%04d-%02d", date.year, date.monthValue)
        return "$journalDir/$month/$deviceId.jsonl"
    }

    companion object {
        const val DEFAULT_BRANCH = "main"
        const val DEFAULT_JOURNAL_DIR = "journal"
        const val CONFIG_PATH = "habits.json"

        private val ownerPattern = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val repoPattern = Regex("^[A-Za-z0-9._-]{1,100}$")
        private val branchPattern = Regex("^[A-Za-z0-9._/-]{1,255}$")
        private val pathSegmentPattern = Regex("^[A-Za-z0-9._-]{1,64}$")

        fun parse(owner: String, repo: String, branch: String, journalDir: String): Result<GithubTarget> {
            return runCatching {
                val cleanOwner = owner.trim()
                val cleanRepo = repo.trim()
                val cleanBranch = branch.trim().ifEmpty { DEFAULT_BRANCH }
                val cleanDir = journalDir.trim().trim('/').ifEmpty { DEFAULT_JOURNAL_DIR }

                require(ownerPattern.matches(cleanOwner)) { "invalid GitHub owner" }
                require(repoPattern.matches(cleanRepo) && cleanRepo != "." && cleanRepo != "..") {
                    "invalid repository name"
                }
                require(validBranch(cleanBranch)) { "invalid branch" }
                require(validPath(cleanDir)) { "invalid journal folder" }
                GithubTarget(cleanOwner, cleanRepo, cleanBranch, cleanDir)
            }
        }

        fun validBranch(branch: String): Boolean {
            if (!branchPattern.matches(branch)) return false
            if (branch.startsWith('-') || branch.startsWith('/') || branch.endsWith('/')) return false
            if (branch.startsWith('.') || branch.endsWith('.') || branch.endsWith(".lock")) return false
            if (".." in branch || "//" in branch) return false
            return true
        }

        /** Relative, slash-separated path. Absolute paths and `..` are rejected. */
        fun validPath(path: String): Boolean {
            if (path.isEmpty() || path.length > 200) return false
            if (path.startsWith('/') || path.endsWith('/')) return false
            val segments = path.split('/')
            if (segments.size > 5) return false
            return segments.all { it !in setOf(".", "..") && pathSegmentPattern.matches(it) }
        }

        fun pathSegments(path: String): List<String> = path.split('/')
    }
}
