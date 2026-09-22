package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChangeFingerprintTest {
    private fun event(time: String = "18:30") = KovaEvent(
        id = "id",
        dateIso = "2026-09-22",
        dateLabel = "tir. 22.9",
        time = time,
        type = "Korpskveld",
        description = "Øvelse",
        sourceUrl = "https://example.invalid"
    )

    @Test
    fun fingerprintIsStable() {
        assertEquals(
            ChangeFingerprint.of("UllensakerRKH", "added", event()),
            ChangeFingerprint.of("UllensakerRKH", "added", event())
        )
    }

    @Test
    fun matchesBridgeFingerprint() {
        assertEquals(
            "f3f201d80749359fb86a9830",
            ChangeFingerprint.of("UllensakerRKH", "added", event())
        )
    }

    @Test
    fun changedTimeCreatesDifferentFingerprint() {
        val old = event("18:30")
        val first = ChangeFingerprint.of("UllensakerRKH", "changed", event("19:00"), old)
        val second = ChangeFingerprint.of("UllensakerRKH", "changed", event("19:30"), old)
        assertNotEquals(first, second)
    }
}
