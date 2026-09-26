package com.covdbg.coverage.seats

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Probes the sign-in state once the project is open, so the status bar widget and settings panel
 * show something real before the first run rather than after it is refused.
 */
class CovdbgSeatStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        CovdbgSeatService.getInstance(project).refresh()
    }
}
