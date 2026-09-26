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

    data class SignedIn(val email: String) : SignInStatus {
        override val label get() = email
        override val tooltip get() = "Signed in to covdbg as $email"
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
 * Parses `covdbg whoami`.
 *
 * covdbg prints `Signed in as <email>.` and exits 0, or `Not signed in.` and exits **1**.
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
                return SignInStatus.SignedIn(match.groupValues[1].trim())
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
