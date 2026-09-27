package com.covdbg.coverage.seats

import com.covdbg.coverage.CovdbgNotifications
import com.covdbg.coverage.CovdbgNotifications.action
import com.covdbg.coverage.CovdbgNotifications.openSettingsAction
import com.covdbg.coverage.run.CovdbgArgs
import com.covdbg.coverage.run.CovdbgExecutableResolver
import com.covdbg.coverage.run.LineAssembler
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.Duration

/**
 * Drives the device-code sign-in flow as a cancellable background task.
 *
 * covdbg prints a URL and a code, then polls the service until the user confirms in the browser. It
 * blocks for up to ten minutes and installs no Ctrl+C handler, so cancelling means killing the
 * child. That is safe and idempotent: nothing is stored locally, and the pending device code simply
 * expires on the service.
 *
 * covdbg sleeps the poll interval (about five seconds) before its first poll, so success can surface
 * several seconds after the browser confirmation. That is not a hang; do not add a shorter timeout.
 */
class CovdbgLoginTask(project: Project) :
    Task.Backgroundable(project, "Signing in to covdbg", true) {

    private val parser = LoginOutputParser()
    private val stderr = StringBuilder()
    private var promptNotification: Notification? = null

    /** Whoever covdbg last named, whether an existing session or the completed sign-in. */
    private var lastKnownEmail: String? = null
    private var sawDeviceCode = false

    override fun run(indicator: ProgressIndicator) {
        val exe = CovdbgExecutableResolver.pathOrNotify(project) ?: return

        indicator.isIndeterminate = true
        indicator.text = "Starting sign-in"

        val cmd = GeneralCommandLine(exe).apply {
            addParameters(CovdbgArgs.login())
            project.basePath?.let { workDirectory = File(it) }
        }

        // The Covdbg path setting is honoured even when it points nowhere, so starting can fail.
        val handler = try {
            KillableProcessHandler(cmd)
        } catch (e: ExecutionException) {
            LOG.info("covdbg login could not be started", e)
            CovdbgNotifications.error(
                project,
                "${e.message ?: "covdbg could not be started."}<br/>Check the Covdbg path in Settings.",
                "covdbg sign-in failed"
            )?.openSettingsAction()
            return
        }
        val lines = LineAssembler { line, isError ->
            if (isError) stderr.appendLine(line) else onStdoutLine(line, indicator)
        }

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                lines.accept(event.text, outputType == ProcessOutputTypes.STDERR)
            }
        })
        handler.startNotify()

        val deadline = System.nanoTime() + SIGN_IN_BUDGET_NANOS
        var cancelled = false
        var timedOut = false
        while (!handler.waitFor(POLL_MS.toLong())) {
            if (indicator.isCanceled) {
                cancelled = true
                break
            }
            if (System.nanoTime() > deadline) {
                timedOut = true
                break
            }
        }

        if (cancelled || timedOut) {
            handler.destroyProcess()
            handler.waitFor(KILL_GRACE_MS.toLong())
        }
        lines.flush()
        expirePrompt()

        when {
            cancelled -> CovdbgNotifications.info(project, "Sign-in cancelled.")
            timedOut -> CovdbgNotifications.warn(
                project,
                "Sign-in timed out after ten minutes. Start it again when you are ready to " +
                    "confirm in the browser."
            )
            handler.exitCode == 0 -> notifySignedIn()
            else -> notifyFailed(handler.exitCode ?: -1)
        }

        CovdbgSeatService.getInstance(project).refresh()
    }

    private fun onStdoutLine(line: String, indicator: ProgressIndicator) {
        LOG.debug("covdbg login: $line")
        when (val event = parser.accept(line)) {
            is LoginEvent.AlreadySignedIn -> lastKnownEmail = event.email
            is LoginEvent.DeviceCode -> {
                sawDeviceCode = true
                indicator.text2 = "Code ${event.code}"
                showPrompt(event.url, event.code)
            }
            LoginEvent.Waiting -> indicator.text = "Waiting for browser confirmation"
            is LoginEvent.SignedIn -> event.email?.let { lastKnownEmail = it }
            null -> {}
        }
    }

    /**
     * The prompt lives in a sticky notification rather than only in the progress indicator: text2
     * truncates, and users navigate away from the progress bar while they go to the browser.
     */
    private fun showPrompt(url: String, code: String) {
        expirePrompt()
        promptNotification = CovdbgNotifications.notify(
            project = project,
            type = NotificationType.INFORMATION,
            content = "Open $url and confirm the code <b>$code</b> there.",
            title = "Confirm your covdbg sign-in",
            group = CovdbgNotifications.SIGN_IN_GROUP
        ) {
            action("Open Page and Copy Code") {
                CopyPasteManager.getInstance().setContents(StringSelection(code))
                BrowserUtil.browse(url)
            }
            action("Copy Code") { CopyPasteManager.getInstance().setContents(StringSelection(code)) }
            action("Open Page") { BrowserUtil.browse(url) }
        }
    }

    private fun expirePrompt() {
        promptNotification?.expire()
        promptNotification = null
    }

    private fun notifySignedIn() {
        val headline = lastKnownEmail?.let { "Signed in to covdbg as $it" } ?: "Signed in to covdbg"
        CovdbgNotifications.info(
            project,
            "$headline.<br/>Seats, teams and your personal lock are managed at " +
                CovdbgNotifications.SERVICE_URL
        )
    }

    private fun notifyFailed(exitCode: Int) {
        val detail = stderr.lines().lastOrNull { it.isNotBlank() }?.trim()
            ?: if (sawDeviceCode) {
                "The sign-in was not completed."
            } else {
                "covdbg login exited with $exitCode."
            }
        // Nothing below needs the transcript, and the retry action keeps this task in the event log.
        stderr.setLength(0)
        CovdbgNotifications.error(project, detail, "covdbg sign-in failed")
            ?.action("Try Again") { CovdbgLoginTask(it).queue() }
    }

    companion object {
        private val LOG = Logger.getInstance(CovdbgLoginTask::class.java)

        /** covdbg gives up after ten minutes; allow a little slack before we pull the plug. */
        private val SIGN_IN_BUDGET_NANOS = Duration.ofMinutes(10).plusSeconds(30).toNanos()
        private const val POLL_MS = 200
        private const val KILL_GRACE_MS = 2_000
    }
}
