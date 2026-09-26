package com.covdbg.coverage.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton

/**
 * Wires a browse button to a file or folder chooser.
 *
 * One helper because the run-configuration editor and the settings panel had the same eight lines
 * each, and both spelled the descriptor as six positional booleans.
 */
internal fun TextFieldWithBrowseButton.chooseFile(
    project: Project,
    title: String,
    description: String,
    chooseFolder: Boolean = false
) {
    val descriptor = if (chooseFolder) {
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
    } else {
        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
    }
    descriptor.title = title
    descriptor.description = description
    addBrowseFolderListener(project, descriptor)
}
