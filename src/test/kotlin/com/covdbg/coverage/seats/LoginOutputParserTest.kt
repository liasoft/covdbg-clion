package com.covdbg.coverage.seats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoginOutputParserTest {

    private fun eventsOf(vararg lines: String): List<LoginEvent> {
        val parser = LoginOutputParser()
        return lines.mapNotNull { parser.accept(it) }
    }

    @Test
    fun `reads the device code out of the two-line prompt`() {
        // covdbg writes two spaces of indent, and two spaces after the colon.
        val events = eventsOf(
            "",
            "  Open https://app.covdbg.com/device?code=WXYZ-1234",
            "  and confirm the code there:  WXYZ-1234",
            "",
            "Waiting for you to finish..."
        )

        val code = assertInstanceOf(LoginEvent.DeviceCode::class.java, events.first())
        assertEquals("https://app.covdbg.com/device?code=WXYZ-1234", code.url)
        assertEquals("WXYZ-1234", code.code)
        assertEquals(LoginEvent.Waiting, events[1])
    }

    @Test
    fun `reports an existing session before the prompt`() {
        val events = eventsOf(
            "Already signed in as dev@example.com. Signing in again replaces it.",
            "  Open https://app.covdbg.com/device",
            "  and confirm the code there:  ABCD-9999"
        )

        val already = assertInstanceOf(LoginEvent.AlreadySignedIn::class.java, events[0])
        assertEquals("dev@example.com", already.email)
        assertInstanceOf(LoginEvent.DeviceCode::class.java, events[1])
    }

    @Test
    fun `reads the signed-in line with an email`() {
        val events = eventsOf("Signed in as dev@example.com.")
        val signedIn = assertInstanceOf(LoginEvent.SignedIn::class.java, events.single())
        assertEquals("dev@example.com", signedIn.email)
        assertNull(signedIn.teamName)
    }

    @Test
    fun `reads the team name from the signed-in line`() {
        val signedIn = assertInstanceOf(
            LoginEvent.SignedIn::class.java,
            eventsOf("Signed in as dev@example.com for Team for Acme.").single()
        )
        assertEquals("dev@example.com", signedIn.email)
        assertEquals("Team for Acme", signedIn.teamName)
    }

    @Test
    fun `an existing session with a team reports only the email`() {
        val events = eventsOf("Already signed in as dev@example.com for Acme. Signing in again replaces it.")
        assertEquals("dev@example.com", assertInstanceOf(LoginEvent.AlreadySignedIn::class.java, events.single()).email)
    }

    @Test
    fun `reads the signed-in line when the service returned no email`() {
        val events = eventsOf("Signed in.")
        val signedIn = assertInstanceOf(LoginEvent.SignedIn::class.java, events.single())
        assertNull(signedIn.email)
        assertNull(signedIn.teamName)
    }

    @Test
    fun `a code without a preceding url produces nothing`() {
        // Defensive: emitting a prompt with no page to open would be worse than staying quiet.
        val parser = LoginOutputParser()
        assertNull(parser.accept("  and confirm the code there:  WXYZ-1234"))
    }

    @Test
    fun `ignores unrelated chatter`() {
        val parser = LoginOutputParser()
        assertNull(parser.accept(""))
        assertNull(parser.accept("Seats, teams and your personal lock are managed at https://app.covdbg.com"))
    }

    @Test
    fun `a full transcript yields exactly the interesting events, in order`() {
        val events = eventsOf(
            "Already signed in as old@example.com. Signing in again replaces it.",
            "",
            "  Open https://app.covdbg.com/device",
            "  and confirm the code there:  QRST-5678",
            "",
            "Waiting for you to finish...",
            "Signed in as new@example.com.",
            "Seats, teams and your personal lock are managed at https://app.covdbg.com"
        )

        assertEquals(4, events.size)
        assertInstanceOf(LoginEvent.AlreadySignedIn::class.java, events[0])
        assertInstanceOf(LoginEvent.DeviceCode::class.java, events[1])
        assertEquals(LoginEvent.Waiting, events[2])
        assertEquals("new@example.com", assertInstanceOf(LoginEvent.SignedIn::class.java, events[3]).email)
    }
}

class LogoutOutputParserTest {

    @Test
    fun `reads a completed sign-out`() {
        val result = LogoutOutputParser.parse(
            "Signed out dev@example.com.\nYour seats and personal lock stay as they are; manage them at https://app.covdbg.com\n"
        )
        val signedOut = assertInstanceOf(LogoutOutputParser.Result.SignedOut::class.java, result)
        assertEquals("dev@example.com", signedOut.email)
    }

    @Test
    fun `distinguishes never having been signed in`() {
        // covdbg exits 0 in this case too, so only the text tells the two apart.
        assertEquals(
            LogoutOutputParser.Result.WasNotSignedIn,
            LogoutOutputParser.parse("Not signed in.\n")
        )
    }

    @Test
    fun `anything else is unknown, keeping the first line said`() {
        val result = LogoutOutputParser.parse("Could not remove the stored sign-in at Windows Credential Manager\n")
        val unknown = assertInstanceOf(LogoutOutputParser.Result.Unknown::class.java, result)
        assertEquals("Could not remove the stored sign-in at Windows Credential Manager", unknown.detail)
    }
}
