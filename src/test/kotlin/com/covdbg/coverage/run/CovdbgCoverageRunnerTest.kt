package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which configurations are run through CTest, and so need covdbg to follow ctest.exe into the tests. */
class CovdbgCoverageRunnerTest {

    @Test
    fun `ctest is recognised by its type id`() {
        // CLion's id for CTest, "All CTests" included, the same in 2025.3 and 2026.2.
        assertTrue(CovdbgCoverageRunner.isCTest("CTestRunConfiguration"))
    }

    @Test
    fun `every other configuration type runs its target directly`() {
        assertFalse(CovdbgCoverageRunner.isCTest("CMakeRunConfiguration"))
        assertFalse(CovdbgCoverageRunner.isCTest("SomeApplicationRunConfiguration"))
    }
}
