package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class KovaEventTimeTest {
    private fun event(time: String) = KovaEvent(
        id = "id",
        dateIso = "2026-09-23",
        dateLabel = "ons. 23.9",
        time = time,
        type = "Korpskveld",
        description = "Test",
        sourceUrl = "https://example.invalid"
    )

    @Test
    fun ordinaryTimeParses() {
        assertEquals(LocalTime.of(18, 30), event("18:30").parsedTime)
    }

    @Test
    fun arrowTimeParsesToClockTime() {
        assertEquals(LocalTime.of(17, 0), event("-> 17:00").parsedTime)
    }

    @Test
    fun blankTimeStaysWithoutClockTime() {
        assertNull(event("").parsedTime)
    }
}
