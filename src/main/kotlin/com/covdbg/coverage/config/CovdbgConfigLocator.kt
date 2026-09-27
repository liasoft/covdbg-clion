package com.covdbg.coverage.config

import java.io.File

/**
 * Finds the `.covdbg.yaml` a run will use.
 *
 * covdbg 1.3.0 requires a configuration file: without `--config`, and with none discoverable, it
 * refuses the run. It looks in the working directory and beside the target executable.
 *
 * This mirrors that search for pre-flight warnings only. covdbg remains the authority — a mismatch
 * here must never block a run, because an unusual layout is the plugin's problem to tolerate, not
 * the user's problem to fix.
 */
object CovdbgConfigLocator {

    const val CONFIG_ENV = "COVDBG_CONFIG"

    /** The explicit config path a run will pass, or null when covdbg should discover one. */
    fun resolveConfigured(runConfigPath: String, settingsPath: String): String? =
        runConfigPath.ifBlank { settingsPath }.ifBlank { null }

    /**
     * The first configuration covdbg would find, or null when it would refuse the run.
     *
     * COVDBG_CONFIG comes first, as it does for covdbg itself - otherwise this reports "no config"
     * for an environment where the run would have worked, the same false negative the sign-in status
     * takes care to avoid for COVDBG_PROJECT_TOKEN.
     */
    fun discover(
        workDir: String?,
        targetExe: String?,
        env: (String) -> String? = System::getenv
    ): File? {
        env(CONFIG_ENV)?.takeIf { it.isNotBlank() }?.let { fromEnv ->
            return File(fromEnv).takeIf { it.isFile }
        }
        return candidates(workDir, targetExe).firstOrNull { it.isFile }
    }

    /** The configuration a run uses, and whether the plugin has to name it with `--config`. */
    data class Resolution(
        /** The file covdbg will read, or null when it will refuse the run. */
        val file: File?,
        /** The path to pass as `--config`, or null to let covdbg discover [file] itself. */
        val configArgument: String?
    )

    /**
     * Resolves the configuration for a run, running covdbg's own search once.
     *
     * An explicit path wins. A relative one is taken from the project root, where the user sees it,
     * and passed absolute: covdbg would resolve it against the working directory, which for a CMake
     * target is the build tree. A configured file that does not exist is still passed - covdbg then
     * says so - but [Resolution.file] is null, so the pre-flight check can warn about it.
     *
     * Otherwise covdbg discovers one, and when its search would come up empty the project's own
     * `.covdbg.yaml` is passed with `--config`: CLion runs CMake targets and tests in the build tree
     * (`cmake-build-debug/tests`, say), which is also where the binary is, so covdbg's search never
     * reaches the file at the repository root.
     */
    fun resolve(
        configured: String?,
        projectRoot: String,
        workDir: String?,
        targetExe: String?,
        env: (String) -> String? = System::getenv
    ): Resolution {
        if (configured != null) {
            val file = File(configured).let { if (it.isAbsolute) it else File(projectRoot, configured) }
            return Resolution(file.takeIf { it.isFile }, file.absolutePath)
        }
        discover(workDir, targetExe, env)?.let { return Resolution(it, null) }
        val atRoot = File(projectRoot, CovdbgConfigTemplate.FILE_NAME).takeIf { it.isFile }
        return Resolution(atRoot, atRoot?.absolutePath)
    }

    /** Every directory [resolve] looks in, in order, for a "none found" message. */
    fun searchedDirectories(projectRoot: String, workDir: String?, targetExe: String?): List<File> =
        (candidateDirectories(workDir, targetExe) + File(projectRoot))
            .distinctBy { it.absolutePath.lowercase() }

    /** Every directory covdbg would look in, in order. */
    fun candidateDirectories(workDir: String?, targetExe: String?): List<File> = buildList {
        if (!workDir.isNullOrBlank()) add(File(workDir))
        if (!targetExe.isNullOrBlank()) {
            File(targetExe).parentFile?.let { add(it) }
        }
    }.distinctBy { it.absolutePath.lowercase() }

    private fun candidates(workDir: String?, targetExe: String?): List<File> =
        candidateDirectories(workDir, targetExe).map { File(it, CovdbgConfigTemplate.FILE_NAME) }
}
