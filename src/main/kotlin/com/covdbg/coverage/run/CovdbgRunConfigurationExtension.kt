package com.covdbg.coverage.run

import com.covdbg.coverage.settings.CovdbgSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.execution.CidrRunConfigurationExtensionBase
import com.jetbrains.cidr.execution.ConfigurationExtensionContext
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import com.jetbrains.cidr.lang.workspace.OCRunConfiguration
import com.pty4j.PtyProcess
import java.io.File

/**
 * Puts covdbg in front of the target when a CLion run configuration is run with covdbg.
 *
 * CLion builds the target's command line exactly as for a normal Run - build before launch, the
 * toolchain environment, macros, environment variables, input redirection, terminal emulation, test
 * filters - hands it to run-configuration extensions, and then starts it itself, elevated if the
 * configuration asks. This extension only swaps the executable for covdbg and moves the target behind
 * covdbg's options, so everything else about the run, including the test tree and rerunning failed
 * tests, stays CLion's. Valgrind is integrated into CLion the same way.
 *
 * Every other run passes through untouched: all hooks check that the run came from
 * [CovdbgCoverageRunner].
 */
class CovdbgRunConfigurationExtension : CidrRunConfigurationExtensionBase() {

    override fun isApplicableFor(configuration: OCRunConfiguration<*, *>): Boolean = SystemInfo.isWindows

    override fun isEnabledFor(
        applicableConfiguration: OCRunConfiguration<*, *>,
        environment: CidrToolEnvironment,
        runnerSettings: RunnerSettings?
    ): Boolean = true

    override fun patchCommandLine(
        configuration: OCRunConfiguration<*, *>,
        runnerSettings: RunnerSettings?,
        environment: CidrToolEnvironment,
        cmdLine: GeneralCommandLine,
        runnerId: String,
        context: ConfigurationExtensionContext
    ) {
        if (runnerId != CovdbgCoverageRunner.RUNNER_ID) return

        if ((environment as? CPPEnvironment)?.isMSVC == false) {
            throw ExecutionException(NOT_MSVC_MESSAGE)
        }

        val project = configuration.project
        val covdbg = CovdbgExecutableResolver.pathFor(project)
            ?: throw ExecutionException(CovdbgExecutableResolver.NOT_FOUND_MESSAGE)

        // Through the RunConfiguration interface: CMakeAppRunConfiguration's own getType() override
        // was removed in 2026.2.
        val isCTest = CovdbgCoverageRunner.isCTest((configuration as RunConfiguration).type.id)
        if (isCTest) {
            refuseCovdbgTooOldForCTest(covdbg)
        }

        // CTest's command line is ctest.exe, which has no symbols and starts the tests itself: follow
        // it into them, and name the database after the configuration ("All CTests") rather than
        // after ctest.exe, so two CTest configurations do not overwrite each other.
        val plan = CovdbgRunPlan.create(
            project,
            target = cmdLine.exePath,
            workingDirectory = cmdLine.workingDirectory?.toString(),
            configOverride = "",
            followChildren = isCTest || CovdbgSettings.getInstance(project).state.followChildren,
            mode = "ONE_SHOT",
            outputName = if (isCTest) configuration.name else File(cmdLine.exePath).nameWithoutExtension
        )
        plan.prepareOutput()

        CovdbgCommandLinePatch.apply(cmdLine, covdbg, plan::arguments)
        LOG.info("Running coverage: ${cmdLine.commandLineString}")

        context.putUserData(PLAN, plan)
        context.putUserData(PTY_COMMAND_LINE, cmdLine is PtyCommandLine)
    }

    override fun attachToProcess(
        configuration: OCRunConfiguration<*, *>,
        handler: ProcessHandler,
        environment: CidrToolEnvironment,
        runnerSettings: RunnerSettings?,
        runnerId: String,
        context: ConfigurationExtensionContext
    ) {
        if (runnerId != CovdbgCoverageRunner.RUNNER_ID) return
        val plan = context.getUserData(PLAN) ?: return

        val usesPty = context.getUserData(PTY_COMMAND_LINE) == true ||
            (handler as? BaseProcessHandler<*>)?.process is PtyProcess
        handler.addProcessListener(CovdbgRunMonitor(configuration.project, plan, usesPty))
        handler.putUserData(ATTACHED, true)
    }

    /**
     * An older covdbg refuses ctest.exe outright ("no functions to track"), which would read as a filter
     * problem. Say what is actually wrong instead. A version that cannot be read runs anyway, for the
     * reasons [CovdbgCoverageRunner] gives for its soft version check.
     */
    private fun refuseCovdbgTooOldForCTest(covdbg: String) {
        val info = CovdbgRuntimeInfoService.getInstance()
        // Running covdbg to ask is not allowed on the EDT; the version an earlier probe found is.
        val version = if (ApplicationManager.getApplication().isDispatchThread) {
            info.cachedVersion(covdbg)
        } else {
            info.version(covdbg)
        } ?: return
        if (version < CovdbgVersion.CTEST_MINIMUM) {
            throw ExecutionException(CovdbgVersion.ctestTooOldMessage(version))
        }
    }

    companion object {
        /** Set on a process handler once covdbg is in front of its target and its output is watched. */
        val ATTACHED: Key<Boolean> = Key.create("covdbg.attached")

        private val PLAN: Key<CovdbgRunPlan> = Key.create("covdbg.plan")
        private val PTY_COMMAND_LINE: Key<Boolean> = Key.create("covdbg.ptyCommandLine")

        const val NOT_MSVC_MESSAGE = "covdbg only supports Visual Studio (MSVC) builds. Select a CMake " +
            "profile that uses a Visual Studio toolchain."
    }
}
