package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CovdbgArgsTest {

    private fun spec(
        configPath: String? = null,
        logLevel: String? = null,
        logFile: String? = null,
        symbolEngine: String? = null,
        followChildren: Boolean = false,
        targetArgs: List<String> = emptyList()
    ) = CovdbgArgs.RunSpec(
        configPath = configPath,
        outputPath = """C:\proj\.covdbg\tests.covdb""",
        mode = "ONE_SHOT",
        logLevel = logLevel,
        logFile = logFile,
        symbolEngine = symbolEngine,
        followChildren = followChildren,
        target = """C:\proj\build\tests.exe""",
        targetArgs = targetArgs
    )

    @Test
    fun `never emits options covdbg 1_3_0 removed`() {
        val removed = listOf("--appdata", "--license", "--license-file", "--fetch-license", "--demo", "--plugin-name", "--plugin-ver")
        val everything = CovdbgArgs.run(
            spec(configPath = "c.yaml", logLevel = "WARN", logFile = "l.log", symbolEngine = "native", followChildren = true)
        ) + CovdbgArgs.login() + CovdbgArgs.logout() + CovdbgArgs.whoami() + CovdbgArgs.version() +
            CovdbgArgs.convertHtml("a.covdb", "out", "t", "root")

        removed.forEach { option ->
            assertFalse(everything.contains(option), "$option must never be passed to covdbg 1.3.0")
        }
    }

    @Test
    fun `always passes an explicit output path`() {
        val args = CovdbgArgs.run(spec())
        val index = args.indexOf("--output")
        assertTrue(index >= 0, "--output must always be passed")
        assertEquals("""C:\proj\.covdbg\tests.covdb""", args[index + 1])
    }

    @Test
    fun `omits optional values that are blank or absent`() {
        val args = CovdbgArgs.run(spec(configPath = "  ", logLevel = "", logFile = null, symbolEngine = ""))
        assertFalse(args.contains("--config"))
        assertFalse(args.contains("--log-level"))
        assertFalse(args.contains("--log-file"))
        assertFalse(args.contains("--symbol-engine"))
    }

    @Test
    fun `includes optional values when supplied`() {
        val args = CovdbgArgs.run(
            spec(configPath = "cfg.yaml", logLevel = "DEBUG", logFile = "run.log", symbolEngine = "native")
        )
        assertEquals("cfg.yaml", args[args.indexOf("--config") + 1])
        assertEquals("DEBUG", args[args.indexOf("--log-level") + 1])
        assertEquals("run.log", args[args.indexOf("--log-file") + 1])
        assertEquals("native", args[args.indexOf("--symbol-engine") + 1])
    }

    @Test
    fun `follow children is a flag and only present when enabled`() {
        assertFalse(CovdbgArgs.run(spec(followChildren = false)).contains("--follow-children"))
        val args = CovdbgArgs.run(spec(followChildren = true))
        val index = args.indexOf("--follow-children")
        assertTrue(index >= 0)
        // A flag takes no value: the next token is the target, not an argument to the flag.
        assertEquals("""C:\proj\build\tests.exe""", args[index + 1])
    }

    @Test
    fun `target and its arguments come last and in order`() {
        val args = CovdbgArgs.run(spec(configPath = "cfg.yaml", targetArgs = listOf("--gtest_filter=A*", "-v")))
        val tail = args.takeLast(3)
        assertEquals(listOf("""C:\proj\build\tests.exe""", "--gtest_filter=A*", "-v"), tail)
    }

    @Test
    fun `a target argument spelled like a covdbg option is left after the target`() {
        // covdbg calls positionals_at_end(), so everything past the target belongs to the target
        // and the plugin must not hoist it or insert a separator.
        val target = """C:\proj\build\tests.exe"""
        val args = CovdbgArgs.run(spec(configPath = "ours.yaml", targetArgs = listOf("--config", "theirs.yaml")))

        // covdbg's own --config appears once, before the target; the target's copy stays behind it.
        val targetIndex = args.indexOf(target)
        assertEquals(1, args.take(targetIndex).count { it == "--config" })
        assertEquals("ours.yaml", args[args.indexOf("--config") + 1])
        assertEquals(listOf(target, "--config", "theirs.yaml"), args.takeLast(3))
    }

    @Test
    fun `subcommands are the bare covdbg verbs`() {
        assertEquals(listOf("login"), CovdbgArgs.login())
        assertEquals(listOf("logout"), CovdbgArgs.logout())
        assertEquals(listOf("whoami"), CovdbgArgs.whoami())
        assertEquals(listOf("--version"), CovdbgArgs.version())
    }

    @Test
    fun `html conversion names the format and a required output directory`() {
        val args = CovdbgArgs.convertHtml("in.covdb", """C:\out\html""", "covdbg", """C:\proj""")
        assertEquals("convert", args.first())
        assertEquals("HTML", args[args.indexOf("--format") + 1])
        assertEquals("""C:\out\html""", args[args.indexOf("--output") + 1])
        assertEquals("covdbg", args[args.indexOf("--html-title") + 1])
        assertEquals("""C:\proj""", args[args.indexOf("--html-source-root") + 1])
    }

    @Test
    fun `html conversion omits title and source root when absent`() {
        val args = CovdbgArgs.convertHtml("in.covdb", "out", null, "")
        assertFalse(args.contains("--html-title"))
        assertFalse(args.contains("--html-source-root"))
    }
}
