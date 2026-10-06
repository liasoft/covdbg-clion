package com.covdbg.coverage.engine

import com.intellij.coverage.CoverageAnnotator
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageViewExtension
import com.intellij.coverage.view.DirectoryCoverageViewExtension
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Presents covdbg's `.covdb` databases through the platform coverage UI: the editor gutter, the
 * Coverage tool window, "Show Coverage Data" and "Import External Coverage Report".
 *
 * Only presentation. Runs are started by "Run with covdbg" (`CovdbgCoverageRunner`, the program
 * runner), never by the platform's "Run with Coverage": CLion's own gcov/llvm engine already claims
 * CMake configurations there, so [isApplicableTo] is false. A finished run hands its database to
 * [CovdbgCoverageSuites].
 *
 * Coverage is keyed by source file path, the way CLion's own engine does it: each file's lines live
 * in a `ClassData` named after the file's `VirtualFile.path`, which is what [getQualifiedNames]
 * answers for an open file.
 *
 * The editor gutter is painted by [CovdbgEditorCoverage], not by the platform: the platform gutter
 * reaches a file's coverage only through internal API (see there), so it is off for covdbg.
 */
class CovdbgCoverageEngine : CoverageEngine() {

    override fun getPresentableText(): String = "covdbg"

    override fun isApplicableTo(conf: RunConfigurationBase<*>): Boolean = false

    override fun createCoverageEnabledConfiguration(
        conf: RunConfigurationBase<*>
    ): CoverageEnabledConfiguration = CovdbgCoverageEnabledConfiguration(conf)

    override fun createEmptyCoverageSuite(coverageRunner: CoverageRunner): CoverageSuite =
        CovdbgCoverageSuite()

    override fun createCoverageSuite(
        name: String,
        project: Project,
        runner: CoverageRunner,
        fileProvider: CoverageFileProvider,
        timestamp: Long
    ): CoverageSuite = CovdbgCoverageSuite(name, project, runner, fileProvider, timestamp)

    override fun getCoverageAnnotator(project: Project): CoverageAnnotator {
        // Asked before covdbg coverage is loaded, so the tab icon's and the gutter's listeners are in
        // place by the time it is shown.
        CovdbgCoverageTabIcon.getInstance(project)
        CovdbgEditorCoverage.getInstance(project)
        return CovdbgCoverageAnnotator.getInstance(project)
    }

    override fun getQualifiedNames(sourceFile: PsiFile): Set<String> =
        setOfNotNull(sourceFile.virtualFile?.path)

    /**
     * Off: the platform gutter would find no coverage without internal API. [CovdbgEditorCoverage]
     * paints it instead.
     */
    override fun coverageEditorHighlightingApplicableTo(psiFile: PsiFile): Boolean = false

    override fun acceptedByFilters(psiFile: PsiFile, suite: CoverageSuitesBundle): Boolean = true

    override fun createCoverageViewExtension(
        project: Project,
        suiteBundle: CoverageSuitesBundle
    ): CoverageViewExtension =
        DirectoryCoverageViewExtension(project, getCoverageAnnotator(project), suiteBundle)

    companion object {
        fun getInstance(): CovdbgCoverageEngine = EP_NAME.findExtensionOrFail(CovdbgCoverageEngine::class.java)
    }
}
