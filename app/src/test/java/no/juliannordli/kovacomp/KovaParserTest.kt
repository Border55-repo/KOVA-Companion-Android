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
    }
}
