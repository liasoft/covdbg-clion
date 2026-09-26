package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CovdbgVersionParserTest {

    private fun parsed(output: String): CovdbgVersion =
        requireNotNull(CovdbgVersionParser.parse(output)) { "no version parsed from: $output" }

    @Test
    fun `parses the released form`() {
        val version = parsed("covdbg 1.3.0\n")
        assertEquals(1, version.major)
        assertEquals(3, version.minor)
        assertEquals(0, version.patch)
        assertEquals("1.3.0", version.raw)
    }

    @Test
    fun `parses prerelease and build metadata`() {
        val version = parsed("covdbg 1.4.0-rc.1+42")
        assertEquals(1, version.major)
        assertEquals(4, version.minor)
        assertEquals("1.4.0-rc.1+42", version.raw)
    }

    @Test
    fun `skips leading blank lines`() {
        val version = parsed("\n\ncovdbg 1.3.0")
        assertEquals(3, version.minor)
    }

    @Test
    fun `returns null when there is no version to read`() {
        assertNull(CovdbgVersionParser.parse(""))
        assertNull(CovdbgVersionParser.parse("'covdbg' is not recognized as an internal command"))
    }

    @Test
    fun `orders by major then minor then patch`() {
        val v120 = parsed("covdbg 1.2.0")
        val v130 = parsed("covdbg 1.3.0")
        val v131 = parsed("covdbg 1.3.1")
        val v200 = parsed("covdbg 2.0.0")

        assertTrue(v120 < v130)
        assertTrue(v130 < v131)
        assertTrue(v131 < v200)
    }

    @Test
    fun `1_2_0 is below the supported minimum and 1_3_0 is not`() {
        val v120 = parsed("covdbg 1.2.0")
        val v130 = parsed("covdbg 1.3.0")

        assertTrue(v120 < CovdbgVersion.MINIMUM_SUPPORTED)
        assertTrue(v130 >= CovdbgVersion.MINIMUM_SUPPORTED)
    }

    @Test
    fun `a prerelease of the minimum still compares as the minimum`() {
        // GitVersion stamps development builds as 1.3.0-debug; they carry the 1.3.0 CLI surface,
        // so they must not trip the too-old warning.
        val debug = parsed("covdbg 1.3.0-debug")
        assertTrue(debug >= CovdbgVersion.MINIMUM_SUPPORTED)
    }
}
