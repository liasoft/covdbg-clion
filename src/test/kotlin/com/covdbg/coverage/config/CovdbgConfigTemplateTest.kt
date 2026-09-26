package com.covdbg.coverage.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CovdbgConfigTemplateTest {

    private val starter = CovdbgConfigTemplate.starter()

    @Test
    fun `the resource is present and non-trivial`() {
        assertTrue(starter.lines().size > 50, "the starter config should be the full annotated template")
    }

    @Test
    fun `declares the keys covdbg requires`() {
        assertTrue(starter.contains("version: 1"))
        assertTrue(starter.contains("source_root:"))
        assertTrue(starter.contains("coverage:"))
        assertTrue(starter.contains("include:"))
        assertTrue(starter.contains("exclude:"))
    }

    @Test
    fun `excludes fetched dependencies in any build directory, not just build`() {
        // CLion builds into cmake-build-*, so a build/-only pattern let FetchContent sources through.
        assertTrue(starter.contains("""- "**/_deps/**""""))
        assertTrue(!starter.contains("""- "build/**/_deps/**""""))
    }

    @Test
    fun `excludes the toolchain sources that otherwise swamp a report`() {
        listOf(
            """- "**/Windows Kits/**"""",
            """- "**/VC/Tools/MSVC/**"""",
            """- "**/stl/inc/**"""",
            """- "**/minkernel/crts/ucrt/**""""
        ).forEach { pattern ->
            assertTrue(starter.contains(pattern), "starter config should exclude $pattern")
        }
    }

    @Test
    fun `documents baselines without enabling one`() {
        // A baseline costs a full binary analysis, so the starter explains it and leaves it off.
        assertTrue(starter.contains("# baseline:"), "baseline should be documented")
        assertFalse(
            starter.lines().any { it.trimStart().startsWith("baseline:") },
            "the starter must not enable a baseline"
        )
    }

    @Test
    fun `is indented with spaces only`() {
        val tabbed = starter.lines().filter { it.contains('\t') }
        assertTrue(tabbed.isEmpty(), "YAML must not contain tabs, found: $tabbed")
    }

    @Test
    fun `names the file covdbg looks for`() {
        assertTrue(CovdbgConfigTemplate.FILE_NAME == ".covdbg.yaml")
    }
}
