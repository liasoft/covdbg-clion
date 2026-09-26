package com.covdbg.coverage.engine

import com.intellij.coverage.BaseCoverageSuite
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.openapi.project.Project

/** One `.covdb` shown in the platform coverage UI. */
class CovdbgCoverageSuite : BaseCoverageSuite {

    /** For restoring suites the platform persisted between sessions. */
    constructor() : super()

    constructor(
        name: String,
        project: Project,
        runner: CoverageRunner,
        fileProvider: CoverageFileProvider,
        timestamp: Long
    ) : super(name, project, runner, fileProvider, timestamp)

    override fun getCoverageEngine(): CoverageEngine = CovdbgCoverageEngine.getInstance()
}
