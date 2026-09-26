package com.covdbg.coverage.run

import com.covdbg.coverage.ui.chooseFile
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CovdbgSettingsEditor(private val project: Project) : SettingsEditor<CovdbgRunConfiguration>() {

    private val targetField = TextFieldWithBrowseButton()
    private val argsField = JBTextField()
    private val workingDirField = TextFieldWithBrowseButton()
    private val configPathField = TextFieldWithBrowseButton()
    private val modeCombo = ComboBox(arrayOf("ONE_SHOT", "PERSISTENT"))
    private val followChildrenCheckbox = JBCheckBox("Follow child processes")

    private val panel: JPanel = JPanel(BorderLayout())

    init {
        targetField.chooseFile(
            project,
            "Select Target Executable",
            "Choose the executable to run with covdbg."
        )
        workingDirField.chooseFile(
            project,
            "Select Working Directory",
            "Choose the working directory for the target executable.",
            chooseFolder = true
        )
        @Suppress("DialogTitleCapitalization")
        configPathField.chooseFile(
            project,
            "Select .covdbg.yaml File",
            "Choose a covdbg configuration file (optional)."
        )

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Target executable:", targetField)
            .addLabeledComponent("Arguments:", argsField)
            .addLabeledComponent("Working directory:", workingDirField)
            .addLabeledComponent("Config path:", configPathField)
            .addLabeledComponent("Mode:", modeCombo)
            .addComponent(followChildrenCheckbox)
            .panel

        panel.add(form, BorderLayout.NORTH)
    }

    override fun createEditor(): JComponent = panel

    override fun applyEditorTo(config: CovdbgRunConfiguration) {
        config.targetExecutable = targetField.text.trim()
        config.targetArguments = argsField.text.trim()
        config.workingDirectory = workingDirField.text.trim()
        config.covdbgConfigPath = configPathField.text.trim()
        config.mode = modeCombo.selectedItem?.toString() ?: "ONE_SHOT"
        config.followChildren = followChildrenCheckbox.isSelected
    }

    override fun resetEditorFrom(config: CovdbgRunConfiguration) {
        targetField.text = config.targetExecutable
        argsField.text = config.targetArguments
        workingDirField.text = config.workingDirectory
        configPathField.text = config.covdbgConfigPath
        modeCombo.selectedItem = config.mode
        followChildrenCheckbox.isSelected = config.followChildren
    }

}
