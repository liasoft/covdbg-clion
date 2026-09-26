package com.covdbg.coverage.run

import com.intellij.execution.configurations.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

class CovdbgConfigurationType : ConfigurationType {

    @Suppress("DialogTitleCapitalization")
    override fun getDisplayName(): String = "covdbg Coverage"
    override fun getConfigurationTypeDescription(): String =
        "Run an executable with covdbg coverage instrumentation"
    override fun getIcon(): Icon = AllIcons.General.RunWithCoverage
    override fun getId(): String = "COVDBG_RUN_CONFIGURATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
        arrayOf(CovdbgConfigurationFactory(this))
}

class CovdbgConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "COVDBG_FACTORY"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CovdbgRunConfiguration(project, this, "covdbg")

    override fun getName(): String = "covdbg Coverage"
}
