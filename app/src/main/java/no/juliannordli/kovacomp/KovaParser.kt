package no.juliannordli.kovacomp

import org.jsoup.Jsoup
import java.time.LocalDate

object KovaParser {
    private val knownTypes = setOf(
        "Aksjon","Aktivitet","Ambulansevakt","Båtvakt","Beredskap","Beredskapsvakt","Dugnad",
        "Eksterne kurs","Eksterne møter","Forebygging","Interne kurs","Interne møter",
        "Korpskveld","RØFF","Øvelse","Profilering","Rådsmøte","Sanitetsvakt","Sommervakt","Transport","Vintervakt"
    )

    fun parse(html: String, sourceUrl: String, today: LocalDate = LocalDate.now()): List<KovaEvent> {
        val doc = Jsoup.parse(html, sourceUrl)
        val out = mutableListOf<KovaEvent>()
        var lastDateText: String? = null
        var year = today.year
        var lastMonth = today.monthValue

        for (row in doc.select("tr")) {
            val cells = row.select("th,td").map { it.text().trim() }.filter { it.isNotBlank() }
            if (cells.isEmpty()) continue
            val typeIndex = cells.indexOfFirst { c -> knownTypes.any { it.equals(c, true) } }
            if (typeIndex < 0) continue
            val before = cells.take(typeIndex)
            val time = before.lastOrNull {
                it.matches(Regex("(?:->\\s*)?\\d{1,2}:\\d{2}"))
            }?.trim().orEmpty()
            before.lastOrNull { Regex(".*\\d{1,2}\\.\\d{1,2}.*").matches(it) }?.let { lastDateText = it }
            val dateText = lastDateText ?: continue
            val m = Regex("(\\d{1,2})\\.(\\d{1,2})").find(dateText) ?: continue
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            if (month < lastMonth - 6) year++
            lastMonth = month
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
            val type = cells[typeIndex]
            val description = cells.drop(typeIndex + 1).joinToString(" ").ifBlank { type }
            val normalizedTime = Regex("\\d{1,2}:\\d{2}").find(time)?.value.orEmpty()
            val normalized = date.toString() + "|" + normalizedTime + "|" + type + "|" + description
            val id = normalized.hashCode().toUInt().toString(16)
            out += KovaEvent(id,date.toString(),dateText,time,type,description,sourceUrl)
        }
        return out.distinctBy { it.id }.sortedWith(compareBy({ it.dateIso }, { it.time }))
    }
}
