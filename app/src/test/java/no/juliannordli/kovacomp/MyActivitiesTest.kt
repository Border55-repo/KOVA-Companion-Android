package no.juliannordli.kovacomp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MyActivitiesTest {
    private fun favorite(date: String, name: String) = FavoriteActivity(
        organization = "UllensakerRKH",
        event = KovaEvent(
            id = name,
            dateIso = date,
            dateLabel = date,
            time = "18:00",
            type = "Korpskveld",
            description = name,
            sourceUrl = "https://example.invalid"
        )
    )

    @Test
    fun weekFilterOnlyReturnsNextSevenDays() {
        val today = LocalDate.of(2026, 9, 22)
        val items = listOf(
            favorite("2026-09-23", "A"),
            favorite("2026-09-29", "B"),
            favorite("2026-10-10", "C")
        )

        val result = MyActivities.filter(
            items,
            MyActivitiesRange.WEEK,
            today
        )

        assertEquals(listOf("A", "B"), result.map { it.event.description })
    }
}
