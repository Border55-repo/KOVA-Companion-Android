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
    @Test
    fun monthFilterIncludesThirtyDayBoundary() {
        val today = LocalDate.of(2026, 9, 23)
        val items = listOf(
            favorite("2026-10-23", "Boundary"),
            favorite("2026-10-24", "Too late")
        )

        val result = MyActivities.filter(
            items,
            MyActivitiesRange.MONTH,
            today
        )

        assertEquals(listOf("Boundary"), result.map { it.event.description })
    }

    @Test
    fun allRangeKeepsAllUpcomingFavoritesAndExcludesPast() {
        val today = LocalDate.of(2026, 9, 23)
        val items = listOf(
            favorite("2026-09-22", "Past"),
            favorite("2026-09-23", "Today"),
            favorite("2027-01-01", "Future")
        )

        val result = MyActivities.filter(
            items,
            MyActivitiesRange.ALL,
            today
        )

        assertEquals(listOf("Today", "Future"), result.map { it.event.description })
    }
}
