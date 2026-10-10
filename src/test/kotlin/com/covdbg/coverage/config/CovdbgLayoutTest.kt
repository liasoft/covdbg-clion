package com.covdbg.coverage.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Where a run's database goes. */
class CovdbgLayoutTest {

    @Test
    fun `a target's database is named after the executable`(@TempDir root: File) {
        val covdb = CovdbgLayout.covdbFor(root.path, File(root, "build/tests.exe").path)
        assertEquals(File(root, ".covdbg/tests.covdb"), covdb)
    }

    @Test
    fun `a ctest run's database is named after the configuration`(@TempDir root: File) {
        assertEquals(File(root, ".covdbg/All CTests.covdb"), CovdbgLayout.covdbNamed(root.path, "All CTests"))
    }

    @Test
    fun `characters windows does not allow in a file name become underscores`(@TempDir root: File) {
        assertEquals(
            File(root, ".covdbg/math_add _ sub_.covdb"),
            CovdbgLayout.covdbNamed(root.path, "math/add | sub?")
        )
    }

    @Test
    fun `a name with nothing usable left falls back to coverage`(@TempDir root: File) {
        assertEquals(File(root, ".covdbg/coverage.covdb"), CovdbgLayout.covdbNamed(root.path, "  ..  "))
    }
}
