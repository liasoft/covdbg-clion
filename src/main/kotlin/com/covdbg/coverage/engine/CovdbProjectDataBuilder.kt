package com.covdbg.coverage.engine

import com.covdbg.coverage.model.LineCoverageRecord
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData

/**
 * Turns a `.covdb`'s line records into the platform's `ProjectData`: one `ClassData` per source file,
 * named by the file's local path, with one `LineData` per reported line.
 */
object CovdbProjectDataBuilder {

    /**
     * @param localPathOf The local path to key a database path's lines under, asked once per database
     *   path - it may hit the file system. Two database paths that map to the same local file are
     *   merged, line by line.
     */
    fun build(records: List<LineCoverageRecord>, localPathOf: (String) -> String): ProjectData {
        val data = ProjectData()
        val byDatabasePath = records.filter { it.lineNumber > 0 }.groupBy { it.filePath }
        val byLocalPath = byDatabasePath.entries.groupBy(
            keySelector = { localPathOf(it.key) },
            valueTransform = { it.value }
        )
        byLocalPath
            .mapValues { (_, lists) -> lists.flatten() }
            .forEach { (localPath, fileRecords) ->
                val lines = arrayOfNulls<LineData>(fileRecords.maxOf { it.lineNumber } + 1)
                fileRecords.groupBy { it.lineNumber }.forEach { (lineNumber, sameLine) ->
                    lines[lineNumber] = lineData(lineNumber, sameLine)
                }
                data.getOrCreateClassData(localPath).setLines(lines)
            }
        return data
    }

    private fun lineData(lineNumber: Int, records: List<LineCoverageRecord>): LineData {
        val executionCount = records.sumOf { it.executionCount }
        val line = LineData(lineNumber, null)
        line.hits = executionCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        line.setStatus(
            LineStatus.of(executionCount, records.sumOf { it.blocks }, records.sumOf { it.blocksHit })
        )
        line.fillArrays()
        return line
    }
}
