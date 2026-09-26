package com.covdbg.coverage.run

import com.covdbg.coverage.config.CovdbgConfigTemplate
import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element

class CovdbgRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    var targetExecutable: String = ""
    var targetArguments: String = ""
    var workingDirectory: String = ""
    var covdbgConfigPath: String = ""   // .covdbg.yaml override
    var mode: String = "ONE_SHOT"       // ONE_SHOT or PERSISTENT
    var followChildren: Boolean = false // Instrument processes the target spawns

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CovdbgSettingsEditor(project)

    override fun checkConfiguration() {
        if (targetExecutable.isBlank()) {
            throw RuntimeConfigurationError("Target executable is not specified")
        }
        if (CovdbgExecutableResolver.pathFor(project) == null) {
            @Suppress("DialogTitleCapitalization")
            throw RuntimeConfigurationWarning(CovdbgExecutableResolver.NOT_FOUND_MESSAGE)
        }

        // covdbg requires a configuration file and refuses the run without one. Warn rather than
        // error: this mirrors covdbg's own discovery, and an unusual layout must not be blocked by
        // the plugin guessing wrong. The plan is the one the run would use, so the two agree.
        if (project.basePath == null) return
        val plan = CovdbgRunPlan.create(
            project,
            target = targetExecutable,
            workingDirectory = CovdbgMacros.expand(workingDirectory, project, validation = true),
            configOverride = covdbgConfigPath,
            followChildren = followChildren,
            mode = mode
        )
        if (plan.configFile == null) {
            val looked = plan.searchedDirectories.joinToString(", ") { it.absolutePath }
            @Suppress("DialogTitleCapitalization")
            throw RuntimeConfigurationWarning(
                "No ${CovdbgConfigTemplate.FILE_NAME} found in $looked. covdbg will refuse the run."
            )
        }
    }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState {
        return CovdbgCommandLineState(environment, this)
    }

    // Persist configuration across IDE restarts
    override fun readExternal(element: Element) {
        super.readExternal(element)
        targetExecutable = element.getAttributeValue("target") ?: ""
        targetArguments = element.getAttributeValue("args") ?: ""
        workingDirectory = element.getAttributeValue("workDir") ?: ""
        covdbgConfigPath = element.getAttributeValue("configPath") ?: ""
        mode = element.getAttributeValue("mode") ?: "ONE_SHOT"
        followChildren = element.getAttributeValue("followChildren")?.toBoolean() ?: false
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("target", targetExecutable)
        element.setAttribute("args", targetArguments)
        element.setAttribute("workDir", workingDirectory)
        element.setAttribute("configPath", covdbgConfigPath)
        element.setAttribute("mode", mode)
        element.setAttribute("followChildren", followChildren.toString())
    }
}
