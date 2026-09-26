package com.covdbg.coverage.engine

import com.intellij.openapi.util.io.FileUtil

/**
 * Maps a source path recorded in a `.covdb` to the local file the editor has open.
 *
 * The database records absolute paths from the machine that ran covdbg. They are used as they are
 * when that file exists here; otherwise a path under the database's `source_root` is re-rooted at the
 * project; failing both, a file name found exactly once in the project is taken to be the same file,
 * which covers a checkout moved elsewhere.
 *
 * The answer must be the local file's `VirtualFile.path` exactly, because that is the name the
 * platform looks coverage up by for an open editor.
 *
 * @param sourceRoot `metadata.source_root` of the database, if any.
 * @param projectRoot The project's base path.
 * @param existingFile The local file's canonical path when a file exists at the given path, else null.
 * @param projectFilesNamed Canonical paths of the project's files with the given name.
 */
class CovdbPathMapper(
    sourceRoot: String?,
    projectRoot: String?,
    private val existingFile: (String) -> String?,
    private val projectFilesNamed: (String) -> Collection<String>
) {
    private val sourceRoot = sourceRoot?.let { normalized(it) }?.takeIf { it.isNotEmpty() }
    private val projectRoot = projectRoot?.let { normalized(it) }?.takeIf { it.isNotEmpty() }

    /**
     * The local path for [databasePath]; when no local file can be found for it, the database path
     * itself with forward slashes, so its coverage is still keyed consistently.
     */
    fun map(databasePath: String): String = find(databasePath) ?: normalized(databasePath)

    /** The local path for [databasePath], or null when no local file can be found for it. */
    fun find(databasePath: String): String? {
        val path = normalized(databasePath)
        existingFile(path)?.let { return it }
        reRooted(path)?.let { local -> existingFile(local)?.let { return it } }
        val name = path.substringAfterLast('/')
        return projectFilesNamed(name).singleOrNull()
    }

    private fun reRooted(path: String): String? {
        val root = sourceRoot ?: return null
        val target = projectRoot ?: return null
        if (!path.startsWith("$root/", ignoreCase = true)) return null
        return target + path.substring(root.length)
    }

    private fun normalized(path: String): String = FileUtil.toSystemIndependentName(path).trimEnd('/')
}
