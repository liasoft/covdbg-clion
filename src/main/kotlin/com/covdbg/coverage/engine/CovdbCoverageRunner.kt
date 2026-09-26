package com.covdbg.coverage.engine

import com.covdbg.coverage.db.CovdbReader
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageLoadErrorReporter
import com.intellij.coverage.CoverageLoadingResult
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.FailedCoverageLoadingResult
import com.intellij.coverage.SuccessCoverageLoadingResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * Loads a `.covdb` into the platform's coverage model.
 *
 * Not to be confused with `com.covdbg.coverage.run.CovdbgCoverageRunner`, the program runner behind
 * "Run with covdbg": this one only reads databases, for suites created after a run and for the
 * platform's "Import External Coverage Report".
 */
class CovdbCoverageRunner : CoverageRunner() {

    override fun getId(): String = ID

    override fun getPresentableName(): String = "covdbg"

    override fun getDataFileExtension(): String = "covdb"

    override fun acceptsCoverageEngine(engine: CoverageEngine): Boolean = engine is CovdbgCoverageEngine

    /**
     * The `File` overload on purpose: 2026.2 adds a `Path` one whose default delegates here, while
     * 2025.3 has only this.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun loadCoverageData(
        sessionDataFile: File,
        baseCoverageSuite: CoverageSuite?,
        reporter: CoverageLoadErrorReporter
    ): CoverageLoadingResult {
        val project = baseCoverageSuite?.project
        return try {
            CovdbReader(sessionDataFile.path).use { reader ->
                val version = reader.getSchemaVersion()
                if (version < MIN_SCHEMA_VERSION) {
                    return FailedCoverageLoadingResult(
                        "${sessionDataFile.name} uses .covdb schema version $version; this plugin " +
                            "reads version $MIN_SCHEMA_VERSION and newer, written by covdbg 1.3.0 or later."
                    )
                }
                val mapper = pathMapper(reader.getSourceRoot(), project)
                val records = reader.getAllLineCoverage()
                val data = CovdbProjectDataBuilder.build(records, mapper::map)
                SuccessCoverageLoadingResult(data)
            }
        } catch (e: Exception) {
            FailedCoverageLoadingResult("Could not read ${sessionDataFile.path}: ${e.message}", e)
        }
    }

    private fun pathMapper(sourceRoot: String?, project: Project?): CovdbPathMapper =
        CovdbPathMapper(
            sourceRoot = sourceRoot,
            projectRoot = project?.basePath,
            existingFile = { path ->
                LocalFileSystem.getInstance().findFileByPath(path)?.takeIf { !it.isDirectory }?.path
            },
            projectFilesNamed = { name ->
                if (project == null || project.isDisposed) {
                    emptyList()
                } else {
                    ApplicationManager.getApplication().runReadAction(Computable {
                        FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
                            .map { it.path }
                    })
                }
            }
        )

    companion object {
        const val ID = "covdbg"

        /** Schema v7 (natural keys) is what covdbg 1.3.0 writes and what the queries rely on. */
        const val MIN_SCHEMA_VERSION = 7

        fun getInstance(): CovdbCoverageRunner = getInstance(CovdbCoverageRunner::class.java)
    }
}
