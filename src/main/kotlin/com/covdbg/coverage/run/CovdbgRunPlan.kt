package com.covdbg.coverage.run

import com.covdbg.coverage.config.CovdbgConfigLocator
import com.covdbg.coverage.config.CovdbgLayout
import com.covdbg.coverage.settings.CovdbgSettings
import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * One covdbg run: where it writes, what it reads, and the arguments that say so.
 *
 * Shared by both ways of starting a run - a CLion run configuration patched by
 * [CovdbgRunConfigurationExtension], and a covdbg run configuration through [CovdbgCommandLineState] -
 * so the paths passed to covdbg and the ones the notifications later open are derived once.
 */
class CovdbgRunPlan private constructor(
    val target: String,
    val workingDirectory: String,
    private val config: CovdbgConfigLocator.Resolution,
    /** Where the configuration was looked for, for a "none found" message. */
    val searchedDirectories: List<File>,
    /**
     * Per target. covdbg's own default is .covdbg/coverage.covdb relative to the working directory,
     * which would scatter databases into build trees and let two targets overwrite one file.
     */
    val outputCovdbPath: String,
    /** Always passed, so the log the failure notification offers to open is the one covdbg wrote. */
    val logFilePath: String,
    private val mode: String,
    private val followChildren: Boolean,
    private val logLevel: String,
    private val symbolEngine: String
) {

    /** `covdbg [options] <target> [args...]` */
    fun arguments(targetArgs: List<String>): List<String> =
        CovdbgArgs.run(
            CovdbgArgs.RunSpec(
                configPath = config.configArgument,
                outputPath = outputCovdbPath,
                mode = mode,
                logLevel = logLevel,
                logFile = logFilePath,
                symbolEngine = symbolEngine,
                followChildren = followChildren,
                target = target,
                targetArgs = targetArgs
            )
        )

    /** The configuration file covdbg will use, or null when it will refuse the run. */
    val configFile: File? get() = config.file

    /** The path passed with `--config`, or null when covdbg discovers the configuration itself. */
    val configArgument: String? get() = config.configArgument

    /** Creates the directory the database is written to; covdbg does not. */
    fun prepareOutput() {
        FileUtil.createParentDirs(File(outputCovdbPath))
    }

    companion object {

        /**
         * @param workingDirectory Where covdbg runs. It decides where covdbg looks for .covdbg.yaml and
         *   where its log goes; blank means the project root, never the IDE's own directory.
         * @param configOverride A run-configuration-level .covdbg.yaml, taking precedence over the
         *   project setting. Blank for none.
         * @param outputName What the database is named after: the target's name, unless the target
         *   says nothing about the run, as ctest.exe does not.
         */
        fun create(
            project: Project,
            target: String,
            workingDirectory: String?,
            configOverride: String,
            followChildren: Boolean,
            mode: String,
            outputName: String = File(target).nameWithoutExtension
        ): CovdbgRunPlan {
            // An ExecutionException reaches the user as the run's error; anything else would be
            // reported as an internal IDE error.
            val projectRoot = project.basePath
                ?: throw ExecutionException("covdbg needs a project directory to write coverage to.")
            val state = CovdbgSettings.getInstance(project).state
            val workDir = workingDirectory?.takeIf { it.isNotBlank() } ?: projectRoot
            return CovdbgRunPlan(
                target = target,
                workingDirectory = workDir,
                config = CovdbgConfigLocator.resolve(
                    CovdbgConfigLocator.resolveConfigured(configOverride, state.configPath),
                    projectRoot,
                    workDir,
                    target
                ),
                searchedDirectories = CovdbgConfigLocator.searchedDirectories(projectRoot, workDir, target),
                outputCovdbPath = CovdbgLayout.covdbNamed(projectRoot, outputName).absolutePath,
                // A relative setting is taken from the project root, like a relative config path: covdbg
                // would resolve it against the working directory, and "Open Log" against the IDE's.
                logFilePath = state.logFile.ifBlank { null }
                    ?.let { File(it).takeIf(File::isAbsolute) ?: File(projectRoot, it) }
                    ?.absolutePath
                    ?: CovdbgLayout.logFile(workDir).absolutePath,
                mode = mode,
                followChildren = followChildren,
                logLevel = state.logLevel,
                symbolEngine = state.symbolEngine
            )
        }
    }
}
