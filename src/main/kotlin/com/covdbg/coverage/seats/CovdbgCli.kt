package com.covdbg.coverage.seats

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/** Result of a short, captured covdbg invocation. */
data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean
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

    fun capture(exe: String, args: List<String>, workDir: String?, timeoutMs: Int): CliResult {
        val cmd = GeneralCommandLine(exe)
        cmd.addParameters(args)
        if (!workDir.isNullOrBlank()) {
            cmd.workDirectory = File(workDir)
        }

        return try {
            val output = CapturingProcessHandler(cmd).runProcess(timeoutMs)
            CliResult(output.exitCode, output.stdout, output.stderr, output.isTimeout)
        } catch (e: Exception) {
            LOG.debug("covdbg ${args.joinToString(" ")} could not be run", e)
            CliResult(-1, "", e.message ?: "covdbg could not be started", false)
        }
    }
}
