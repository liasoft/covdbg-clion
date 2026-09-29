package com.covdbg.coverage.config

import com.covdbg.coverage.CovdbgEditors
import com.covdbg.coverage.CovdbgNotifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/** Creates the starter `.covdbg.yaml` in the project root and opens it. */
object CovdbgConfigWriter {

    private val LOG = Logger.getInstance(CovdbgConfigWriter::class.java)

    /**
     * Never overwrites: an existing configuration is the user's work, so it is opened instead.
     *
     * Safe to call from a notification action or a menu action; it schedules its own write action.
     */
    fun createAndOpen(project: Project) {
        val basePath = project.basePath
        if (basePath == null) {
            CovdbgNotifications.error(project, "Cannot determine the project root directory.")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val existing = File(basePath, CovdbgConfigTemplate.FILE_NAME)
            if (existing.isFile) {
                CovdbgEditors.open(project, existing)
                CovdbgNotifications.info(
                    project,
                    "${CovdbgConfigTemplate.FILE_NAME} already exists; opened it instead."
                )
                return@invokeLater
            }

            val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath)
            if (root == null) {
                CovdbgNotifications.error(project, "Cannot access the project root directory.")
                return@invokeLater
            }

            try {
                val created = WriteCommandAction.writeCommandAction(project)
                    .withName("Create ${CovdbgConfigTemplate.FILE_NAME}")
                    .compute<VirtualFile, Exception> {
                        val file = root.findChild(CovdbgConfigTemplate.FILE_NAME)
                            ?: root.createChildData(this, CovdbgConfigTemplate.FILE_NAME)
                        VfsUtil.saveText(file, CovdbgConfigTemplate.starter())
                        file
                    }
                CovdbgEditors.open(project, created)
                CovdbgNotifications.info(
                    project,
                    "Created ${created.name}. Adjust the include and exclude patterns for this project, " +
                        "then commit it."
                )
            } catch (e: Exception) {
                LOG.warn("Could not create ${CovdbgConfigTemplate.FILE_NAME}", e)
                CovdbgNotifications.error(
                    project,
                    "Could not create ${CovdbgConfigTemplate.FILE_NAME}: ${CovdbgNotifications.escape(e.message.orEmpty())}"
                )
            }
        }
    }
}
