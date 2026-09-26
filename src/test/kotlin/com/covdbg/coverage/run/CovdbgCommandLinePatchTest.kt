package com.covdbg.coverage.run

import com.intellij.execution.configurations.GeneralCommandLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The patch that puts covdbg in front of a command line CLion built. Only the executable and the
 * parameters may change: everything else CLion set up has to reach the target unchanged.
 */
class CovdbgCommandLinePatchTest {

    private val covdbg = """C:\Tools\covdbg.exe"""

    private fun targetCommandLine() =
        GeneralCommandLine("""C:\b\app.exe""", "--gtest_filter=A.B", "x y")
            .withWorkingDirectory(Path.of("""C:\work"""))
            .withEnvironment(mapOf("FOO" to "bar"))

    private fun covdbgArgs(targetArgs: List<String>): List<String> =
        CovdbgArgs.run(
            CovdbgArgs.RunSpec(
                outputPath = """C:\proj\.covdbg\app.covdb""",
                target = """C:\b\app.exe""",
                targetArgs = targetArgs
            )
        )

    @Test
    fun `covdbg becomes the executable and the target follows its options`() {
        val cmd = targetCommandLine()
        CovdbgCommandLinePatch.apply(cmd, covdbg, ::covdbgArgs)

        assertEquals(covdbg, cmd.exePath)
        assertEquals(
            listOf(
                "--output", """C:\proj\.covdbg\app.covdb""",
                "--mode", "ONE_SHOT",
                """C:\b\app.exe""", "--gtest_filter=A.B", "x y"
            ),
            cmd.parametersList.list
        )
    }

    @Test
    fun `working directory and environment are left as the IDE set them`() {
        val cmd = targetCommandLine()
        CovdbgCommandLinePatch.apply(cmd, covdbg, ::covdbgArgs)

        assertEquals(Path.of("""C:\work"""), cmd.workingDirectory)
        assertEquals(mapOf("FOO" to "bar"), cmd.environment)
    }

    @Test
    fun `a target without arguments ends with the target`() {
        val cmd = GeneralCommandLine("""C:\b\app.exe""")
        CovdbgCommandLinePatch.apply(cmd, covdbg, ::covdbgArgs)

        assertEquals("""C:\b\app.exe""", cmd.parametersList.list.last())
    }
}
