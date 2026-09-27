package com.covdbg.coverage.run

import com.covdbg.coverage.CovdbgEditors
import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.CovdbgNotifications.action
import com.covdbg.coverage.config.CovdbgConfigTemplate
import com.covdbg.coverage.config.CovdbgConfigWriter
import com.covdbg.coverage.engine.CovdbgCoverageSuites
import com.covdbg.coverage.db.CovdbReader
import com.covdbg.coverage.seats.CovdbgLoginTask
import com.covdbg.coverage.seats.CovdbgSeatService
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Watches a covdbg process, decides from its exit code and output what the run did, and reports it:
 * loads the coverage on success, explains the failure otherwise.
 *
 * One covdbg invocation, not three. covdbg analyses the target binary before running it, so
 * functions the run never executes already appear with zero hits; the separate `analyze` and `merge`
 * steps earlier versions of this plugin performed added nothing but time. The one case they did
 * cover — code compiled into a static library that the run never links — belongs in `baseline:` in
 * `.covdbg.yaml`, where covdbg handles it inside the run.
 *
 * @param usesPty The process runs in a pseudo-terminal, which has a single output stream: covdbg's
 *   diagnostics then arrive among everything else rather than on stderr.
 */
class CovdbgRunMonitor(
    private val project: Project,
    private val plan: CovdbgRunPlan,
    private val usesPty: Boolean
) : ProcessListener {

    /** Bounded so a chatty target cannot grow the heap while we wait for covdbg's verdict. */
    private val stdoutLines = ArrayDeque<String>()
    private val stderrLines = ArrayDeque<String>()

    private val lines = LineAssembler { line, isError -> record(line, isError) }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        lines.accept(event.text, ProcessOutputType.isStderr(outputType))
    }

    override fun processTerminated(event: ProcessEvent) {
        lines.flush()
        val stdout = synchronized(stdoutLines) { stdoutLines.toList() }
        val stderr = if (usesPty) stdout else synchronized(stderrLines) { stderrLines.toList() }
        val outcome = CovdbgOutcomeClassifier.classify(event.exitCode, stdout, stderr)
        // The outcome carries everything still needed. A notification with an action lives in the
        // event log until expired and holds this object, so the captured output goes now.
        clearCapturedOutput()
        LOG.info("covdbg exited with ${event.exitCode}: $outcome")
        report(outcome)
    }

    // ── Output capture ──────────────────────────────────────────────────

    private fun record(line: String, isError: Boolean) {
        val target = if (isError) stderrLines else stdoutLines
        synchronized(target) {
            target.addLast(line)
            while (target.size > MAX_CAPTURED_LINES) {
                target.removeFirst()
            }
        }
    }

    private fun clearCapturedOutput() {
        synchronized(stdoutLines) { stdoutLines.clear() }
        synchronized(stderrLines) { stderrLines.clear() }
    }

    // ── Reporting ───────────────────────────────────────────────────────

    private fun report(outcome: CovdbgRunOutcome) {
        when (outcome) {
            is CovdbgRunOutcome.Success -> {
                // Load the path this run asked covdbg to write, not the one read back from its output:
                // that went through the console's charset, and a non-ASCII project path comes back
                // mangled. The printed path only confirms covdbg wrote where it was told to.
                if (!FileUtil.pathsEqual(outcome.outputPath, plan.outputCovdbPath)) {
                    LOG.info("covdbg reported ${outcome.outputPath}; loading ${plan.outputCovdbPath}")
                }
                loadCoverage(plan.outputCovdbPath, outcome.gated)
            }

            is CovdbgRunOutcome.NotLicensed -> notifyNotLicensed(outcome.message)

            CovdbgRunOutcome.NoFunctionsToTrack ->
                CovdbgNotifications.warn(
                    project,
                    "Nothing was tracked. Adjust the file or function filters in " +
                        "${CovdbgConfigTemplate.FILE_NAME} and try again.",
                    "No functions passed the coverage filter"
                )?.addOpenConfigAction()

            is CovdbgRunOutcome.MissingConfig -> notifyMissingConfig(outcome.searchedDirs)

            is CovdbgRunOutcome.StaleCli -> notifyStaleCli(outcome.detail)

            is CovdbgRunOutcome.Failed ->
                CovdbgNotifications.error(
                    project,
                    outcome.detail ?: "covdbg produced no diagnostic output.",
                    "covdbg run failed (exit code ${outcome.exitCode})"
                )?.addOpenLogAction()
        }
    }

    private fun notifyNotLicensed(message: String) {
        CovdbgNotifications.error(project, message, "This covdbg run is not licensed")
            ?.action("Sign In to covdbg") { CovdbgLoginTask(it).queue() }
            ?.action("Manage Seats") { BrowserUtil.browse(CovdbgNotifications.SERVICE_URL) }

        // The stored session may have expired or been signed out elsewhere; re-probe so the UI
        // stops claiming the user is signed in.
        CovdbgSeatService.getInstance(project).refresh()
    }

    private fun notifyMissingConfig(searchedDirs: List<String>) {
        val looked = if (searchedDirs.isEmpty()) {
            ""
        } else {
            "<br/>Looked in:<br/>" + searchedDirs.joinToString("<br/>") { "&nbsp;&nbsp;$it" }
        }
        CovdbgNotifications.error(
            project,
            "covdbg needs a configuration file to decide what to measure.$looked",
            "No ${CovdbgConfigTemplate.FILE_NAME} found"
        )?.action("Create ${CovdbgConfigTemplate.FILE_NAME}") { CovdbgConfigWriter.createAndOpen(it) }
    }

    private fun notifyStaleCli(detail: String) {
        val found = CovdbgExecutableResolver.pathFor(project)
            ?.let { CovdbgRuntimeInfoService.getInstance().version(it) }
            ?.let { " Found covdbg $it." }
            ?: ""
        CovdbgNotifications.error(
            project,
            "$detail<br/>${CovdbgVersion.REQUIREMENT}.$found",
            "covdbg rejected an option"
        )
    }

    private fun loadCoverage(pathToLoad: String, gated: Boolean) {
        try {
            LOG.info("Loading coverage data from: $pathToLoad")
            CovdbgCoverageSuites.show(project, pathToLoad)

            val summary = CovdbReader(pathToLoad).use { it.getCoverageSummary() }

            val message = "Coverage: %.1f%% lines (%d/%d), %.1f%% functions (%d/%d), %.1f%% blocks (%d/%d)".format(
                summary.lineCoveragePercent, summary.coveredLines, summary.totalLines,
                summary.functionCoveragePercent, summary.coveredFunctions, summary.totalFunctions,
                summary.blockCoveragePercent, summary.coveredBasicBlocks, summary.totalBasicBlocks
            )
            LOG.info(message)

            if (gated) {
                CovdbgNotifications.warn(
                    project,
                    "$message<br/>The analysis ran, and the database keeps only the ten most-hit " +
                        "files, so these totals do not describe the whole program.",
                    "Coverage reporting is gated for this account"
                )?.action("Manage Seats") { BrowserUtil.browse(CovdbgNotifications.SERVICE_URL) }
            } else {
                CovdbgNotifications.info(project, message)
            }
        } catch (e: Exception) {
            // A locked, truncated or foreign database is the environment's doing, and the user is told
            // below. LOG.error would also raise the IDE's internal-error indicator against the plugin.
            LOG.warn("Failed to load coverage data from $pathToLoad", e)
            CovdbgNotifications.error(
                project,
                e.message ?: e.javaClass.simpleName,
                "Failed to load coverage data"
            )
        }
    }

    // ── Notification actions ────────────────────────────────────────────

    private fun Notification.addOpenConfigAction() {
        val configFile = plan.configFile ?: return
        action("Open ${CovdbgConfigTemplate.FILE_NAME}") { CovdbgEditors.open(it, configFile) }
    }

    private fun Notification.addOpenLogAction() {
        val log = File(plan.logFilePath)
        action("Open Log") { project ->
            if (!CovdbgEditors.open(project, log)) {
                CovdbgNotifications.info(project, "No covdbg log at ${log.absolutePath}")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(CovdbgRunMonitor::class.java)

        private const val MAX_CAPTURED_LINES = 2_000
    }
}
