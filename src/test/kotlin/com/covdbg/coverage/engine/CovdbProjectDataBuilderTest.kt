package com.covdbg.coverage.engine

import com.covdbg.coverage.model.LineCoverageRecord
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CovdbProjectDataBuilderTest {

    private fun line(file: String, line: Int, count: Long, blocks: Int = 0, hit: Int = 0) =
        LineCoverageRecord(file, line, count, blocks = blocks, blocksHit = hit)

    private val toLocal: (String) -> String = { it.replace('\\', '/').replace("C:/build", "D:/work") }

    @Test
    fun `each file becomes class data keyed by its local path, lines indexed by number`() {
        val data = CovdbProjectDataBuilder.build(
            listOf(
                line("""C:\build\a.cpp""", 3, 5, blocks = 2, hit = 1),
                line("""C:\build\a.cpp""", 4, 2, blocks = 1, hit = 1),
                line("""C:\build\b.cpp""", 1, 0, blocks = 1, hit = 0)
            ),
            toLocal
        )

        assertEquals(setOf("D:/work/a.cpp", "D:/work/b.cpp"), data.classes.keys)

        val a = data.getClassData("D:/work/a.cpp")
        assertNotNull(a)
        val lines = a.lines
        assertNull(lines[1], "lines the database did not report stay empty")
        val three = lines[3] as LineData
        assertEquals(3, three.lineNumber)
        assertEquals(5, three.hits)
        assertEquals(LineCoverage.PARTIAL, three.status.toByte())
        assertEquals(LineCoverage.FULL, (lines[4] as LineData).status.toByte())

        val b = data.getClassData("D:/work/b.cpp")
        assertEquals(LineCoverage.NONE, (b.lines[1] as LineData).status.toByte())
    }

    @Test
    fun `two database paths for one local file are merged line by line`() {
        val data = CovdbProjectDataBuilder.build(
            listOf(
                line("""C:\build\a.cpp""", 2, 1, blocks = 1, hit = 1),
                line("""c:\build\a.cpp""", 2, 0, blocks = 1, hit = 0)
            )
        ) { "D:/work/a.cpp" }

        val merged = data.getClassData("D:/work/a.cpp").lines[2] as LineData
        assertEquals(1, merged.hits)
        assertEquals(LineCoverage.PARTIAL, merged.status.toByte())
    }

    @Test
    fun `a hit count beyond Int range is capped rather than wrapped`() {
        // A covdb count is a Long; a plain toInt() would turn this negative. (LineData caps hits
        // further on its own, so only the sign is pinned here.)
        val data = CovdbProjectDataBuilder.build(listOf(line("a.cpp", 1, Long.MAX_VALUE, 1, 1))) { it }
        assertTrue((data.getClassData("a.cpp").lines[1] as LineData).hits > 0)
    }
}
