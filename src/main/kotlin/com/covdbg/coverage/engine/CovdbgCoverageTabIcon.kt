package com.covdbg.coverage.engine

import com.covdbg.coverage.CovdbgIcons
import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/**
 * Puts the covdbg logo on the Coverage tool window tabs that show covdbg coverage.
 *
 * The platform creates those tabs itself and offers engines no icon of their own, so this watches
 * the Coverage tool window for new tabs and marks the ones holding the view of a covdbg suite -
 * whether it came from a run or from "Import External Coverage Report".
 */
class CovdbgCoverageTabIcon(private val project: Project) : ToolWindowManagerListener {

    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
        if (CoverageViewManager.TOOLWINDOW_ID !in ids) return
        val toolWindow = toolWindowManager.getToolWindow(CoverageViewManager.TOOLWINDOW_ID) ?: return
        toolWindow.contentManager.contents.forEach { mark(it) }
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) = mark(event.content)
        })
    }

    private fun mark(content: Content) {
        // A tab is created for the bundle being shown, which is by then the current one.
        val bundle = CoverageDataManager.getInstance(project).currentSuitesBundle ?: return
        if (bundle.coverageEngine !is CovdbgCoverageEngine) return
        if (CoverageViewManager.getInstance(project).getView(bundle) !== content.component) return

        content.icon = CovdbgIcons.Logo
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    }
}
