package com.covdbg.coverage.engine

import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GutterLinesTest {

    private fun line(number: Int, status: Byte, hits: Int): LineData =
        LineData(number, null).apply {
            this.hits = hits
            setStatus(status)
        }

    @Test
    fun `database lines from 1 become document lines from 0`() {
        val lines = arrayOfNulls<Any>(4)
        lines[1] = line(1, LineCoverage.FULL, 3)
        lines[3] = line(3, LineCoverage.PARTIAL, 5)

        assertEquals(
            listOf(
                GutterLine(0, LineCoverage.FULL.toInt(), 3),
                GutterLine(2, LineCoverage.PARTIAL.toInt(), 5)
            ),
            GutterLines.of(lines, lineCount = 10)
        )
    }

    @Test
    fun `lines past the end of a shortened document are dropped`() {
        val lines = arrayOfNulls<Any>(6)
        lines[2] = line(2, LineCoverage.NONE, 0)
        lines[5] = line(5, LineCoverage.FULL, 1)

        assertEquals(listOf(GutterLine(1, LineCoverage.NONE.toInt(), 0)), GutterLines.of(lines, lineCount = 4))
    }

    @Test
    fun `no lines paint nothing`() {
        assertEquals(emptyList<GutterLine>(), GutterLines.of(null, lineCount = 10))
    }
}
