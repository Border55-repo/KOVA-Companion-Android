package no.juliannordli.kovacomp
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DataFreshnessTest {
    private val now = Instant.parse("2026-09-25T12:00:00Z")
    @Test fun oldAndUnknownDataCannotLookFresh() {
        assertTrue(dataFreshnessLabel(null, false, now).contains("ukjent"))
        assertTrue(dataFreshnessLabel("2026-09-25T11:00:00Z", false, now).contains("gamle"))
        assertTrue(dataFreshnessLabel("2026-09-25T11:55:00Z", false, now).contains("nylig"))
        assertTrue(dataFreshnessLabel("2026-09-25T11:55:00Z", true, now).contains("lagrede"))
    }
}
