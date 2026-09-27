package com.covdbg.coverage.seats

import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.run.CovdbgArgs
import com.covdbg.coverage.run.CovdbgExecutableResolver
import com.covdbg.coverage.run.firstInteresting
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * Signs out.
 *
 * covdbg exits 0 whether or not anyone was signed in, so the exit code alone would tell someone who
 * was never signed in that they had just been signed out. The reply is parsed instead.
 */
class CovdbgLogoutTask(project: Project) :
    Task.Backgroundable(project, "Signing out of covdbg", false) {

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val exe = CovdbgExecutableResolver.pathOrNotify(project) ?: return

        val result = CovdbgCli.capture(exe, CovdbgArgs.logout(), project.basePath, TIMEOUT_MS)
        when (val parsed = LogoutOutputParser.parse(result.stdout)) {
            is LogoutOutputParser.Result.SignedOut ->
                CovdbgNotifications.info(project, "Signed out of covdbg (${CovdbgNotifications.escape(parsed.email)}).")
            LogoutOutputParser.Result.WasNotSignedIn ->
                CovdbgNotifications.info(project, "Not signed in to covdbg.")
            is LogoutOutputParser.Result.Unknown -> {
                val detail = parsed.detail
                    ?: result.stderr.lines().firstInteresting()
                    ?: "covdbg logout exited with ${result.exitCode}."
                if (result.exitCode == 0) {
                    CovdbgNotifications.warn(project, CovdbgNotifications.escape(detail))
                } else {
                    CovdbgNotifications.error(project, CovdbgNotifications.escape(detail))
                }
            }
        }

        CovdbgSeatService.getInstance(project).refresh()
    }

    private companion object {
        const val TIMEOUT_MS = 60_000
    }
}
