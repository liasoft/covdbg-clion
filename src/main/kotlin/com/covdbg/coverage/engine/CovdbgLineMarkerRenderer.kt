package com.covdbg.coverage.engine

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * The coverage bar beside one line in the editor gutter, in the color the scheme gives covered,
 * partially covered or uncovered lines - the same keys, and so the same colors, as the platform's own
 * coverage gutter. The scheme is read when painting, so a scheme change shows on the next repaint.
 */
class CovdbgLineMarkerRenderer(private val line: GutterLine) : ActiveGutterRenderer, LineMarkerRendererEx {

    /** Where the bar was last painted across the gutter; the gutter itself checks the line. */
    @Volatile
    private var paintedX: IntRange = IntRange.EMPTY

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        paintedX = r.x until r.x + r.width
        g.color = colorOf(editor.colorsScheme, line.status) ?: return
        g.fillRect(r.x, r.y, r.width, r.height)
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.LEFT

    override fun getTooltipText(): String = tooltipOf(line)

    override fun getAccessibleName(): String = tooltipOf(line)

    /**
     * True over the bar. The gutter shows [getTooltipText], and passes clicks to [doAction], only for a
     * renderer that says it can act where the mouse is, and the interface's default says no. The bar is a few pixels wide, so a
     * little slack on either side makes it reachable.
     */
    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
        val x = paintedX
        if (x.isEmpty()) return false
        val slack = JBUI.scale(2)
        return e.x in (x.first - slack)..(x.last + slack)
    }

    /** Opens the line's popup, with the "Hide coverage" link, as clicking the platform's bar does. */
    override fun doAction(editor: Editor, e: MouseEvent) {
        val highlighter = CovdbgEditorCoverage.highlightersOf(editor).firstOrNull { it.lineMarkerRenderer === this }
            ?: return
        e.consume()
        CovdbgCoveragePopup.show(editor, highlighter)
    }

    companion object {
        fun keyOf(status: Int): TextAttributesKey = when (status) {
            LineCoverage.FULL.toInt() -> CodeInsightColors.LINE_FULL_COVERAGE
            LineCoverage.PARTIAL.toInt() -> CodeInsightColors.LINE_PARTIAL_COVERAGE
            else -> CodeInsightColors.LINE_NONE_COVERAGE
        }

        /** The coverage keys define their color as an error-stripe color, as the platform reads them. */
        fun colorOf(scheme: EditorColorsScheme, status: Int): Color? =
            scheme.getAttributes(keyOf(status))?.let { it.errorStripeColor ?: it.foregroundColor }

        fun tooltipOf(line: GutterLine): String = when (line.status) {
            LineCoverage.PARTIAL.toInt() -> "Partially covered, hits: ${line.hits}"
            LineCoverage.NONE.toInt() -> "Not covered"
            else -> "Hits: ${line.hits}"
        }
    }
}
