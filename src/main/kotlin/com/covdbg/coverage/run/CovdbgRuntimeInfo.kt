package com.covdbg.coverage.run

import com.covdbg.coverage.seats.CovdbgCli
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A covdbg version, as reported by `covdbg --version`. */
data class CovdbgVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** As covdbg printed it, which may carry prerelease or build metadata. */
    val raw: String = "$major.$minor.$patch"
) : Comparable<CovdbgVersion> {

    override fun compareTo(other: CovdbgVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = raw

    companion object {
        /** The oldest covdbg this plugin speaks to. */
        val MINIMUM_SUPPORTED = CovdbgVersion(1, 3, 0)

        /** One phrasing of the minimum, so the three places that mention it cannot disagree. */
        val REQUIREMENT = "This plugin needs covdbg $MINIMUM_SUPPORTED or newer"

        fun tooOldMessage(found: CovdbgVersion) =
            "$REQUIREMENT. Found $found."

        /**
         * The oldest covdbg that can measure a CTest run: ctest.exe has no symbols, and covdbg only
         * runs a root like that unmeasured, following the test binaries it starts, from 1.5.0.
         */
        val CTEST_MINIMUM = CovdbgVersion(1, 5, 0)

        fun ctestTooOldMessage(found: CovdbgVersion) =
            "Coverage for CTest run configurations needs covdbg $CTEST_MINIMUM or newer. Found $found."
    }
}

object CovdbgVersionParser {

    // "covdbg 1.3.0", "covdbg 1.3.0-rc.1+42", or a bare "1.3.0".
    private val VERSION = Regex("""(\d+)\.(\d+)\.(\d+)""")

    /** Parses the first line that carries a version triple, or null if none does. */
    fun parse(output: String): CovdbgVersion? {
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = VERSION.find(trimmed) ?: continue
            val (major, minor, patch) = match.destructured
            return CovdbgVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt(),
                trimmed.removePrefix("covdbg").trim().ifBlank { trimmed }
            )
        }
        return null
    }
}

/**
 * Probes and caches `covdbg --version`.
 *
 * The cache is keyed on the executable's path, size and timestamp rather than the path alone, so
 * upgrading covdbg in place — exactly the situation this probe exists to notice — invalidates it.
 */
@Service(Service.Level.APP)
class CovdbgRuntimeInfoService {

    private val cache = ConcurrentHashMap<String, CovdbgVersion>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** Never call on the EDT: this runs covdbg. Returns null when the probe fails for any reason. */
    fun version(exePath: String): CovdbgVersion? {
        if (exePath.isBlank()) return null
        val key = cacheKey(exePath) ?: return null
        cache[key]?.let { return it }

        val result = CovdbgCli.capture(exePath, CovdbgArgs.version(), null, PROBE_TIMEOUT_MS)
        val version = CovdbgVersionParser.parse(result.stdout)
            ?: CovdbgVersionParser.parse(result.stderr)
        if (version == null) {
            LOG.debug("Could not read a version from `covdbg --version` at $exePath")
            return null
        }
        cache[key] = version
        return version
    }

    /** The version an earlier probe found, without running covdbg; safe on the EDT. */
    fun cachedVersion(exePath: String): CovdbgVersion? =
        cacheKey(exePath)?.let { cache[it] }

    /**
     * True the first time a known-too-old covdbg is seen for this executable, so the warning is
     * shown once per IDE session rather than on every run.
     */
    fun shouldWarnAboutVersion(exePath: String, version: CovdbgVersion): Boolean {
        if (version >= CovdbgVersion.MINIMUM_SUPPORTED) return false
        val key = cacheKey(exePath) ?: exePath
        return warned.add(key)
    }

    private fun cacheKey(exePath: String): String? {
        val file = File(exePath)
        if (!file.isFile) return null
        return "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }

    companion object {
        private val LOG = Logger.getInstance(CovdbgRuntimeInfoService::class.java)
        private const val PROBE_TIMEOUT_MS = 2_000

        fun getInstance(): CovdbgRuntimeInfoService =
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(CovdbgRuntimeInfoService::class.java)
    }
}
