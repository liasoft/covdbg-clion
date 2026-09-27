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
import java.io.File

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
        // Asked before covdbg coverage is loaded, so the tab icon's listener is in place for its tab.
        CovdbgCoverageTabIcon.getInstance(project)
        return CovdbgCoverageAnnotator.getInstance(project)
    }

    override fun getQualifiedNames(sourceFile: PsiFile): Set<String> =
        setOfNotNull(sourceFile.virtualFile?.path)

    /**
     * The name the editor gutter looks a file's coverage up by. The one internal API this plugin
     * uses, allowed in `verifier-allowed-internal-api.txt`, and the reason for it:
     *
     * The gutter never asks [getQualifiedNames]. It asks for the file's "output files" - by default
     * the source file itself - and then for this name per output file, which is null by default, so
     * without this override no line is ever painted. CLion's own coverage engine overrides it the same
     * way. On 2026.2 the `Path` overload delegates here.
     */
    @Suppress("UnstableApiUsage")
    override fun getQualifiedName(outputFile: File, sourceFile: PsiFile): String? =
        sourceFile.virtualFile?.path

    override fun coverageEditorHighlightingApplicableTo(psiFile: PsiFile): Boolean = true

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
