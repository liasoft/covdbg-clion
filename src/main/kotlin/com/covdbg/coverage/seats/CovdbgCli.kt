package com.covdbg.coverage.seats

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File

/** Result of a short, captured covdbg invocation. */
data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    /** The progress indicator was cancelled, and covdbg was killed. */
    val cancelled: Boolean = false
)

/**
 * Runs covdbg to completion and captures both streams.
 *
 * For short, non-interactive commands only — `whoami`, `logout`, `--version`, `convert`. Never call
 * this on the EDT, and never for `login`, which blocks for up to ten minutes and needs its output
 * streamed as it arrives (see CovdbgLoginTask).
 */
object CovdbgCli {

    private val LOG = Logger.getInstance(CovdbgCli::class.java)

    /**
     * @param indicator When given, cancelling it kills covdbg - for commands the user waits on, such
     *   as `convert`, where Cancel must not leave them waiting out the timeout.
     */
    fun capture(
        exe: String,
        args: List<String>,
        workDir: String?,
        timeoutMs: Int,
        indicator: ProgressIndicator? = null
    ): CliResult {
        val cmd = GeneralCommandLine(exe)
        cmd.addParameters(args)
        if (!workDir.isNullOrBlank()) {
            cmd.workDirectory = File(workDir)
        }

        return try {
            val handler = CapturingProcessHandler(cmd)
            val output = if (indicator != null) {
                handler.runProcessWithProgressIndicator(indicator, timeoutMs)
            } else {
                handler.runProcess(timeoutMs)
            }
            CliResult(output.exitCode, output.stdout, output.stderr, output.isTimeout, output.isCancelled)
        } catch (e: Exception) {
            LOG.debug("covdbg ${args.joinToString(" ")} could not be run", e)
            CliResult(-1, "", e.message ?: "covdbg could not be started", false)
        }
    }
}
