package com.covdbg.coverage.db

import com.covdbg.coverage.model.*
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.sqlite.SQLiteConfig

/**
 * Reads .covdb files (SQLite databases) produced by covdbg.
 *
 * Schema reference: CoverageDatabase.cpp schema v7 (natural keys). Tables read: `metadata`
 * (source_root, schema_version), `functions`, `basic_blocks`, `source_locations` and `line_coverage`.
 */
class CovdbReader(covdbPath: String) : Closeable {

    private val connection: Connection

    init {
        Class.forName("org.sqlite.JDBC")
        val file = File(covdbPath)
        require(file.exists()) { "Coverage database not found: $covdbPath" }
        // Read-only through SQLiteConfig: a "?mode=ro" URL suffix would need the file: prefix.
        val config = SQLiteConfig()
        config.setReadOnly(true)
        connection = DriverManager.getConnection("jdbc:sqlite:$covdbPath", config.toProperties())
    }

    override fun close() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    fun getMetadata(key: String): String? =
        connection.prepareStatement("SELECT value FROM metadata WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    fun getSourceRoot(): String? = getMetadata("source_root")

    fun getSchemaVersion(): Int = getMetadata("schema_version")?.toIntOrNull() ?: 0

    // =========================================================================
    // Coverage Summary
    // =========================================================================

    /**
     * Totals for the run's notification, aggregated from the coverage tables.
     *
     * Deliberately not the `coverage_metrics` table, even though it is cheaper: gating prunes the
     * database to the ten most-hit files *after* those metrics were written, so on a gated report the
     * stored totals describe files that are no longer there. covdbg's own `CoverageSummaryBuilder.h`
     * aggregates the tables for the same reason.
     */
    fun getCoverageSummary(): CoverageSummary = CoverageSummary(
        totalBasicBlocks = queryInt("SELECT COUNT(*) FROM basic_blocks"),
        coveredBasicBlocks = queryInt("SELECT COUNT(*) FROM basic_blocks WHERE hit_count > 0"),
        totalFunctions = queryInt("SELECT COUNT(*) FROM functions"),
        // Covered functions: have at least one covered basic block
        coveredFunctions = queryInt(
            """
            SELECT COUNT(DISTINCT f.module_path || ':' || f.start_address)
            FROM functions f
            JOIN basic_blocks bb ON f.module_path = bb.module_path
                AND bb.function_start_address = f.start_address
            WHERE bb.hit_count > 0
            """
        ),
        totalLines = queryInt("SELECT COUNT(*) FROM line_coverage WHERE is_executable = 1"),
        coveredLines = queryInt(
            "SELECT COUNT(*) FROM line_coverage WHERE is_executable = 1 AND execution_count > 0"
        )
    )

    /** Single-value COUNT query, closed after use. */
    private fun queryInt(sql: String): Int =
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    // =========================================================================
    // Line-Level Coverage
    // =========================================================================

    /**
     * Every executable line in the database, with the basic blocks mapped to it.
     *
     * `execution_count` is the sum of the line's block hits, so on its own it cannot tell a line that
     * fully ran from one where a branch was never taken. `blocks` and `blocksHit` can: they come from
     * `source_locations`, which maps each block to the lines it covers. A line with no mapped blocks
     * reports zero for both.
     */
    fun getAllLineCoverage(): List<LineCoverageRecord> {
        val results = mutableListOf<LineCoverageRecord>()
        connection.prepareStatement(
            """
            SELECT lc.file_path, lc.line_number, lc.execution_count,
                   COUNT(bb.start_address) AS blocks,
                   COALESCE(SUM(bb.hit_count > 0), 0) AS blocks_hit
            FROM line_coverage lc
            LEFT JOIN source_locations sl
                   ON sl.file_path = lc.file_path AND sl.line_number = lc.line_number
            LEFT JOIN basic_blocks bb
                   ON bb.module_path = sl.module_path AND bb.start_address = sl.block_start_address
            WHERE lc.is_executable = 1 AND lc.line_number > 0
            GROUP BY lc.file_path, lc.line_number
            ORDER BY lc.file_path, lc.line_number
            """
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(
                        LineCoverageRecord(
                            filePath = rs.getString("file_path"),
                            lineNumber = rs.getInt("line_number"),
                            executionCount = rs.getLong("execution_count"),
                            blocks = rs.getInt("blocks"),
                            blocksHit = rs.getInt("blocks_hit")
                        )
                    )
                }
            }
        }
        return results
    }
}
