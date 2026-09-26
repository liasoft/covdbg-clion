package com.covdbg.coverage.run

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which configuration types "Run with covdbg" is offered for. */
class CovdbgCoverageRunnerTest {

    @Test
    fun `ctest is not offered - covdbg would measure ctest exe instead of the tests`() {
        // CLion's id for CTest, the same in 2025.3 and 2026.2.
        assertFalse(CovdbgCoverageRunner.isSupportedType("CTestRunConfiguration"))
    }

    @Test
    fun `every other configuration type is offered`() {
        assertTrue(CovdbgCoverageRunner.isSupportedType("SomeApplicationRunConfiguration"))
    }
}
