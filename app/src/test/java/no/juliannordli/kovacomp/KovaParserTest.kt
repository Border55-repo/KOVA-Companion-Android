package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class KovaParserTest {
    @Test
    fun parsesSchedule() {
        val html = """
            <table>
              <tr><td>sep</td><td>39</td><td>tir. 22.9</td><td>18:30</td><td>Korpskveld</td><td>Øvelse med EHRKH</td></tr>
              <tr><td></td><td></td><td>ons. 23.9</td><td>18:00</td><td>Rådsmøte</td><td>Lokalrådsmøte</td></tr>
            </table>
        """.trimIndent()

        val events = KovaParser.parse(
            html,
            "https://example.invalid",
            LocalDate.of(2026, 9, 22)
        )

        assertEquals(2, events.size)
        assertEquals("2026-09-22", events[0].dateIso)
        assertEquals("18:30", events[0].time)
    }

    @Test
    fun keepsActivityWithoutTime() {
        val html = """
            <table>
              <tr><td>tir. 22.9</td><td>Aktivitet</td><td>Materiellkontroll</td></tr>
            </table>
        """.trimIndent()

        val event = KovaParser.parse(
            html,
            "https://example.invalid",
            LocalDate.of(2026, 9, 22)
        ).single()

        assertEquals("", event.time)
        assertEquals("Tid ikke oppgitt", event.displayTime)
    }

    @Test
    fun preservesKovaEndTimeMarker() {
        val html = """
            <table>
              <tr><td>tir. 22.9</td><td>-> 17:00</td><td>Aktivitet</td><td>ATV-kurs</td></tr>
            </table>
        """.trimIndent()

        val event = KovaParser.parse(
            html,
            "https://example.invalid",
            LocalDate.of(2026, 9, 22)
        ).single()

        assertEquals("-> 17:00", event.time)
        assertEquals("17:00", event.normalizedTime)
        assertEquals("Til 17:00", event.displayTime)
    }
}
