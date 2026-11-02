package dev.habitcommit.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GithubTargetTest {

    @Test
    fun parsesAndNormalizesTarget() {
        val target = GithubTarget.parse(" giorg ", "habit-data", "", "/journal/").getOrThrow()
        assertEquals("giorg", target.owner)
        assertEquals("habit-data", target.repo)
        assertEquals(GithubTarget.DEFAULT_BRANCH, target.branch)
        assertEquals("journal", target.journalDir)
    }

    @Test
    fun buildsOneFilePerDeviceAndMonth() {
        val target = GithubTarget.parse("giorg", "habit-data", "main", "data/journal").getOrThrow()
        assertEquals(
            "data/journal/2026-10/abc.jsonl",
            target.journalPath(LocalDate.of(2026, 10, 25), "abc"),
        )
    }

    @Test
    fun rejectsInvalidOwnerAndRepo() {
        assertTrue(GithubTarget.parse("-bad", "repo", "main", "journal").isFailure)
        assertTrue(GithubTarget.parse("owner", "repo with space", "main", "journal").isFailure)
        assertTrue(GithubTarget.parse("owner", ".", "main", "journal").isFailure)
        assertTrue(GithubTarget.parse("owner", "../repo", "main", "journal").isFailure)
    }

    @Test
    fun rejectsInvalidBranches() {
        assertFalse(GithubTarget.validBranch("/main"))
        assertFalse(GithubTarget.validBranch("main/"))
        assertFalse(GithubTarget.validBranch("a..b"))
        assertFalse(GithubTarget.validBranch("a//b"))
        assertFalse(GithubTarget.validBranch("-bad"))
        assertTrue(GithubTarget.validBranch("feature/sync"))
    }

    @Test
    fun rejectsPathsOutsideTheRepo() {
        assertFalse(GithubTarget.validPath("/etc/passwd"))
        assertFalse(GithubTarget.validPath("journal/../../etc"))
        assertFalse(GithubTarget.validPath("journal/"))
        assertFalse(GithubTarget.validPath("a/b/c/d/e/f"))
        assertTrue(GithubTarget.validPath("data/habit-journal"))
        assertTrue(GithubTarget.parse("owner", "repo", "main", "../data").isFailure)
    }

    @Test
    fun journalPathZeroPadsMonthsAndAppendsDeviceFile() {
        val target = GithubTarget.parse("owner", "repo", "main", "journal").getOrThrow()
        assertEquals(
            "journal/2026-01/device.jsonl",
            target.journalPath(LocalDate.of(2026, 1, 1), "device"),
        )
        assertEquals(
            "journal/2026-12/device.jsonl",
            target.journalPath(LocalDate.of(2026, 12, 31), "device"),
        )
        // Single-digit years are padded to four digits too.
        assertEquals(
            "journal/0999-03/device.jsonl",
            target.journalPath(LocalDate.of(999, 3, 15), "device"),
        )
    }

    @Test
    fun journalPathUsesNestedDirectoryAndBothDatesFromTheSameMonthGitTheSameFile() {
        val target = GithubTarget.parse("owner", "repo", "main", "/data/journal/").getOrThrow()
        val first = target.journalPath(LocalDate.of(2026, 10, 1), "dev")
        val last = target.journalPath(LocalDate.of(2026, 10, 31), "dev")
        assertEquals("data/journal/2026-10/dev.jsonl", first)
        assertEquals(first, last)
    }

    @Test
    fun rejectsMoreInvalidBranches() {
        assertFalse(GithubTarget.validBranch(""))
        assertFalse(GithubTarget.validBranch("."))
        assertFalse(GithubTarget.validBranch("main."))
        assertFalse(GithubTarget.validBranch("main.lock"))
        assertFalse(GithubTarget.validBranch("feature//x"))
        assertFalse(GithubTarget.validBranch("has space"))
        assertFalse(GithubTarget.validBranch("main\n"))
        assertFalse(GithubTarget.validBranch(".."))
        assertTrue(GithubTarget.validBranch("release/1.0"))
        assertTrue(GithubTarget.validBranch("a"))
    }

    @Test
    fun rejectsDotSegmentsAndOversizedPaths() {
        assertFalse(GithubTarget.validPath(""))
        assertFalse(GithubTarget.validPath("."))
        assertFalse(GithubTarget.validPath(".."))
        assertFalse(GithubTarget.validPath("./journal"))
        assertFalse(GithubTarget.validPath("journal/./sub"))
        assertFalse(GithubTarget.validPath("journal/.."))
        assertFalse(GithubTarget.validPath("journal\\sub"))
        assertFalse(GithubTarget.validPath("a".repeat(65)))
        assertFalse(GithubTarget.validPath((1..6).joinToString("/") { "a".repeat(40) }))
        assertTrue(GithubTarget.validPath("a/b/c/d/e"))
    }

    @Test
    fun parseAppliesDefaultsAndRejectsInvalidSettings() {
        val defaults = GithubTarget.parse("owner", "repo", "  ", "   ").getOrThrow()
        assertEquals(GithubTarget.DEFAULT_BRANCH, defaults.branch)
        assertEquals(GithubTarget.DEFAULT_JOURNAL_DIR, defaults.journalDir)

        // Leading/trailing slashes are stripped, so "/abs" becomes the valid "abs".
        assertEquals("abs", GithubTarget.parse("owner", "repo", "main", "/abs/").getOrThrow().journalDir)

        assertTrue(GithubTarget.parse("owner", "repo", "-bad", "journal").isFailure)
        assertTrue(GithubTarget.parse("owner", "repo", "main", "journal//sub").isFailure)
        assertTrue(GithubTarget.parse("bad_owner", "repo", "main", "journal").isFailure)
        assertTrue(GithubTarget.parse("owner", "repo..lock", "main", "journal").isSuccess)
    }
}
