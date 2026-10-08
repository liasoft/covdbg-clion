package com.covdbg.coverage.seats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class WhoamiParserTest {

    @Test
    fun `exit 0 with an email is signed in`() {
        val status = WhoamiParser.parse(0, "Signed in as dev@example.com.\n", projectTokenSet = false)
        assertEquals(SignInStatus.SignedIn("dev@example.com"), status)
    }

    @Test
    fun `exit 1 with the refusal line is not signed in`() {
        // covdbg exits 1 here, which is why the exit code alone cannot drive this.
        val status = WhoamiParser.parse(1, "Not signed in.\n", projectTokenSet = false)
        assertEquals(SignInStatus.NotSignedIn, status)
    }

    @Test
    fun `not signed in is inconclusive when a project token is present`() {
        // whoami reads only the stored session and ignores COVDBG_PROJECT_TOKEN, so in CI it says
        // nobody is signed in while runs are perfectly licensed. Reporting that as "not signed in"
        // would send people to sign in for no reason.
        val status = WhoamiParser.parse(1, "Not signed in.\n", projectTokenSet = true)
        assertEquals(SignInStatus.ProjectToken, status)
    }

    @Test
    fun `a stored session still wins over the token for display purposes`() {
        val status = WhoamiParser.parse(0, "Signed in as dev@example.com.\n", projectTokenSet = true)
        assertEquals(SignInStatus.SignedIn("dev@example.com"), status)
    }

    @Test
    fun `unparseable output is unknown, not a refusal`() {
        val status = WhoamiParser.parse(-1, "covdbg.exe is not recognized\n", projectTokenSet = false)
        val unknown = assertInstanceOf(SignInStatus.Unknown::class.java, status)
        assertEquals("covdbg.exe is not recognized", unknown.reason)
    }

    @Test
    fun `silence is unknown and names the exit code`() {
        val unknown = assertInstanceOf(
            SignInStatus.Unknown::class.java,
            WhoamiParser.parse(3, "", projectTokenSet = false)
        )
        assertEquals("covdbg whoami exited with 3 and said nothing", unknown.reason)
    }

    @Test
    fun `reads the team name that covdbg 1_4 appends`() {
        val status = WhoamiParser.parse(0, "Signed in as dev@example.com for Acme.
", projectTokenSet = false)
        assertEquals(SignInStatus.SignedIn("dev@example.com", "Acme"), status)
        assertEquals("dev@example.com · Acme", status.label)
    }

    @Test
    fun `a team name containing for splits at the first for`() {
        val status = WhoamiParser.parse(0, "Signed in as dev@example.com for Team for Acme.
", projectTokenSet = false)
        assertEquals(SignInStatus.SignedIn("dev@example.com", "Team for Acme"), status)
    }

    @Test
    fun `without a team the label is just the email`() {
        assertEquals("dev@example.com", SignInStatus.SignedIn("dev@example.com").label)
    }
}
