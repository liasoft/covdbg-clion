package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every transcript here uses covdbg's literal output. If covdbg changes its wording these tests are
 * the thing that should fail.
 */
class CovdbgOutcomeClassifierTest {

    private val outputPath = """C:\proj\.covdbg\tests.covdb"""

    @Test
    fun `written and exit 0 is a success carrying covdbg's own path`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            0,
            listOf(
                "[==========] Running 12 tests.",
                "[  PASSED  ] 12 tests.",
                "covdbg: coverage written to $outputPath"
            ),
            emptyList()
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `the target's own stderr cannot turn a written database into a failure`() {
        // A target with a CLI11-style parser, or one that happens to mention a missing configuration,
        // prints covdbg's failure markers on its own stderr. covdbg still wrote this run's database.
        val outcome = CovdbgOutcomeClassifier.classify(
            0,
            listOf("covdbg: coverage written to $outputPath"),
            listOf(
                "The following argument was not expected: --x",
                "Error: No configuration file found."
            )
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a target that exits with 2 still has its coverage loaded`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            2,
            listOf("[  FAILED  ] 1 test.", "covdbg: coverage written to $outputPath"),
            emptyList()
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a written database is loaded even when covdbg passes on a failing exit code`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            listOf("[  FAILED  ] 3 tests.", "covdbg: coverage written to $outputPath"),
            listOf("tests.cpp(42): error: Expected equality")
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a written line behind terminal escape sequences is still found`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            0,
            listOf("\u001B[0m\u001B[?25hcovdbg: coverage written to $outputPath"),
            emptyList()
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a gated banner alongside a written database marks the success as gated`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            0,
            listOf(
                "covdbg: coverage reporting is gated for this account. The analysis runs, and the " +
                    "database keeps only the ten most-hit files.",
                "covdbg: coverage written to $outputPath"
            ),
            emptyList()
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = true), outcome)
    }

    @Test
    fun `the refusal wins even though the target already ran and printed`() {
        // covdbg awaits the entitlement only after the target finishes, so the refusal is the last
        // thing said. Classification must not be swayed by a target that exited cleanly and noisily.
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            listOf("[  PASSED  ] 12 tests.", "covdbg: collecting coverage for tests.exe"),
            listOf(
                "covdbg: This run is not licensed: Not signed in. Run `covdbg login`, or set " +
                    "COVDBG_PROJECT_TOKEN in CI."
            )
        )
        val notLicensed = assertInstanceOf(CovdbgRunOutcome.NotLicensed::class.java, outcome)
        assertEquals(
            "Not signed in. Run `covdbg login`, or set COVDBG_PROJECT_TOKEN in CI.",
            notLicensed.message
        )
    }

    @Test
    fun `a stale database from an earlier run cannot make a refusal look like success`() {
        // The classifier never touches the filesystem, so the only way to report success is for
        // covdbg to say it wrote something during this run.
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            emptyList(),
            listOf("covdbg: This run is not licensed: Your seat has expired.")
        )
        assertInstanceOf(CovdbgRunOutcome.NotLicensed::class.java, outcome)
    }

    @Test
    fun `exit 2 means nothing passed the coverage filter`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            2,
            emptyList(),
            listOf("covdbg: Error: No functions passed the coverage filter - nothing to track")
        )
        assertEquals(CovdbgRunOutcome.NoFunctionsToTrack, outcome)
    }

    @Test
    fun `a missing config is reported with the directories covdbg searched`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            emptyList(),
            listOf(
                "Error: No configuration file found.",
                "Looked for .covdbg.yaml in:",
                """  C:\proj""",
                """  C:\proj\build\RelWithDebInfo\bin""",
                "Please create a .covdbg.yaml file in the repository or specify one with --config."
            )
        )
        val missing = assertInstanceOf(CovdbgRunOutcome.MissingConfig::class.java, outcome)
        assertEquals(listOf("""C:\proj""", """C:\proj\build\RelWithDebInfo\bin"""), missing.searchedDirs)
    }

    @Test
    fun `an option covdbg does not know is reported as a stale CLI`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            emptyList(),
            listOf("The following argument was not expected: --appdata", "Run with --help for more information.")
        )
        val stale = assertInstanceOf(CovdbgRunOutcome.StaleCli::class.java, outcome)
        assertTrue(stale.detail.contains("--appdata"))
    }

    @Test
    fun `a save failure quotes covdbg's own sentence, not the target's output`() {
        // covdbg says this on stdout and exits 1, so the detail has to be picked from stdout even
        // though the target has also written to stderr.
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            listOf("covdbg: no coverage database was written to $outputPath - see the log for the reason"),
            listOf("target: warning: something unrelated")
        )
        val failed = assertInstanceOf(CovdbgRunOutcome.Failed::class.java, outcome)
        assertEquals(1, failed.exitCode)
        assertEquals(
            "covdbg: no coverage database was written to $outputPath - see the log for the reason",
            failed.detail
        )
    }

    @Test
    fun `an unexplained non-zero exit keeps the code and the first stderr line`() {
        val outcome = CovdbgOutcomeClassifier.classify(
            1,
            listOf("target output"),
            listOf("covdbg: Failed to create process: The system cannot find the file specified.")
        )
        val failed = assertInstanceOf(CovdbgRunOutcome.Failed::class.java, outcome)
        assertEquals(1, failed.exitCode)
        assertEquals(
            "covdbg: Failed to create process: The system cannot find the file specified.",
            failed.detail
        )
    }

    @Test
    fun `licensing outranks a no-functions exit code`() {
        // covdbg checks the entitlement before the filter result, so exit 2 with a refusal on the
        // stream is still a licensing problem, not a filter problem.
        val outcome = CovdbgOutcomeClassifier.classify(
            2,
            emptyList(),
            listOf("covdbg: This run is not licensed: Not signed in.")
        )
        assertInstanceOf(CovdbgRunOutcome.NotLicensed::class.java, outcome)
    }

    // ── One merged stream, as under an emulated terminal ────────────────
    // A PTY has no separate stderr, so the run passes the same lines as both streams.

    private fun classifyMerged(exitCode: Int, lines: List<String>) =
        CovdbgOutcomeClassifier.classify(exitCode, lines, lines)

    @Test
    fun `a merged stream still finds a success`() {
        val outcome = classifyMerged(
            0,
            listOf("covdbg: collecting coverage for app.exe", "hello", "covdbg: coverage written to $outputPath")
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a merged stream with the target's failure markers is still a success`() {
        val outcome = classifyMerged(
            0,
            listOf(
                "covdbg: collecting coverage for app.exe",
                "The following argument was not expected: --x",
                "covdbg: coverage written to $outputPath"
            )
        )
        assertEquals(CovdbgRunOutcome.Success(outputPath, gated = false), outcome)
    }

    @Test
    fun `a merged stream still finds a missing config`() {
        val outcome = classifyMerged(
            1,
            listOf(
                "Error: No configuration file found.",
                "Looked for .covdbg.yaml in:",
                """  C:\proj""",
                "Please create a .covdbg.yaml file in the repository or specify one with --config."
            )
        )
        val missing = assertInstanceOf(CovdbgRunOutcome.MissingConfig::class.java, outcome)
        assertEquals(listOf("""C:\proj"""), missing.searchedDirs)
    }

    @Test
    fun `a merged stream still finds a stale CLI`() {
        val outcome = classifyMerged(
            1,
            listOf("The following argument was not expected: --appdata", "Run with --help for more information.")
        )
        assertInstanceOf(CovdbgRunOutcome.StaleCli::class.java, outcome)
    }
}
