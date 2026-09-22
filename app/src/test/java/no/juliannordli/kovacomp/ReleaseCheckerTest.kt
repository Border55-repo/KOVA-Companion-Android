package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckerTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(ReleaseChecker.compareVersions("v0.6.1", "0.6.0") > 0)
        assertTrue(ReleaseChecker.compareVersions("1.0.0", "0.9.9") > 0)
        assertEquals(0, ReleaseChecker.compareVersions("v0.6.0", "0.6.0"))
        assertTrue(ReleaseChecker.compareVersions("0.5.9", "0.6.0") < 0)
    }

    @Test
    fun ignoresPrereleaseSuffixForNumericComparison() {
        assertEquals(0, ReleaseChecker.compareVersions("v0.6.0-beta1", "0.6.0"))
    }
}
