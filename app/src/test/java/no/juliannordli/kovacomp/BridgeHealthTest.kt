package no.juliannordli.kovacomp

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BridgeHealthTest {
    private val now = Instant.parse("2026-09-24T20:00:00Z")
    @Test fun recentSuccessfulRunIsHealthy() {
        assertTrue(BridgeHealth("ok", "2026-09-24T19:30:00Z").isHealthyAt(now))
    }
    @Test fun staleOrMissingStatusCannotAppearHealthy() {
        for (value in listOf(null, "invalid", "2026-09-24T16:00:00Z", "2026-09-25T20:00:00Z")) {
            val health = BridgeHealth("ok", value)
            assertFalse(health.isHealthyAt(now))
            assertTrue(health.label(now).contains("må oppdateres"))
        }
    }
    @Test fun queuedPushIsNotHealthy() {
        assertFalse(BridgeHealth("ok", "2026-09-24T19:30:00Z", pendingPushes=2).isHealthyAt(now))
    }
}
