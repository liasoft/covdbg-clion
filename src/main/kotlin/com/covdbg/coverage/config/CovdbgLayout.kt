package com.covdbg.coverage.config

import java.io.File

/**
 * Where covdbg keeps its working files.
 *
 * One place, because the same paths are otherwise derived independently by whoever needs them - and
 * the run that passes `--log-file` and the action that opens the log have to agree on the answer.
 */
object CovdbgLayout {

    /** covdbg's own working directory name, relative to wherever it is run. */
    const val DIR = ".covdbg"

    fun dir(root: String): File = File(root, DIR)

    /** covdbg's default log location, resolved against the directory it runs in. */
    fun logFile(workingDirectory: String): File = File(dir(workingDirectory), "Logs/covdbg.log")

    /** Per-target database, so two targets in one project do not overwrite each other. */
    fun covdbFor(root: String, targetExecutable: String): File =
        covdbNamed(root, File(targetExecutable).nameWithoutExtension)

    /**
     * A database named after something other than the target, such as a CTest configuration ("All
     * CTests"), whose target is always ctest.exe. Characters Windows does not allow in a file name
     * become `_`.
     */
    fun covdbNamed(root: String, name: String): File {
        val safe = name.replace(INVALID_FILE_NAME_CHARS, "_").trim().trimEnd('.').ifBlank { "coverage" }
        return File(dir(root), "$safe.covdb")
    }

    private val INVALID_FILE_NAME_CHARS = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")

    /** Output directory for a converted HTML report. */
    fun htmlDirFor(root: String, covdbPath: String): File =
        File(dir(root), "html/${File(covdbPath).nameWithoutExtension}")
}
