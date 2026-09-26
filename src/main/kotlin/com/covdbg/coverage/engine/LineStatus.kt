package com.covdbg.coverage.engine

import com.intellij.rt.coverage.data.LineCoverage

/**
 * Whether a source line was covered, as the platform gutter paints it.
 *
 * A `.covdb` line's execution count is the *sum* of its basic blocks' hits, so a count above zero
 * does not mean the whole line ran - an `if` whose branch was never taken still counts. The blocks
 * mapped to the line say more: all hit is FULL, some hit is PARTIAL, none hit is NONE.
 */
object LineStatus {

    /**
     * @param blocks Basic blocks mapped to the line. Zero when the database has no mapping for it, in
     *   which case the execution count is all there is to go on.
     */
    fun of(executionCount: Long, blocks: Int, blocksHit: Int): Byte = when {
        blocks <= 0 -> if (executionCount > 0) LineCoverage.FULL else LineCoverage.NONE
        blocksHit <= 0 -> LineCoverage.NONE
        blocksHit < blocks -> LineCoverage.PARTIAL
        else -> LineCoverage.FULL
    }
}
