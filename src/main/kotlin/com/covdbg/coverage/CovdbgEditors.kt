package com.covdbg.coverage

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Opens files the plugin writes or points at, such as `.covdbg.yaml` or covdbg's log.
 *
 * Stateless on purpose: notification actions call it, and a notification lives in the event log
 * until it expires, so its action must not keep a run's state alive.
 */
object CovdbgEditors {

    /** False when there is no such file, or the project is gone. */
    fun open(project: Project, file: File): Boolean =
        open(project, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))

    fun open(project: Project, file: VirtualFile?): Boolean {
        if (file == null || project.isDisposed) return false
        FileEditorManager.getInstance(project).openFile(file, true)
        return true
    }
}
