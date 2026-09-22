package no.juliannordli.kovacomp

import java.time.LocalDate

enum class MyActivitiesRange(val label: String) {
    ALL("Alle"),
    WEEK("7 dager"),
    MONTH("30 dager")
}

object MyActivities {
    fun filter(
        items: List<FavoriteActivity>,
        range: MyActivitiesRange,
        today: LocalDate = LocalDate.now()
    ): List<FavoriteActivity> {
        val end = when (range) {
            MyActivitiesRange.ALL -> null
            MyActivitiesRange.WEEK -> today.plusDays(7)
            MyActivitiesRange.MONTH -> today.plusDays(30)
        }

        return items
            .filter { favorite ->
                val date = runCatching {
                    LocalDate.parse(favorite.event.dateIso)
                }.getOrNull() ?: return@filter true

                !date.isBefore(today) &&
                    (end == null || !date.isAfter(end))
            }
            .sortedWith(
                compareBy(
                    { it.event.dateIso },
                    { it.event.time },
                    { it.event.description.lowercase() }
                )
            )
    }
}
