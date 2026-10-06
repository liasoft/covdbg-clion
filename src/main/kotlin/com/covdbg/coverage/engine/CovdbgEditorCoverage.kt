package com.covdbg.coverage.engine

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuiteListener
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.rt.coverage.data.ProjectData

/**
 * Paints covdbg coverage in the editor gutter.
 *
 * The platform's coverage gutter is not used: it finds a file's coverage only through
 * `CoverageEngine.getQualifiedName(File, PsiFile)`, which is internal API and so not allowed in a
 * Marketplace plugin - and every other way into it (`getCorrespondingOutputFiles`,
 * `createSrcFileAnnotator`, `getLineMarkerRenderer`) is internal too. [CovdbgCoverageEngine] turns it
 * off for covdbg, and this paints the same bars, in the same scheme colors, from the shown suite's
 * `ProjectData`, which is keyed by the file's `VirtualFile.path`.
 *
 * Every trigger repaints all editors from what is active right now, so a redundant one does no harm:
 * - [coverageDataCalculated] - a suite finished loading, from a run or an import;
 * - [beforeSuiteChosen] / [afterSuiteChosen] - another suite is being shown;
 * - [CovdbgCoverageAnnotator.onSuiteChosen] - a coverage tab was closed or coverage hidden, which the
 *   platform announces to no listener, only to the engine's annotator;
 * - an editor opening, and the color scheme changing.
 *
 * Created by [CovdbgCoverageEngine.getCoverageAnnotator], which the platform asks before it loads
 * covdbg coverage, so nothing is registered in projects that never show any.
 */
@Service(Service.Level.PROJECT)
class CovdbgEditorCoverage(private val project: Project) : CoverageSuiteListener, Disposable {

    init {
        CoverageDataManager.getInstance(project).addSuiteListener(this, this)
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                if (event.editor.project == project) paint(event.editor, shownData())
            }
        }, this)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refresh() })
    }

    override fun coverageDataCalculated(bundle: CoverageSuitesBundle) {
        if (bundle.coverageEngine is CovdbgCoverageEngine) refresh()
    }

    override fun beforeSuiteChosen() = refresh()

    override fun afterSuiteChosen() = refresh()

    /** Repaints every editor of the project from the covdbg coverage shown now, or clears it. */
    fun refresh() {
        val application = ApplicationManager.getApplication()
        if (!application.isDispatchThread) {
            application.invokeLater { if (!project.isDisposed) refresh() }
            return
        }
        if (project.isDisposed) return
        val data = shownData()
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach { paint(it, data) }
    }

    /** The coverage of the covdbg suite being shown, if one is and it has loaded. */
    private fun shownData(): ProjectData? =
        CoverageDataManager.getInstance(project).activeSuites()
            .firstOrNull { it.coverageEngine is CovdbgCoverageEngine && it.coverageData != null }
            ?.coverageData

    private fun paint(editor: Editor, data: ProjectData?) {
        if (editor.isDisposed) return
        CovdbgCoveragePopup.close(editor)
        editor.getUserData(HIGHLIGHTERS)?.forEach { it.dispose() }
        editor.putUserData(HIGHLIGHTERS, null)

        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val classData = data?.getClassData(file.path) ?: return
        val document = editor.document
        val scheme = editor.colorsScheme
        val highlighters = GutterLines.of(classData.lines, document.lineCount).map { line ->
            editor.markupModel.addRangeHighlighter(
                document.getLineStartOffset(line.documentLine),
                document.getLineEndOffset(line.documentLine),
                HighlighterLayer.SELECTION - 1,
                null,
                HighlighterTargetArea.LINES_IN_RANGE
            ).apply {
                lineMarkerRenderer = CovdbgLineMarkerRenderer(line)
                setErrorStripeMarkColor(CovdbgLineMarkerRenderer.colorOf(scheme, line.status))
                errorStripeTooltip = CovdbgLineMarkerRenderer.tooltipOf(line)
                isThinErrorStripeMark = true
            }
        }
        editor.putUserData(HIGHLIGHTERS, highlighters)
    }

    override fun dispose() {
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach { paint(it, null) }
    }

    companion object {
        /** The highlighters covdbg added to an editor, so a repaint removes only its own. */
        private val HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("covdbg.coverage.highlighters")

        /** The coverage marks painted in [editor], for stepping between them. */
        fun highlightersOf(editor: Editor): List<RangeHighlighter> =
            editor.getUserData(HIGHLIGHTERS).orEmpty().filter { it.isValid }

        fun getInstance(project: Project): CovdbgEditorCoverage =
            project.getService(CovdbgEditorCoverage::class.java)
    }
}
