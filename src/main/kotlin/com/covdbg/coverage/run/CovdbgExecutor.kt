package com.covdbg.coverage.run

import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.Icon

/**
 * Custom executor that adds a "Run 'X' with Covdbg" entry to the run-configuration
 * dropdown menu (the three-dot / kebab menu next to the Run button in the toolbar).
 *
 * Registering an Executor is the IntelliJ-platform way to add entries to that menu.
 * A compatible ProgramRunner (CovdbgCoverageRunner) handles the actual execution.
 */
class CovdbgExecutor : Executor() {

    companion object {
        const val EXECUTOR_ID = "CovdbgCoverageExecutor"
    }

    override fun getToolWindowId(): String = ToolWindowId.RUN

    override fun getToolWindowIcon(): Icon = AllIcons.General.RunWithCoverage

    override fun getIcon(): Icon = AllIcons.General.RunWithCoverage

    override fun getDisabledIcon(): Icon = AllIcons.Process.Stop

    override fun getDescription(): String = "Run with covdbg code coverage"

    @Suppress("DialogTitleCapitalization")
    override fun getActionName(): String = "covdbg"

    override fun getId(): String = EXECUTOR_ID

    @Suppress("DialogTitleCapitalization")
    override fun getStartActionText(): String = "Run with covdbg"

    override fun getStartActionText(configurationName: String): String {
        @Suppress("DialogTitleCapitalization")
        if (configurationName.isBlank()) return "Run with covdbg"
        val shortened = shortenNameIfNeeded(configurationName)
        return TextWithMnemonic.parse("Run")
            .append(" '$shortened' with covdbg")
            .toString()
    }

    override fun getContextActionId(): String = "RunCovdbgCoverage"

    override fun getHelpId(): String? = null

    /** covdbg only works on Windows. */
    override fun isApplicable(project: Project): Boolean = SystemInfo.isWindows
}

