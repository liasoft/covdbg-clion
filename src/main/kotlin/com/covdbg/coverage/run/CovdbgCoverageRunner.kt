package com.covdbg.coverage.run

import com.covdbg.coverage.CovdbgNotifications
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.workspace.OCRunConfiguration

/**
 * Runs a CLion run configuration under covdbg: "Run 'X' with covdbg".
 *
 * It runs the configuration itself, the way CLion's Run does - the same state, the same console, the
 * test tree for test configurations. covdbg is put in front of the target by
 * [CovdbgRunConfigurationExtension], which CLion calls with this runner's id while it builds the
 * command line.
 *
 * covdbg only supports MSVC (Visual Studio) builds. The executor is hidden when the selected CMake
 * profile is recognisably something else (WSL, MinGW, ...); the extension refuses a run whose
 * toolchain is not MSVC.
 */
class CovdbgCoverageRunner : GenericProgramRunner<RunnerSettings>() {

    companion object {
        private val LOG = Logger.getInstance(CovdbgCoverageRunner::class.java)

        const val RUNNER_ID = "CovdbgCoverageRunner"

        private val NON_MSVC_MARKERS = listOf("wsl", "mingw", "cygwin")

        /**
         * CLion's CTest configuration, "All CTests" among them. Matched by type id: the class is
         * internal in 2026.2.
         *
         * Its command line is `ctest.exe`, which starts the test binaries itself and has no symbols of
         * its own. [CovdbgRunConfigurationExtension] runs it with `--follow-children`, so covdbg runs
         * CTest unmeasured and measures the tests it starts.
         */
        internal const val CTEST_TYPE_ID = "CTestRunConfiguration"

        internal fun isCTest(typeId: String): Boolean = typeId == CTEST_TYPE_ID
    }

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != CovdbgExecutor.EXECUTOR_ID) return false
        if (profile !is OCRunConfiguration<*, *>) return false

        // Hide the executor when the active CMake profile is not Visual Studio / MSVC
        val target = ExecutionTargetManager.getInstance(profile.project).activeTarget
        val profileName = CmakeProfileName.of(profile, target)
        return profileName == null || !isNonMsvcProfileName(profileName)
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val project = environment.project

        val covdbgPath = CovdbgExecutableResolver.pathOrNotify(project) ?: return null

        warnIfCovdbgIsTooOld(project, covdbgPath)

        // CLion's own state for the configuration: it builds the command line, lets the extension put
        // covdbg in front of the target, and starts the process.
        val result = state.execute(environment.executor, this) ?: return null

        // A configuration CLion launches some other way never reaches the extension, and its target
        // is now running without covdbg. Stop it rather than let it pass for a coverage run.
        val handler = result.processHandler
        if (handler?.getUserData(CovdbgRunConfigurationExtension.ATTACHED) != true) {
            handler?.destroyProcess()
            val type = environment.runProfile.javaClass.name
            LOG.warn("covdbg was not attached to $type; the run was stopped")
            CovdbgNotifications.error(
                project,
                "This run configuration ($type) is not launched in a way covdbg can instrument, so the " +
                    "run was stopped. Run the target through a covdbg run configuration instead.",
                "covdbg could not run this configuration"
            )
        }

        return RunContentBuilder(result, environment).showRunContent(environment.contentToReuse)
    }

    /**
     * Warns once per session about a covdbg older than this plugin supports, and runs anyway.
     *
     * A soft gate on purpose. The probe can fail for reasons that have nothing to do with the
     * version - a wrapper script, antivirus interception, a slow path - and blocking a perfectly
     * good binary would be worse than the failure it prevents. When covdbg really is too old it
     * rejects an option and says so, which the run reports as a stale-CLI error.
     */
    private fun warnIfCovdbgIsTooOld(project: Project, exePath: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val info = CovdbgRuntimeInfoService.getInstance()
            val version = info.version(exePath) ?: return@executeOnPooledThread
            if (!info.shouldWarnAboutVersion(exePath, version)) return@executeOnPooledThread
            if (project.isDisposed) return@executeOnPooledThread

            CovdbgNotifications.warn(
                project,
                "${CovdbgVersion.tooOldMessage(version)} Runs may fail because options this plugin " +
                    "passes do not exist in $version.",
                "covdbg $version is older than this plugin supports"
            )
        }
    }

    /**
     * True when the profile name names a toolchain covdbg cannot instrument.
     *
     * Only rejection is expressed, because only rejection is decidable from a display string: an
     * unnamed or custom profile is allowed rather than blocked.
     *
     * `CPPEnvironment.isMSVC()` is the real answer, but `canRun` has no environment to ask, and the
     * route from a configuration to one - `CMakeWorkspace.getProfileInfoFor(...)` - is declared on a
     * type that is internal in 2026.2. So the executor is hidden by name here, and the extension,
     * which is handed the `CPPEnvironment` at launch, makes the exact check.
     */
    private fun isNonMsvcProfileName(name: String): Boolean {
        val lower = name.lowercase()
        return NON_MSVC_MARKERS.any { it in lower }
    }
}
