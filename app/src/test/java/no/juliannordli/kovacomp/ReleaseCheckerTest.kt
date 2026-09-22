package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckerTest {
    @Test
    fun parsesUpdateManifest() {
        val release = ReleaseChecker.parseManifest(
            """{
                "tagName":"v1.2.3",
                "name":"KOVA Companion v1.2.3",
                "notes":"test",
                "publishedAt":"2026-09-22T00:00:00Z",
                "htmlUrl":"https://example.invalid/release",
                "apkUrl":"https://example.invalid/app.apk"
            }"""
        )

        assertEquals("v1.2.3", release.tagName)
        assertEquals("https://example.invalid/app.apk", release.apkUrl)
    }

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
