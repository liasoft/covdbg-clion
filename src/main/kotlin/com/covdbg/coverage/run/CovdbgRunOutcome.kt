package com.covdbg.coverage.run

/** What a covdbg run actually did, as told by its exit code and its own output. */
sealed interface CovdbgRunOutcome {

    /**
     * Coverage was written and may be loaded. [outputPath] is where covdbg says it wrote, as printed,
     * so only good for comparing and logging: it went through the console's charset. [gated] means
     * covdbg pruned the database to the ten most-hit files because reporting is gated for the account.
     */
    data class Success(val outputPath: String, val gated: Boolean = false) : CovdbgRunOutcome

    /** The run was refused. No database was written. */
    data class NotLicensed(val message: String) : CovdbgRunOutcome

    /** Nothing passed the coverage filter, so there was nothing to track. */
    data object NoFunctionsToTrack : CovdbgRunOutcome

    /** covdbg found no .covdbg.yaml. [searchedDirs] are the directories it looked in. */
    data class MissingConfig(val searchedDirs: List<String>) : CovdbgRunOutcome

    /** covdbg rejected one of our options - most likely an installation older than 1.3.0. */
    data class StaleCli(val detail: String) : CovdbgRunOutcome

    data class Failed(val exitCode: Int, val detail: String?) : CovdbgRunOutcome
}

/**
 * Classifies a finished covdbg run.
 *
 * Pure: it never looks at the filesystem. That is the point - a refused run writes no database but
 * leaves an earlier run's file in place, so file existence says nothing about what just happened.
 *
 * Every marker below is a literal covdbg emits. covdbg reports most failures as exit code 1, so the
 * text is the only thing that separates them; `This run is not licensed` is the same de-facto marker
 * covdbg's own MCP server and integration tests match on.
 */
object CovdbgOutcomeClassifier {

    const val NOT_LICENSED_MARKER = "This run is not licensed"
    const val COVERAGE_WRITTEN_PREFIX = "covdbg: coverage written to "
    const val GATED_MARKER = "coverage reporting is gated for this account"
    const val NO_CONFIG_MARKER = "No configuration file found."
    const val LOOKED_FOR_MARKER = "Looked for "
    const val UNEXPECTED_ARG_MARKER = "The following argument"
    const val NOT_WRITTEN_MARKER = "no coverage database was written to"

    const val EXIT_NO_FUNCTIONS_TO_TRACK = 2

    fun classify(
        exitCode: Int,
        stdoutLines: List<String>,
        stderrLines: List<String>
    ): CovdbgRunOutcome {
        // Licensing first. The refusal is awaited only after the target has run to completion, so it
        // is the last thing covdbg says and it outranks anything the target itself printed. covdbg
        // logs it to stderr; checking both streams costs nothing and cannot misfire.
        val bothStreams = stderrLines + stdoutLines

        bothStreams.firstOrNull { it.contains(NOT_LICENSED_MARKER) }?.let { line ->
            return CovdbgRunOutcome.NotLicensed(licenseMessageOf(line))
        }

        // Then success, whatever the exit code and whatever else was printed. covdbg says this only
        // once it has written this run's database, and it is the one line the target cannot fake by
        // accident. stderr carries the target's own stderr too - under a PTY it is all output - so a
        // test that prints "The following argument was not expected" or exits with 2 must not turn a
        // written database into a failure. `contains`, not `startsWith`: a terminal may prefix the
        // line with escape sequences.
        stdoutLines.firstOrNull { it.contains(COVERAGE_WRITTEN_PREFIX) }?.let { written ->
            return CovdbgRunOutcome.Success(
                outputPath = written.substringAfter(COVERAGE_WRITTEN_PREFIX).trim(),
                gated = stdoutLines.any { it.contains(GATED_MARKER) }
            )
        }

        // Nothing was written, so these are covdbg's own verdicts: all of them stop the run before or
        // instead of writing a database.
        if (exitCode == EXIT_NO_FUNCTIONS_TO_TRACK) {
            return CovdbgRunOutcome.NoFunctionsToTrack
        }

        if (stderrLines.any { it.contains(NO_CONFIG_MARKER) }) {
            return CovdbgRunOutcome.MissingConfig(searchedDirsOf(stderrLines))
        }

        stderrLines.firstOrNull { it.contains(UNEXPECTED_ARG_MARKER) }?.let { line ->
            return CovdbgRunOutcome.StaleCli(line.trim())
        }

        // covdbg says so itself when it could not write the database, on stdout and with a non-zero
        // exit. Prefer that sentence over whatever the target happened to print to stderr.
        val notWritten = bothStreams.firstOrNull { it.contains(NOT_WRITTEN_MARKER) }?.trim()
        return CovdbgRunOutcome.Failed(exitCode, notWritten ?: stderrLines.firstInteresting())
    }

    /** `covdbg: This run is not licensed: <message>` -> `<message>`. */
    private fun licenseMessageOf(line: String): String {
        val marker = line.indexOf(NOT_LICENSED_MARKER)
        val rest = line.substring(marker + NOT_LICENSED_MARKER.length).trim()
        return rest.removePrefix(":").trim().ifBlank { line.trim() }
    }

    /**
     * The indented directories covdbg lists after `Looked for .covdbg.yaml in:`, up to the
     * "Please create ..." sentence that follows them.
     */
    private fun searchedDirsOf(stderrLines: List<String>): List<String> {
        val start = stderrLines.indexOfFirst { it.contains(LOOKED_FOR_MARKER) && it.contains(" in:") }
        if (start < 0) return emptyList()
        return stderrLines.asSequence()
            .drop(start + 1)
            .takeWhile { it.isBlank() || it.first().isWhitespace() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}

/** First line with something on it, which is what a one-line diagnostic wants. */
internal fun List<String>.firstInteresting(): String? =
    asSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
