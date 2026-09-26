package com.covdbg.coverage.seats

import com.covdbg.coverage.run.CovdbgArgs
import com.covdbg.coverage.run.CovdbgExecutableResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Notified whenever the cached sign-in status changes. */
interface CovdbgSeatListener {
    fun signInStatusChanged(status: SignInStatus)

    companion object {
        val TOPIC: Topic<CovdbgSeatListener> =
            Topic.create("covdbg sign-in status", CovdbgSeatListener::class.java)
    }
}

/**
 * Tracks who this machine is signed in to covdbg as.
 *
 * Project-level because the covdbg executable path is a project setting. The credential itself is
 * per-Windows-user (Windows Credential Manager, entry `Liasoft/covdbg`), so two projects will
 * normally agree.
 */
@Service(Service.Level.PROJECT)
class CovdbgSeatService(private val project: Project) {

    @Volatile
    private var cached: SignInStatus = SignInStatus.Unknown("not probed yet")

    @Volatile
    private var probedAt = 0L

    private val probeInFlight = AtomicBoolean(false)

    fun status(): SignInStatus = cached

    /**
     * Probes `covdbg whoami` on a pooled thread.
     *
     * Several places want the answer when a project opens - the startup activity, the status bar widget,
     * the settings page - so concurrent calls are coalesced into the one in flight rather than
     * spawning a process each.
     */
    fun refresh() {
        if (!probeInFlight.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                publish(probe())
            } finally {
                probeInFlight.set(false)
            }
        }
    }

    /** Refreshes only when the cached answer is older than [maxAge]. */
    fun refreshIfStale(maxAge: Duration = DEFAULT_MAX_AGE) {
        if (System.nanoTime() - probedAt < maxAge.toNanos()) return
        refresh()
    }

    private fun publish(status: SignInStatus) {
        val changed = status != cached
        cached = status
        probedAt = System.nanoTime()
        if (!changed || project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            project.messageBus.syncPublisher(CovdbgSeatListener.TOPIC).signInStatusChanged(status)
        }
    }

    /** Blocking probe. Never call on the EDT. */
    fun probe(): SignInStatus {
        val exe = CovdbgExecutableResolver.pathFor(project)
            ?: return SignInStatus.Unknown("covdbg was not found")
        val result = CovdbgCli.capture(exe, CovdbgArgs.whoami(), project.basePath, WHOAMI_TIMEOUT_MS)
        if (result.timedOut) {
            return SignInStatus.Unknown("covdbg whoami timed out")
        }
        val status = WhoamiParser.parse(result.exitCode, result.stdout, isProjectTokenSet())
        LOG.debug("covdbg whoami -> $status")
        return status
    }

    /** True when the environment carries a CI credential, which `covdbg whoami` does not consult. */
    fun isProjectTokenSet(): Boolean =
        !System.getenv(WhoamiParser.PROJECT_TOKEN_ENV).isNullOrBlank()

    companion object {
        private val LOG = Logger.getInstance(CovdbgSeatService::class.java)
        private const val WHOAMI_TIMEOUT_MS = 15_000

        /** Sign-in changes by hand, not by the minute; this only guards against repeat probes. */
        private val DEFAULT_MAX_AGE = Duration.ofMinutes(5)

        fun getInstance(project: Project): CovdbgSeatService =
            project.getService(CovdbgSeatService::class.java)
    }
}
