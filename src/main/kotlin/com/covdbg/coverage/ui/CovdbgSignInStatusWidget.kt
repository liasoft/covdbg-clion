package com.covdbg.coverage.ui

import com.covdbg.coverage.CovdbgIcons
import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.seats.CovdbgSeatListener
import com.covdbg.coverage.seats.CovdbgSeatService
import com.covdbg.coverage.seats.SignInStatus
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import javax.swing.Icon

/**
 * Shows who covdbg runs are licensed to, in the status bar: the signed-in account, "not signed in",
 * or the project token. A click offers Sign In, Sign Out, Refresh and the covdbg settings.
 */
class CovdbgSignInStatusWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(
            CovdbgSeatListener.TOPIC,
            object : CovdbgSeatListener {
                override fun signInStatusChanged(status: SignInStatus) {
                    this@CovdbgSignInStatusWidget.statusBar?.updateWidget(ID)
                }
            }
        )
        CovdbgSeatService.getInstance(project).refreshIfStale()
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getIcon(): Icon = CovdbgIcons.Logo

    override fun getSelectedValue(): String = status().shortLabel

    override fun getTooltipText(): String = status().tooltip.ifBlank { "covdbg sign-in status" }

    override fun getPopup(): JBPopup {
        val actions = ActionManager.getInstance()
        val group = DefaultActionGroup().apply {
            SEAT_ACTION_IDS.mapNotNull { actions.getAction(it) }.forEach { add(it) }
            addSeparator()
            add(object : AnAction("covdbg Settings…") {
                override fun actionPerformed(e: AnActionEvent) = CovdbgNotifications.openSettings(project)
            })
        }
        return JBPopupFactory.getInstance().createActionGroupPopup(
            "covdbg",
            group,
            SimpleDataContext.getProjectContext(project),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )
    }

    private fun status(): SignInStatus = CovdbgSeatService.getInstance(project).status()

    companion object {
        const val ID = "CovdbgSignInStatus"

        /** The Tools | covdbg actions, so the widget and the menu enable them by the same rules. */
        private val SEAT_ACTION_IDS = listOf("covdbg.SignIn", "covdbg.SignOut", "covdbg.RefreshSignInStatus")
    }
}

class CovdbgSignInStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = CovdbgSignInStatusWidget.ID

    @Suppress("DialogTitleCapitalization")
    override fun getDisplayName(): String = "covdbg Sign-In Status"

    /** covdbg runs only on Windows; elsewhere there is nothing to be signed in to. */
    override fun isAvailable(project: Project): Boolean = SystemInfo.isWindows

    override fun createWidget(project: Project): StatusBarWidget = CovdbgSignInStatusWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
