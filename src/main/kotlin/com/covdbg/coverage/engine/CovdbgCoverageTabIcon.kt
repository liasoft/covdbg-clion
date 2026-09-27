package com.covdbg.coverage.engine

import com.covdbg.coverage.CovdbgIcons
import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuiteListener
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Puts the covdbg logo on the Coverage tool window tabs that show covdbg coverage.
 *
 * The platform creates those tabs itself and offers engines no icon of their own. It creates them
 * from its own suite listener, `CoverageViewSuiteListener`, which `CoverageDataManagerImpl` adds in
 * its constructor and so calls before any other: by the time [coverageDataCalculated] runs here, the
 * tab for the bundle exists. The event is fired from the annotation task's `onSuccess`, on the EDT,
 * which is where the tool window's content may be touched - whether the coverage came from a run or
 * from "Import External Coverage Report".
 *
 * Created by [CovdbgCoverageEngine.getCoverageAnnotator], which the platform asks before it loads
 * covdbg coverage, so nothing is registered in projects that never show any. Listening for the tool
 * window instead would touch its content while the project opens, off the EDT, and build it early.
 */
@Service(Service.Level.PROJECT)
class CovdbgCoverageTabIcon(private val project: Project) : CoverageSuiteListener, Disposable {

    init {
        CoverageDataManager.getInstance(project).addSuiteListener(this, this)
    }

    override fun coverageDataCalculated(bundle: CoverageSuitesBundle) {
        if (bundle.coverageEngine !is CovdbgCoverageEngine) return
        val view = CoverageViewManager.getInstance(project).getView(bundle) ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(CoverageViewManager.TOOLWINDOW_ID)
        val content = toolWindow?.contentManagerIfCreated?.getContent(view) ?: return

        // Flag first: the tab exists already, and it is re-rendered when the icon is set - reading the
        // flag then. Setting the flag afterwards fires nothing, so the icon would stay hidden.
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        content.icon = CovdbgIcons.Logo
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): CovdbgCoverageTabIcon =
            project.getService(CovdbgCoverageTabIcon::class.java)
    }
}
