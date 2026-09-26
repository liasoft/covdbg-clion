package com.covdbg.coverage.engine

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuite
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/** Hands `.covdb` databases to the platform coverage UI. */
object CovdbgCoverageSuites {

    /**
     * Shows the coverage a run just wrote, replacing the suite of the previous run of the same target
     * so reruns do not pile up in "Show Coverage Data". The suite is named after the database file,
     * which is per target.
     *
     * The platform then opens the Coverage tool window and highlights editors as its own coverage
     * options say.
     */
    fun show(project: Project, covdbPath: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val dataManager = CoverageDataManager.getInstance(project)

            // unregister, not remove: removeCoverageSuite deletes the suite's data file (after asking),
            // and the previous suite's data file is the database this run just wrote.
            dataManager.suites
                .filter { it.coverageEngine is CovdbgCoverageEngine && samePath(it, covdbPath) }
                .forEach { dataManager.unregisterCoverageSuite(it) }

            // The File overload is the one both supported builds have; 2026.2 adds a Path one.
            @Suppress("DEPRECATION")
            val suite = dataManager.addExternalCoverageSuite(File(covdbPath), CovdbCoverageRunner.getInstance())
                ?: return@invokeLater
            dataManager.coverageGathered(suite)
        }
    }

    /** The `.covdb` behind the coverage currently shown, when it is covdbg's. */
    fun currentCovdbPath(project: Project): String? =
        CoverageDataManager.getInstance(project).currentSuitesBundle?.suites
            ?.firstOrNull { it.coverageEngine is CovdbgCoverageEngine }
            ?.coverageDataFileName

    private fun samePath(suite: CoverageSuite, path: String): Boolean =
        FileUtil.pathsEqual(suite.coverageDataFileName, path)
}
