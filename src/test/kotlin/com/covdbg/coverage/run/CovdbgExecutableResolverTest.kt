package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Covers the order the three sources are consulted in. Searching PATH itself is the platform's job
 * (`PathEnvironmentVariableUtil`, which also honours PATHEXT and the IDE's own environment), so it is
 * injected here rather than reimplemented and tested.
 */
class CovdbgExecutableResolverTest {

    private val onPath = """C:\tools\covdbg.exe"""
    private val installed = """C:\Program Files\Liasoft\covdbg\covdbg.exe"""

    private fun resolve(
        configured: String = "",
        foundOnPath: String? = null,
        environment: Map<String, String> = emptyMap(),
        vararg existing: String
    ): ResolvedCovdbg? {
        val present = existing.map { it.lowercase() }.toSet()
        return CovdbgExecutableResolver.resolve(
            configuredPath = configured,
            findOnPath = { foundOnPath },
            env = { environment[it] },
            exists = { it.lowercase() in present }
        )
    }

    private val programFiles = mapOf("ProgramFiles" to """C:\Program Files""")

    @Test
    fun `the setting wins over everything else`() {
        val resolved = resolve(
            configured = """D:\custom\covdbg.exe""",
            foundOnPath = onPath,
            environment = programFiles,
            existing = arrayOf(onPath, installed)
        )
        assertEquals(ResolvedCovdbg("""D:\custom\covdbg.exe""", CovdbgExecutableSource.SETTING), resolved)
    }

    @Test
    fun `a configured path is honoured even when it does not exist`() {
        // Silently substituting something else would hide the typo the user needs to see.
        val resolved = resolve(configured = """D:\typo\covdbg.exe""", foundOnPath = onPath, existing = arrayOf(onPath))
        assertEquals(CovdbgExecutableSource.SETTING, resolved?.source)
        assertEquals("""D:\typo\covdbg.exe""", resolved?.path)
    }

    @Test
    fun `a blank setting falls through to PATH`() {
        assertEquals(
            ResolvedCovdbg(onPath, CovdbgExecutableSource.PATH),
            resolve(configured = "   ", foundOnPath = onPath)
        )
    }

    @Test
    fun `PATH is preferred over an installed copy`() {
        val resolved = resolve(
            foundOnPath = onPath,
            environment = programFiles,
            existing = arrayOf(onPath, installed)
        )
        assertEquals(CovdbgExecutableSource.PATH, resolved?.source)
    }

    @Test
    fun `falls back to a known install location when PATH has nothing`() {
        val resolved = resolve(environment = programFiles, existing = arrayOf(installed))
        assertEquals(ResolvedCovdbg(installed, CovdbgExecutableSource.INSTALL), resolved)
    }

    @Test
    fun `finds a per-user installation under LOCALAPPDATA`() {
        val perUser = """C:\Users\dev\AppData\Local\Programs\covdbg\covdbg.exe"""
        val resolved = resolve(
            environment = mapOf("LOCALAPPDATA" to """C:\Users\dev\AppData\Local"""),
            existing = arrayOf(perUser)
        )
        assertEquals(ResolvedCovdbg(perUser, CovdbgExecutableSource.INSTALL), resolved)
    }

    @Test
    fun `returns null when covdbg is nowhere to be found`() {
        assertNull(resolve(environment = programFiles))
    }

    @Test
    fun `known install paths cover machine-wide and per-user installs`() {
        val paths = CovdbgExecutableResolver.knownInstallPaths(
            mapOf(
                "ProgramFiles" to """C:\Program Files""",
                "ProgramFiles(x86)" to """C:\Program Files (x86)""",
                "LOCALAPPDATA" to """C:\Users\dev\AppData\Local"""
            )::get
        )
        assertEquals(3, paths.size)
        assertTrue(paths.all { it.endsWith(CovdbgExecutableResolver.EXECUTABLE_NAME) })
        assertTrue(paths[0].startsWith("""C:\Program Files\"""))
        assertTrue(paths.last().contains("""AppData\Local\Programs"""))
    }

    @Test
    fun `known install paths fall back to conventional roots`() {
        val paths = CovdbgExecutableResolver.knownInstallPaths { null }
        // No LOCALAPPDATA means no per-user candidate, rather than a path with an empty segment.
        assertEquals(2, paths.size)
        assertTrue(paths.none { it.startsWith(File.separator) })
    }

    @Test
    fun `describe names the source for anything not explicitly configured`() {
        assertEquals(onPath, ResolvedCovdbg(onPath, CovdbgExecutableSource.SETTING).describe())
        assertTrue(ResolvedCovdbg(onPath, CovdbgExecutableSource.PATH).describe().contains("PATH"))
        assertTrue(ResolvedCovdbg(installed, CovdbgExecutableSource.INSTALL).describe().contains("installed"))
    }
}
