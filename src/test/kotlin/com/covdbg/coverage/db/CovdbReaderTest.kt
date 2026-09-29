package com.covdbg.coverage.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Reads a fixture database built here rather than a committed binary.
 *
 * A checked-in `.covdb` would rot invisibly when covdbg's schema moves, and it would sit under the
 * `/ide/` ignore rule where it is easy to forget. Building it in the test states the exact contract
 * [CovdbReader] depends on, which is the thing worth pinning.
 */
class CovdbReaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads schema version and source root from metadata`(@TempDir dir: Path) {
        val db = fixture(dir, "meta.covdb")
        CovdbReader(db).use { reader ->
            assertEquals(7, reader.getSchemaVersion())
            assertEquals("""C:\proj""", reader.getSourceRoot())
        }
    }

    @Test
    fun `summary ignores the metrics table, which gating leaves stale`() {
        // covdbg writes coverage_metrics before gating prunes the database to the ten most-hit files,
        // so those totals can describe files that are no longer present. The metrics here deliberately
        // disagree with the rows; the rows must win, or a gated report would show whole-program
        // headline numbers above a ten-file list.
        val db = fixture(dir = tempDir, name = "metrics.covdb") { connection ->
            metric(connection, "total_basic_blocks", "100")
            metric(connection, "covered_basic_blocks", "40")
            metric(connection, "total_functions", "10")
            metric(connection, "covered_functions", "5")
            metric(connection, "total_lines", "200")
            metric(connection, "covered_lines", "50")
        }

        CovdbReader(db).use { reader ->
            val summary = reader.getCoverageSummary()
            assertEquals(3, summary.totalBasicBlocks)
            assertEquals(2, summary.coveredBasicBlocks)
            assertEquals(4, summary.totalLines)
            assertEquals(2, summary.coveredLines)
        }
    }

    @Test
    fun `summary aggregates the coverage tables`(@TempDir dir: Path) {
        val db = fixture(dir, "fallback.covdb")
        CovdbReader(db).use { reader ->
            val summary = reader.getCoverageSummary()
            // The fixture has 3 blocks (2 hit), 2 functions (1 hit) and 4 executable lines (2 hit).
            assertEquals(3, summary.totalBasicBlocks)
            assertEquals(2, summary.coveredBasicBlocks)
            assertEquals(2, summary.totalFunctions)
            assertEquals(1, summary.coveredFunctions)
            assertEquals(4, summary.totalLines)
            assertEquals(2, summary.coveredLines)
        }
    }

    @Test
    fun `covered lines count only executable lines, so coverage cannot exceed 100 percent`() {
        // A line covdbg marks non-executable can still carry a hit count; counting it as covered but
        // not in the total would report more covered lines than lines.
        val db = fixture(dir = tempDir, name = "nonexecutable.covdb") { connection ->
            connection.createStatement().use {
                it.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\b.cpp', 13, 7, 0)""")
            }
        }
        CovdbReader(db).use { reader ->
            val summary = reader.getCoverageSummary()
            assertEquals(4, summary.totalLines)
            assertEquals(2, summary.coveredLines)
        }
    }

    @Test
    fun `all executable lines come back with the blocks mapped to them`(@TempDir dir: Path) {
        val db = fixture(dir, "lines.covdb")
        CovdbReader(db).use { reader ->
            val lines = reader.getAllLineCoverage().associateBy { it.filePath to it.lineNumber }

            // Non-executable lines are left out: the gutter has nothing to say about them.
            assertEquals(4, lines.size)
            assertFalse(("""C:\proj\src\b.cpp""" to 12) in lines)

            // a.cpp:1 maps to one hit block and one missed one - the partially covered line whose
            // execution count alone (5) would call it covered.
            val partial = lines.getValue("""C:\proj\src\a.cpp""" to 1)
            assertEquals(5L, partial.executionCount)
            assertEquals(2, partial.blocks)
            assertEquals(1, partial.blocksHit)

            val full = lines.getValue("""C:\proj\src\a.cpp""" to 2)
            assertEquals(1, full.blocks)
            assertEquals(1, full.blocksHit)

            val missed = lines.getValue("""C:\proj\src\b.cpp""" to 10)
            assertEquals(1, missed.blocks)
            assertEquals(0, missed.blocksHit)
        }
    }

    @Test
    fun `a line without mapped blocks reports none`(@TempDir dir: Path) {
        val db = fixture(dir, "unmapped.covdb")
        CovdbReader(db).use { reader ->
            val unmapped = reader.getAllLineCoverage()
                .single { it.filePath == """C:\proj\src\b.cpp""" && it.lineNumber == 11 }
            assertEquals(0, unmapped.blocks)
            assertEquals(0, unmapped.blocksHit)
        }
    }

    // ── Fixture ─────────────────────────────────────────────────────────

    private fun fixture(dir: Path, name: String, extra: (Connection) -> Unit = {}): String {
        val path = dir.resolve(name).toString()
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { stmt ->
                // Only the tables and columns CovdbReader actually selects.
                stmt.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT)")
                stmt.executeUpdate("CREATE TABLE coverage_metrics (key TEXT PRIMARY KEY, value TEXT)")
                stmt.executeUpdate("CREATE TABLE files (path TEXT PRIMARY KEY, relative_path TEXT)")
                stmt.executeUpdate(
                    "CREATE TABLE functions (module_path TEXT, start_address INTEGER, " +
                        "PRIMARY KEY (module_path, start_address))"
                )
                stmt.executeUpdate(
                    "CREATE TABLE basic_blocks (module_path TEXT, start_address INTEGER, " +
                        "function_start_address INTEGER, hit_count INTEGER, " +
                        "PRIMARY KEY (module_path, start_address))"
                )
                stmt.executeUpdate(
                    "CREATE TABLE source_locations (module_path TEXT, block_start_address INTEGER, " +
                        "file_path TEXT, line_number INTEGER)"
                )
                stmt.executeUpdate(
                    "CREATE TABLE line_coverage (file_path TEXT, line_number INTEGER, " +
                        "execution_count INTEGER, is_executable INTEGER)"
                )
                stmt.executeUpdate(
                    "CREATE TABLE function_coverage (file_path TEXT, function_name TEXT, " +
                        "start_line INTEGER, end_line INTEGER, hit_count INTEGER)"
                )

                stmt.executeUpdate("INSERT INTO metadata VALUES ('schema_version', '7')")
                stmt.executeUpdate("""INSERT INTO metadata VALUES ('source_root', 'C:\proj')""")

                stmt.executeUpdate("""INSERT INTO files VALUES ('C:\proj\src\a.cpp', 'src/a.cpp')""")
                stmt.executeUpdate("""INSERT INTO files VALUES ('C:\proj\src\b.cpp', 'src/b.cpp')""")
                // A file with no executable lines: present in the database, absent from summaries.
                stmt.executeUpdate("""INSERT INTO files VALUES ('C:\proj\src\empty.cpp', 'src/empty.cpp')""")

                stmt.executeUpdate("INSERT INTO functions VALUES ('app.exe', 100)")
                stmt.executeUpdate("INSERT INTO functions VALUES ('app.exe', 200)")

                stmt.executeUpdate("INSERT INTO basic_blocks VALUES ('app.exe', 100, 100, 5)")
                stmt.executeUpdate("INSERT INTO basic_blocks VALUES ('app.exe', 110, 100, 3)")
                stmt.executeUpdate("INSERT INTO basic_blocks VALUES ('app.exe', 200, 200, 0)")

                // a.cpp:1 spans a hit block (100) and a missed one (200); a.cpp:2 is block 110 alone;
                // b.cpp:10 is the missed block 200; b.cpp:11 has no mapping at all.
                stmt.executeUpdate("""INSERT INTO source_locations VALUES ('app.exe', 100, 'C:\proj\src\a.cpp', 1)""")
                stmt.executeUpdate("""INSERT INTO source_locations VALUES ('app.exe', 200, 'C:\proj\src\a.cpp', 1)""")
                stmt.executeUpdate("""INSERT INTO source_locations VALUES ('app.exe', 110, 'C:\proj\src\a.cpp', 2)""")
                stmt.executeUpdate("""INSERT INTO source_locations VALUES ('app.exe', 200, 'C:\proj\src\b.cpp', 10)""")

                stmt.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\a.cpp', 1, 5, 1)""")
                stmt.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\a.cpp', 2, 3, 1)""")
                stmt.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\b.cpp', 10, 0, 1)""")
                stmt.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\b.cpp', 11, 0, 1)""")
                // Non-executable lines must not be counted.
                stmt.executeUpdate("""INSERT INTO line_coverage VALUES ('C:\proj\src\b.cpp', 12, 0, 0)""")

                stmt.executeUpdate("""INSERT INTO function_coverage VALUES ('C:\proj\src\a.cpp', 'Alpha', 1, 2, 5)""")
                stmt.executeUpdate("""INSERT INTO function_coverage VALUES ('C:\proj\src\b.cpp', 'Beta', 10, 11, 0)""")
            }
            extra(connection)
        }
        return path
    }

    private fun metric(connection: Connection, key: String, value: String) {
        connection.prepareStatement("INSERT OR REPLACE INTO coverage_metrics VALUES (?, ?)").use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.executeUpdate()
        }
    }
}
