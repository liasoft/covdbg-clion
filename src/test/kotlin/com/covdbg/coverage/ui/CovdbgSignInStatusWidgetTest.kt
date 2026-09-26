package com.covdbg.coverage.ui

import com.covdbg.coverage.seats.SignInStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The status-bar text: short for every state, and never blank. */
class CovdbgSignInStatusWidgetTest {

    @Test
    fun `signed in shows the account`() {
        assertEquals("dev@example.com", SignInStatus.SignedIn("dev@example.com").shortLabel)
    }

    @Test
    fun `not signed in says so`() {
        assertEquals("Not signed in", SignInStatus.NotSignedIn.shortLabel)
    }

    @Test
    fun `a project token is named rather than described`() {
        assertEquals("COVDBG_PROJECT_TOKEN", SignInStatus.ProjectToken.shortLabel)
    }

    @Test
    fun `an unknown status still shows something to click`() {
        // Unknown's label is blank, which would leave an empty, unclickable widget.
        assertEquals("covdbg", SignInStatus.Unknown("covdbg was not found").shortLabel)
    }
}
