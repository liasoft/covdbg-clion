package com.covdbg.coverage.engine

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * What clicking a coverage bar opens, as the platform's coverage gutter does: the line's hits, under
 * a toolbar to step to the previous or next coverage mark and the platform's own "Hide coverage" link.
 *
 * The platform builds its version with `HintManagerImpl.showGutterHint`, an implementation class, so
 * this is an ordinary popup. "Hide coverage" is the platform action itself (`HideCoverage`), which
 * closes every active suite; [CovdbgEditorCoverage] then clears the gutter and, with it, this popup.
 */
object CovdbgCoveragePopup {

    /** The popup open on an editor, so a repaint of the gutter can close it. */
    private val POPUP = Key.create<JBPopup>("covdbg.coverage.popup")

    /** The platform's "Hide coverage" link, registered by the coverage plugin in 2025.3 and 2026.2. */
    private const val HIDE_COVERAGE_ACTION_ID = "HideCoverage"

    fun show(editor: Editor, highlighter: RangeHighlighter) {
        close(editor)
        val renderer = highlighter.lineMarkerRenderer as? CovdbgLineMarkerRenderer ?: return
        val line = editor.document.getLineNumber(highlighter.startOffset)

        val actions = DefaultActionGroup(
            GotoMarkAction(editor, line, previous = true),
            GotoMarkAction(editor, line, previous = false)
        )
        ActionManager.getInstance().getAction(HIDE_COVERAGE_ACTION_ID)?.let {
            actions.addSeparator()
            actions.add(it)
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("CovdbgCoverageHint", actions, true)
        // The editor supplies the project the actions need.
        toolbar.targetComponent = editor.contentComponent

        val panel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)
            add(JBLabel(renderer.tooltipText).apply { border = JBUI.Borders.empty(4, 8, 6, 8) }, BorderLayout.CENTER)
        }
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .createPopup()
        editor.putUserData(POPUP, popup)

        // Just below the line, at the gutter's edge, where the platform shows its own.
        val gutter = editor.gutter as? JComponent ?: editor.contentComponent
        val y = editor.logicalPositionToXY(LogicalPosition(line + 1, 0)).y
        popup.show(RelativePoint(gutter, Point(0, y)))
    }

    fun close(editor: Editor) {
        editor.getUserData(POPUP)?.cancel()
        editor.putUserData(POPUP, null)
    }

    /** Moves to the nearest coverage mark above or below [line] and opens its popup. */
    private class GotoMarkAction(
        private val editor: Editor,
        private val line: Int,
        private val previous: Boolean
    ) : DumbAwareAction(
        if (previous) "Previous Coverage Mark" else "Next Coverage Mark",
        null,
        if (previous) AllIcons.Actions.PreviousOccurence else AllIcons.Actions.NextOccurence
    ) {
        private fun target(): RangeHighlighter? {
            val marks = CovdbgEditorCoverage.highlightersOf(editor)
                .map { editor.document.getLineNumber(it.startOffset) to it }
            return if (previous) {
                marks.filter { it.first < line }.maxByOrNull { it.first }?.second
            } else {
                marks.filter { it.first > line }.minByOrNull { it.first }?.second
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = target() != null
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            val target = target() ?: return
            val targetLine = editor.document.getLineNumber(target.startOffset)
            editor.caretModel.moveToLogicalPosition(LogicalPosition(targetLine, 0))
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            show(editor, target)
        }
    }
}
