package no.juliannordli.kovacomp

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class KovaEvent(
    val id: String,
    val dateIso: String,
    val dateLabel: String,
    val time: String,
    val type: String,
    val description: String,
    val sourceUrl: String
) {
    val normalizedTime: String
        get() = Regex("\\d{1,2}:\\d{2}").find(time)?.value.orEmpty()

    val displayTime: String
        get() = when {
            time.isBlank() -> "Tid ikke oppgitt"
            time.trim().startsWith("->") -> "Til " + normalizedTime.ifBlank { time.trim() }
            else -> time.trim()
        }

    val weekNumber: Int?
        get() = runCatching {
            LocalDate.parse(dateIso).get(WeekFields.ISO.weekOfWeekBasedYear())
        }.getOrNull()

    val monthLabel: String
        get() = runCatching {
            val date = LocalDate.parse(dateIso)
            val month = date.month.getDisplayName(TextStyle.FULL, Locale("nb", "NO"))
            month.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("nb", "NO")) else it.toString()
            } + " " + date.year
        }.getOrDefault("")

    val semanticKey: String
        get() = (type.trim().lowercase() + "|" + description.trim().lowercase())
            .replace(Regex("\\s+"), " ")
}
