package com.covdbg.coverage.engine

import com.intellij.rt.coverage.data.LineData

/** One line of a file's coverage as the gutter paints it. */
data class GutterLine(
    /** 0-based, as the editor's document counts lines. */
    val documentLine: Int,
    /** `LineCoverage.FULL`, `PARTIAL` or `NONE`. */
    val status: Int,
    val hits: Int
)

object GutterLines {

    /**
     * The lines of a `ClassData` worth painting in a document of [lineCount] lines.
     *
     * The database numbers lines from 1. A line past the end of the document - the file was shortened
     * since the run - is dropped rather than painted on the wrong line or thrown on.
     */
    fun of(lines: Array<Any?>?, lineCount: Int): List<GutterLine> =
        lines.orEmpty()
            .filterIsInstance<LineData>()
            .filter { it.lineNumber in 1..lineCount }
            .map { GutterLine(it.lineNumber - 1, it.status, it.hits) }
}
