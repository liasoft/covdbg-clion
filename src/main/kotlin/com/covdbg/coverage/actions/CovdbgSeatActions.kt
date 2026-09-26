package com.covdbg.coverage.actions

import com.covdbg.coverage.config.CovdbgConfigWriter
import com.covdbg.coverage.seats.CovdbgLoginTask
import com.covdbg.coverage.seats.CovdbgLogoutTask
import com.covdbg.coverage.seats.CovdbgSeatService
import com.covdbg.coverage.seats.SignInStatus
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/** Starts the device-code sign-in flow. */
class CovdbgSignInAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project == null) return

        // Signing in again replaces an existing session, so say so rather than looking like a no-op.
        //
        // Deliberately no "is covdbg installed?" check here: update() runs on every toolbar repaint,
        // resolving touches the filesystem, and both tasks already report a missing executable at
        // invoke time with a better message and an Open Settings action.
        val signedIn = CovdbgSeatService.getInstance(project).status() is SignInStatus.SignedIn
        e.presentation.text = if (signedIn) "Sign In to covdbg as Someone Else" else "Sign In to covdbg"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        CovdbgLoginTask(project).queue()
    }
}

/** Ends the session on the service and forgets it on this machine. */
class CovdbgSignOutAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        CovdbgLogoutTask(project).queue()
    }
}

/** Re-probes `covdbg whoami`. */
class CovdbgRefreshSignInStatusAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        CovdbgSeatService.getInstance(project).refresh()
    }
}

/** Writes a starter `.covdbg.yaml` in the project root. */
class CovdbgCreateConfigAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        CovdbgConfigWriter.createAndOpen(project)
    }
}
