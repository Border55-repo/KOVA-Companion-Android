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

    @Test
    fun comparesPatchVersionsUsedByReleaseCandidates() {
        assertTrue(ReleaseChecker.compareVersions("v0.13.2", "0.13.1") > 0)
        assertTrue(ReleaseChecker.compareVersions("0.13.1", "v0.13.2") < 0)
        assertEquals(0, ReleaseChecker.compareVersions("v0.13.2", "0.13.2"))
    }

    @Test
    fun parsesPublicUpdateManifest() {
        val release = ReleaseChecker.parseManifest(
            """
            {
              "schemaVersion": 1,
              "tagName": "v0.13.2",
              "name": "KOVA Companion v0.13.2",
              "notes": "RC update",
              "publishedAt": "2026-09-23T07:30:00Z",
              "htmlUrl": "https://github.com/Border55-repo/KOVA-Companion-Android/releases/tag/v0.13.2",
              "apkUrl": "https://github.com/Border55-repo/KOVA-Companion-Android/releases/download/v0.13.2/KOVA-Companion-v0.13.2.apk"
            }
            """.trimIndent()
        )

        assertEquals("v0.13.2", release.tagName)
        assertEquals("KOVA Companion v0.13.2", release.name)
        assertEquals(
            "https://github.com/Border55-repo/KOVA-Companion-Android/releases/download/v0.13.2/KOVA-Companion-v0.13.2.apk",
            release.apkUrl
        )
        assertTrue(release.isNewerThan("0.13.1"))
    }
}
