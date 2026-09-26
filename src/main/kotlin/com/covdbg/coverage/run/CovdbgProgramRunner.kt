package com.covdbg.coverage.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor

/**
 * Custom ProgramRunner for CovdbgRunConfiguration.
 *
 * This is necessary because CLion's execution target system (e.g. "Debug-Visual Studio")
 * rejects configurations that don't have a compatible runner. By registering our own runner
 * that explicitly accepts CovdbgRunConfiguration, we ensure the configuration can always
 * be launched on the local target.
 */
class CovdbgProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "CovdbgProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is CovdbgRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val result = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(result, environment).showRunContent(environment.contentToReuse)
    }
}


