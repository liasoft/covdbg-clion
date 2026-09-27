package com.covdbg.coverage.actions

import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.config.CovdbgLayout
import com.covdbg.coverage.engine.CovdbgCoverageSuites
import com.covdbg.coverage.run.CovdbgArgs
import com.covdbg.coverage.run.CovdbgExecutableResolver
import com.covdbg.coverage.run.firstInteresting
import com.covdbg.coverage.seats.CovdbgCli
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Exports the loaded coverage as an offline HTML report using `covdbg convert -f HTML`.
 *
 * For HTML the output is a directory, and covdbg requires the path.
 */
class CovdbgExportHtmlReportAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isVisible = project != null
        e.presentation.isEnabled = project != null &&
            CovdbgCoverageSuites.currentCovdbPath(project) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val covdb = CovdbgCoverageSuites.currentCovdbPath(project)
        if (covdb == null) {
            CovdbgNotifications.warn(project, "No covdbg coverage is shown to export.")
            return
        }
        ProgressManager.getInstance().run(ExportTask(project, covdb))
    }

    private class ExportTask(project: Project, private val covdbPath: String) :
        Task.Backgroundable(project, "Exporting covdbg HTML report", true) {

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true

            val exe = CovdbgExecutableResolver.pathOrNotify(project) ?: return

            val projectRoot = project.basePath ?: File(covdbPath).parent
            val outputDir = CovdbgLayout.htmlDirFor(projectRoot, covdbPath)
            // The plugin's own directory, emptied first: an index.html left by an earlier export would
            // otherwise pass for this one's when covdbg writes nothing.
            FileUtil.delete(outputDir)
            outputDir.mkdirs()

            val result = CovdbgCli.capture(
                exe,
                CovdbgArgs.convertHtml(
                    input = covdbPath,
                    outputDir = outputDir.absolutePath,
                    title = project.name,
                    sourceRoot = projectRoot
                ),
                projectRoot,
                TIMEOUT_MS,
                indicator
            )

            if (result.cancelled) {
                CovdbgNotifications.info(project, "HTML report export cancelled.")
                return
            }
            if (result.timedOut) {
                CovdbgNotifications.error(
                    project,
                    "covdbg convert did not finish within ${TIMEOUT_MS / 60_000} minutes and was stopped."
                )
                return
            }
            if (result.exitCode != 0) {
                CovdbgNotifications.error(
                    project,
                    result.stderr.lines().firstInteresting()
                        ?: result.stdout.lines().firstInteresting()
                        ?: "covdbg convert exited with ${result.exitCode}."
                )
                return
            }

            // covdbg writes index.html into the directory we chose, and says so on stdout; no need to
            // parse back a path we passed in.
            val entry = File(outputDir, "index.html")
            if (!entry.exists()) {
                CovdbgNotifications.warn(
                    project,
                    "covdbg reported success but no report was found under ${outputDir.absolutePath}."
                )
                return
            }

            // A URI, not the File: BrowserUtil's File overload converts the path with the IDE's VFS
            // helper, which yields file://D:/... - drive letter in the authority position and spaces
            // left for the launcher to re-encode, so the browser gets a mangled address. Path.toUri()
            // gives the canonical file:///D:/... with everything encoded exactly once.
            BrowserUtil.browse(entry.toPath().toUri())
            CovdbgNotifications.info(project, "HTML coverage report: ${entry.absolutePath}")
        }

        private companion object {
            const val TIMEOUT_MS = 180_000
        }
    }
}
