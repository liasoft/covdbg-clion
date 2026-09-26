package com.covdbg.coverage.engine

import com.intellij.rt.coverage.data.LineCoverage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineStatusTest {

    @Test
    fun `every block hit is full`() {
        assertEquals(LineCoverage.FULL, LineStatus.of(executionCount = 7, blocks = 2, blocksHit = 2))
    }

    @Test
    fun `some blocks hit is partial, however high the summed count`() {
        // The case the execution count alone gets wrong: a hit block and a skipped branch.
        assertEquals(LineCoverage.PARTIAL, LineStatus.of(executionCount = 1000, blocks = 3, blocksHit = 1))
    }

    @Test
    fun `no block hit is none`() {
        assertEquals(LineCoverage.NONE, LineStatus.of(executionCount = 0, blocks = 2, blocksHit = 0))
    }

    @Test
    fun `without a block mapping the execution count decides`() {
        assertEquals(LineCoverage.FULL, LineStatus.of(executionCount = 3, blocks = 0, blocksHit = 0))
        assertEquals(LineCoverage.NONE, LineStatus.of(executionCount = 0, blocks = 0, blocksHit = 0))
    }
}
