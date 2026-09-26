package com.covdbg.coverage.engine

import com.intellij.coverage.CoverageRunner
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration

/**
 * Required by [CovdbgCoverageEngine.createCoverageEnabledConfiguration], and never used: the engine
 * applies to no run configuration, because covdbg runs are started by "Run with covdbg", not by the
 * platform's "Run with Coverage".
 */
class CovdbgCoverageEnabledConfiguration(configuration: RunConfigurationBase<*>) :
    CoverageEnabledConfiguration(configuration, CoverageRunner.getInstance(CovdbCoverageRunner::class.java))
