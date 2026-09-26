package com.covdbg.coverage.engine

import com.intellij.coverage.BaseCoverageAnnotator.DirCoverageInfo
import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.coverage.data.ProjectData

/**
 * Per-file and per-directory line percentages for the Coverage tool window and the project view.
 *
 * The platform's line counting over the suite's `ProjectData` is exactly what is wanted: every line
 * a `.covdb` reports is executable, and its status is already FULL, PARTIAL or NONE.
 *
 * What is not wanted is how the platform picks the files to count. Its folder walk asks the engine's
 * `coverageProjectViewStatisticsApplicableTo(file)`, which answers false unless overridden - so every
 * file is skipped and the Coverage view stays empty - and that method is internal API. The walk
 * below is the platform's own, except that a file counts when the database has coverage for it, and
 * that directories with no such file below them are skipped up front.
 */
@Service(Service.Level.PROJECT)
class CovdbgCoverageAnnotator(private val project: Project) : SimpleCoverageAnnotator(project) {

    override fun collectFolderCoverage(
        dir: VirtualFile,
        dataManager: CoverageDataManager,
        annotator: CoverageAnnotatorRunner,
        projectInfo: ProjectData,
        trackTestFolders: Boolean,
        index: ProjectFileIndex,
        coverageEngine: CoverageEngine,
        visitedDirs: MutableSet<in VirtualFile>,
        normalizedFiles2Files: Map<String, String>
    ): DirCoverageInfo? {
        // Not in the platform's walk: a directory with no covered file below it cannot count a file,
        // so build trees and third-party code are skipped before the index is even asked.
        val dirPath = normalizeFilePath(dir.path)
        if (dirPath !in coveredDirectories(normalizedFiles2Files) || dir in visitedDirs) return null

        val (excluded, isTestDir) = ApplicationManager.getApplication().runReadAction(Computable {
            val outsideProject = !index.isInContent(dir) && !index.isInLibrary(dir)
            val library = !shouldCollectCoverageInsideLibraryDirs() && index.isInLibrary(dir)
            (outsideProject || library) to TestSourcesFilter.isTestSources(dir, project)
        })
        if (excluded) return null
        visitedDirs.add(dir)
        if (!trackTestFolders && isTestDir) return null

        val children = dataManager.doInReadActionIfProjectOpen(Computable { dir.children }) ?: return null
        val info = DirCoverageInfo()
        for (child in children) {
            if (child.isDirectory) {
                val sub = collectFolderCoverage(
                    child, dataManager, annotator, projectInfo, trackTestFolders, index,
                    coverageEngine, visitedDirs, normalizedFiles2Files
                ) ?: continue
                info.totalFilesCount += sub.totalFilesCount
                info.coveredFilesCount += sub.coveredFilesCount
                info.totalLineCount += sub.totalLineCount
                info.coveredLineCount += sub.coveredLineCount
            } else if (normalizeFilePath(child.path) in normalizedFiles2Files) {
                val file = collectBaseFileCoverage(child, annotator, projectInfo, normalizedFiles2Files)
                    ?: continue
                info.totalLineCount += file.totalLineCount
                info.totalFilesCount++
                if (file.coveredLineCount > 0) {
                    info.coveredFilesCount++
                    info.coveredLineCount += file.coveredLineCount
                }
            }
        }
        if (info.totalFilesCount == 0) return null

        if (isTestDir) {
            annotator.annotateTestDirectory(dirPath, info)
        } else {
            annotator.annotateSourceDirectory(dirPath, info)
        }
        return info
    }

    /** The directories above the suite's covered files, for the file map currently being annotated. */
    @Volatile
    private var coveredDirectoriesCache: Pair<Map<String, String>, Set<String>>? = null

    /**
     * Every ancestor directory of the normalized file paths in [files], normalized the same way.
     * Computed once per suite: the platform passes the same map for the whole walk.
     */
    private fun coveredDirectories(files: Map<String, String>): Set<String> {
        coveredDirectoriesCache?.let { (forFiles, dirs) -> if (forFiles === files) return dirs }
        val dirs = HashSet<String>()
        for (file in files.keys) {
            var parent = file.substringBeforeLast('/', "")
            while (parent.isNotEmpty() && dirs.add(parent)) {
                parent = parent.substringBeforeLast('/', "")
            }
        }
        coveredDirectoriesCache = files to dirs
        return dirs
    }

    companion object {
        fun getInstance(project: Project): CovdbgCoverageAnnotator =
            project.getService(CovdbgCoverageAnnotator::class.java)
    }
}
