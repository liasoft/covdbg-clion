package com.covdbg.coverage.run

import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.CovdbgNotifications.openSettingsAction
import com.covdbg.coverage.settings.CovdbgSettings
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Where a covdbg executable came from. */
enum class CovdbgExecutableSource {
    /** The Covdbg path setting, which always wins. */
    SETTING,

    /** Found on PATH. */
    PATH,

    /** Found at a known installation location. */
    INSTALL
}

data class ResolvedCovdbg(val path: String, val source: CovdbgExecutableSource) {

    /** How to describe this to the user, e.g. in the settings panel. */
    fun describe(): String = when (source) {
        CovdbgExecutableSource.SETTING -> path
        CovdbgExecutableSource.PATH -> "$path (found on PATH)"
        CovdbgExecutableSource.INSTALL -> "$path (installed)"
    }
}

/**
 * Finds covdbg without making the user configure it.
 *
 * The setting is an override, not a requirement: most machines have covdbg on PATH because its
 * installer and the winget package put it there, so the common case needs no configuration at all.
 * The order matches the VS Code extension's, minus the portable runtime it bundles and this plugin
 * does not, so both IDEs find the same executable on the same machine.
 *
 * Results are cached briefly. Resolution is filesystem work, and it is asked for on paths that run
 * often - validating a run configuration on every keystroke, for one - where a dead PATH entry would
 * otherwise block the caller each time.
 *
 * The lookups are injected so the ordering can be tested without a real filesystem or environment.
 */
object CovdbgExecutableResolver {

    const val EXECUTABLE_NAME = "covdbg.exe"

    const val NOT_FOUND_TITLE = "covdbg was not found"

    /** Shown wherever a covdbg invocation cannot go ahead because there is nothing to invoke. */
    val NOT_FOUND_MESSAGE =
        "covdbg was not found on PATH or at a known install location. Install covdbg " +
            "${CovdbgVersion.MINIMUM_SUPPORTED} or newer, or set the Covdbg path in " +
            "Settings | Build, Execution, Deployment | Covdbg."

    /** The covdbg path, or null after telling the user it was not found and where to set it. */
    fun pathOrNotify(project: Project): String? =
        pathFor(project) ?: null.also {
            CovdbgNotifications.error(project, NOT_FOUND_MESSAGE, NOT_FOUND_TITLE)?.openSettingsAction()
        }

    fun resolve(
        configuredPath: String,
        findOnPath: () -> String? = ::findOnSystemPath,
        env: (String) -> String? = System::getenv,
        exists: (String) -> Boolean = { File(it).isFile }
    ): ResolvedCovdbg? {
        // An explicit setting is honoured even when it does not exist, so a typo is reported as the
        // broken path the user typed rather than silently replaced by something else. Quotes are
        // dropped: Explorer's "Copy as path" adds them, and no Windows path contains one.
        val configured = configuredPath.trim().removeSurrounding("\"").trim()
        if (configured.isNotEmpty()) {
            return ResolvedCovdbg(configured, CovdbgExecutableSource.SETTING)
        }

        findOnPath()?.let {
            return ResolvedCovdbg(it, CovdbgExecutableSource.PATH)
        }

        knownInstallPaths(env).firstOrNull(exists)?.let {
            return ResolvedCovdbg(it, CovdbgExecutableSource.INSTALL)
        }

        return null
    }

    /** Resolved covdbg for this project, or null when it cannot be found. Cheap to call repeatedly. */
    fun resolveFor(project: Project): ResolvedCovdbg? =
        cached(CovdbgSettings.getInstance(project).state.covdbgPath)

    /** Resolved path, or null when covdbg cannot be found. */
    fun pathFor(project: Project): String? = resolveFor(project)?.path

    /** Forgets cached lookups, so the next call sees a covdbg that was just installed or configured. */
    fun invalidate() = cache.clear()

    /** Where covdbg's installers put it, per-machine first and then per-user. */
    fun knownInstallPaths(env: (String) -> String? = System::getenv): List<String> = buildList {
        val programFiles = env("ProgramFiles") ?: """C:\Program Files"""
        val programFilesX86 = env("ProgramFiles(x86)") ?: """C:\Program Files (x86)"""
        add(File(programFiles, """Liasoft\covdbg\$EXECUTABLE_NAME""").path)
        add(File(programFilesX86, """Liasoft\covdbg\$EXECUTABLE_NAME""").path)
        env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { localAppData ->
            add(File(localAppData, """Programs\covdbg\$EXECUTABLE_NAME""").path)
        }
    }

    /**
     * The platform's PATH search rather than our own: it honours PATHEXT and the environment the IDE
     * was actually launched with, which the JVM's own PATH does not always match.
     */
    private fun findOnSystemPath(): String? =
        PathEnvironmentVariableUtil.findInPath(EXECUTABLE_NAME)?.takeIf { it.isFile }?.path

    private class Entry(val resolved: ResolvedCovdbg?, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    private fun cached(configuredPath: String): ResolvedCovdbg? {
        val now = System.nanoTime()
        cache[configuredPath]?.let { entry ->
            if (now - entry.at < CACHE_TTL_NANOS) return entry.resolved
        }
        val resolved = resolve(configuredPath)
        cache[configuredPath] = Entry(resolved, now)
        return resolved
    }

    /** Long enough to keep repaints off the filesystem, short enough that installing covdbg is noticed. */
    private val CACHE_TTL_NANOS = java.time.Duration.ofSeconds(10).toNanos()
}
