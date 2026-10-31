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
}
