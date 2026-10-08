package com.covdbg.coverage.seats

/**
 * What `covdbg whoami` says about this machine.
 *
 * The user-facing wording lives here rather than in each panel, so the status bar and the settings
 * page cannot describe the same state differently.
 */
sealed interface SignInStatus {

    /** Short text for a status label. */
    val label: String

    /** Shorter still, for the status bar; never blank, so the widget always has something to click. */
    val shortLabel: String get() = label

    /** The same state, explained. */
    val tooltip: String

    data class SignedIn(val email: String, val teamName: String? = null) : SignInStatus {
        override val label get() = if (teamName == null) email else "$email \u00B7 $teamName"
        override val tooltip get() =
            if (teamName == null) "Signed in to covdbg as $email" else "Signed in to covdbg as $email for $teamName"
    }

    data object NotSignedIn : SignInStatus {
        override val label get() = "Not signed in"
        override val tooltip get() = "covdbg runs will be refused until you sign in"
    }

    /**
     * COVDBG_PROJECT_TOKEN is set in the environment, so runs are licensed by that credential even
     * though `covdbg whoami` reports nobody signed in.
     */
    data object ProjectToken : SignInStatus {
        override val label get() = "Using ${WhoamiParser.PROJECT_TOKEN_ENV} from the environment"
        override val shortLabel get() = WhoamiParser.PROJECT_TOKEN_ENV
        override val tooltip get() =
            "Runs are licensed by ${WhoamiParser.PROJECT_TOKEN_ENV}; covdbg whoami does not report it"
    }

    /** The probe failed, or said something unexpected. Says nothing about whether runs will work. */
    data class Unknown(val reason: String) : SignInStatus {
        override val label get() = ""
        override val shortLabel get() = "covdbg"
        override val tooltip get() = reason
    }
}

/**
 * Splits the identity part of a "Signed in as ..." line into e-mail and optional team name.
 *
 * covdbg 1.4.0+ prints `<email> for <team name>`, 1.3.x just `<email>`. An e-mail cannot contain a
 * space but a team name can contain " for " (`Team for Acme`), so the split is at the FIRST " for ".
 */
internal fun splitIdentity(identity: String): Pair<String, String?> {
    val match = Regex("""^(\S+) for (.+)$""").find(identity.trim()) ?: return identity.trim() to null
    return match.groupValues[1] to match.groupValues[2].trim()
}

/**
 * Parses `covdbg whoami`.
 *
 * covdbg prints `Signed in as <email> for <team name>.` (1.3.x: `Signed in as <email>.`) and exits 0, or `Not signed in.` and exits **1**.
 *
 * The important subtlety: `whoami` reads only the stored session and ignores COVDBG_PROJECT_TOKEN
 * entirely. In a token environment it reports "not signed in" while runs are perfectly licensed, so
 * a caller that has the token must be told that its exit 1 is inconclusive rather than a refusal in
 * waiting. That is why [projectTokenSet] is a parameter instead of an environment read — it keeps
 * this parser pure and testable.
 */
object WhoamiParser {

    private val SIGNED_IN = Regex("""^Signed in as (.+)\.$""")
    private val NOT_SIGNED_IN = Regex("""^Not signed in\.$""")

    const val PROJECT_TOKEN_ENV = "COVDBG_PROJECT_TOKEN"

    fun parse(exitCode: Int, stdout: String, projectTokenSet: Boolean): SignInStatus {
        for (line in stdout.lines()) {
            val trimmed = line.trim()
            SIGNED_IN.find(trimmed)?.let { match ->
                val (email, team) = splitIdentity(match.groupValues[1])
                return SignInStatus.SignedIn(email, team)
            }
            if (NOT_SIGNED_IN.matches(trimmed)) {
                return if (projectTokenSet) SignInStatus.ProjectToken else SignInStatus.NotSignedIn
            }
        }

        if (projectTokenSet) {
            return SignInStatus.ProjectToken
        }
        return SignInStatus.Unknown(
            stdout.lines().firstOrNull { it.isNotBlank() }?.trim()
                ?: "covdbg whoami exited with $exitCode and said nothing"
        )
    }
}
