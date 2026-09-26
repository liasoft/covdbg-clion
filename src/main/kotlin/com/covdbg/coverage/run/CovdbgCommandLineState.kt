package com.covdbg.coverage.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.execution.ParametersListUtil
import java.io.File

/**
 * Runs a covdbg run configuration: a target the user named by hand, under covdbg.
 *
 * CLion run configurations do not come through here. "Run with covdbg" on those runs the
 * configuration itself, and [CovdbgRunConfigurationExtension] puts covdbg in front of the target.
 */
class CovdbgCommandLineState(
    environment: ExecutionEnvironment,
    private val config: CovdbgRunConfiguration
) : CommandLineState(environment) {

    private val plan = CovdbgRunPlan.create(
        environment.project,
        target = config.targetExecutable,
        workingDirectory = expanded(config.workingDirectory),
        configOverride = config.covdbgConfigPath,
        followChildren = config.followChildren,
        mode = config.mode
    )

    override fun startProcess(): ProcessHandler {
        val exe = CovdbgExecutableResolver.pathFor(environment.project)
            ?: throw ExecutionException(CovdbgExecutableResolver.NOT_FOUND_MESSAGE)

        plan.prepareOutput()

        val commandLine = GeneralCommandLine(exe).apply {
            // The splitting CLion applies to its own Program arguments field.
            addParameters(plan.arguments(ParametersListUtil.parse(expanded(config.targetArguments))))
            workDirectory = File(plan.workingDirectory)
        }
        LOG.info("Running coverage: ${commandLine.commandLineString}")

        val handler = KillableProcessHandler(commandLine)
        handler.addProcessListener(CovdbgRunMonitor(environment.project, plan, usesPty = false))
        return handler
    }

    private fun expanded(value: String): String = CovdbgMacros.expand(value, environment.project)

    companion object {
        private val LOG = Logger.getInstance(CovdbgCommandLineState::class.java)
    }
}
