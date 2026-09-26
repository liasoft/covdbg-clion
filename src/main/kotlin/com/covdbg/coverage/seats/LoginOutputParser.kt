package com.covdbg.coverage.seats

/** A line of interest in `covdbg login`'s output. */
sealed interface LoginEvent {

    data class AlreadySignedIn(val email: String) : LoginEvent

    /** The device-code prompt: open [url] and confirm [code] there. */
    data class DeviceCode(val url: String, val code: String) : LoginEvent

    data object Waiting : LoginEvent

    /** Sign-in completed. covdbg omits the email when the service returned none. */
    data class SignedIn(val email: String?) : LoginEvent
}

/**
 * Parses `covdbg login`'s stdout line by line.
 *
 * covdbg prints the prompt across two lines, so this keeps the URL from the first until the code
 * arrives on the second:
 *
 * ```
 *   Open https://app.covdbg.com/...
 *   and confirm the code there:  WXYZ-1234
 * ```
 *
 * Note the two spaces before the code; the patterns allow any run of whitespace.
 */
class LoginOutputParser {

    private var pendingUrl: String? = null

    fun accept(line: String): LoginEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        ALREADY_SIGNED_IN.find(trimmed)?.let { match ->
            return LoginEvent.AlreadySignedIn(match.groupValues[1].trim())
        }

        OPEN_URL.find(trimmed)?.let { match ->
            pendingUrl = match.groupValues[1]
            return null
        }

        CONFIRM_CODE.find(trimmed)?.let { match ->
            val url = pendingUrl ?: return null
            pendingUrl = null
            return LoginEvent.DeviceCode(url, match.groupValues[1])
        }

        if (WAITING.matches(trimmed)) {
            return LoginEvent.Waiting
        }

        SIGNED_IN.find(trimmed)?.let { match ->
            return LoginEvent.SignedIn(match.groupValues[1].takeIf { it.isNotBlank() }?.trim())
        }

        return null
    }

    private companion object {
        val ALREADY_SIGNED_IN = Regex("""^Already signed in as (.+)\. Signing in again replaces it\.$""")
        val OPEN_URL = Regex("""^Open (\S+)$""")
        val CONFIRM_CODE = Regex("""^and confirm the code there:\s+(\S+)$""")
        val WAITING = Regex("""^Waiting for you to finish\.\.\.$""")
        val SIGNED_IN = Regex("""^Signed in(?: as (.+?))?\.$""")
    }
}

/** Parses `covdbg logout`, which exits 0 whether or not anyone was signed in. */
object LogoutOutputParser {

    private val SIGNED_OUT = Regex("""^Signed out (.+)\.$""")
    private val NOT_SIGNED_IN = Regex("""^Not signed in\.$""")

    sealed interface Result {
        data class SignedOut(val email: String) : Result
        data object WasNotSignedIn : Result
        data class Unknown(val detail: String?) : Result
    }

    fun parse(stdout: String): Result {
        for (line in stdout.lines()) {
            val trimmed = line.trim()
            SIGNED_OUT.find(trimmed)?.let { return Result.SignedOut(it.groupValues[1].trim()) }
            if (NOT_SIGNED_IN.matches(trimmed)) return Result.WasNotSignedIn
        }
        return Result.Unknown(stdout.lines().firstOrNull { it.isNotBlank() }?.trim())
    }
}
