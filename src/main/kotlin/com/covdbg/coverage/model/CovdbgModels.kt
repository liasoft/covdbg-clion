package com.covdbg.coverage.model

/**
 * Mirrors Database::CoverageSummary from CoverageDatabase.h
 */
data class CoverageSummary(
    val totalBasicBlocks: Int = 0,
    val coveredBasicBlocks: Int = 0,
    val totalFunctions: Int = 0,
    val coveredFunctions: Int = 0,
    val totalLines: Int = 0,
    val coveredLines: Int = 0
) {
    val blockCoveragePercent: Float
        get() = percent(coveredBasicBlocks, totalBasicBlocks)
    val lineCoveragePercent: Float
        get() = percent(coveredLines, totalLines)
    val functionCoveragePercent: Float
        get() = percent(coveredFunctions, totalFunctions)
}

private fun percent(covered: Int, total: Int): Float =
    if (total > 0) 100f * covered / total else 0f

/**
 * One executable line from the line_coverage table (non-executable lines are not read), with the
 * basic blocks mapped to it.
 */
data class LineCoverageRecord(
    val filePath: String,
    val lineNumber: Int,
    /** Sum of the hits of the line's blocks - not the number of times the line ran. */
    val executionCount: Long,
    /** Basic blocks mapped to the line; zero when the database has no mapping for it. */
    val blocks: Int = 0,
    /** How many of [blocks] were executed at least once. */
    val blocksHit: Int = 0
)
