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
        File(dir(root), "${File(targetExecutable).nameWithoutExtension}.covdb")

    /** Output directory for a converted HTML report. */
    fun htmlDirFor(root: String, covdbPath: String): File =
        File(dir(root), "html/${File(covdbPath).nameWithoutExtension}")
}
