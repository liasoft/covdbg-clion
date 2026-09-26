package com.covdbg.coverage.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Which configuration a run uses, and whether the plugin must name it with `--config`. The case that
 * matters: CLion runs CMake tests in the build tree, where covdbg's own search (working directory,
 * then beside the target) never reaches a `.covdbg.yaml` at the repository root.
 */
class CovdbgConfigLocatorTest {

    private val noEnv: (String) -> String? = { null }

    private fun layout(root: File): Pair<String, String> {
        val testsDir = File(root, "cmake-build-debug/tests").apply { mkdirs() }
        return testsDir.path to File(testsDir, "tests.exe").path
    }

    @Test
    fun `a build-tree run passes the project root's config`(@TempDir root: File) {
        val rootConfig = File(root, CovdbgConfigTemplate.FILE_NAME).apply { writeText("x") }
        val (workDir, target) = layout(root)

        val resolution = CovdbgConfigLocator.resolve(null, root.path, workDir, target, noEnv)
        assertEquals(rootConfig, resolution.file)
        assertEquals(rootConfig.absolutePath, resolution.configArgument)
    }

    @Test
    fun `a config covdbg finds itself is not passed`(@TempDir root: File) {
        File(root, CovdbgConfigTemplate.FILE_NAME).writeText("root")
        val (workDir, target) = layout(root)
        val beside = File(workDir, CovdbgConfigTemplate.FILE_NAME).apply { writeText("build tree") }

        val resolution = CovdbgConfigLocator.resolve(null, root.path, workDir, target, noEnv)
        assertEquals(beside, resolution.file)
        assertNull(resolution.configArgument)
    }

    @Test
    fun `COVDBG_CONFIG wins over the project root and is not passed`(@TempDir root: File) {
        File(root, CovdbgConfigTemplate.FILE_NAME).writeText("root")
        val fromEnv = File(root, "elsewhere.yaml").apply { writeText("env") }
        val (workDir, target) = layout(root)
        val env: (String) -> String? = { if (it == CovdbgConfigLocator.CONFIG_ENV) fromEnv.path else null }

        val resolution = CovdbgConfigLocator.resolve(null, root.path, workDir, target, env)
        assertEquals(fromEnv, resolution.file)
        assertNull(resolution.configArgument)
    }

    @Test
    fun `an explicit config is passed as it is`(@TempDir root: File) {
        val (workDir, target) = layout(root)

        val resolution = CovdbgConfigLocator.resolve("""C:\cfg\my.yaml""", root.path, workDir, target, noEnv)
        assertEquals("""C:\cfg\my.yaml""", resolution.configArgument)
    }

    @Test
    fun `nothing anywhere resolves to nothing`(@TempDir root: File) {
        val (workDir, target) = layout(root)

        val resolution = CovdbgConfigLocator.resolve(null, root.path, workDir, target, noEnv)
        assertNull(resolution.file)
        assertNull(resolution.configArgument)
    }
}
