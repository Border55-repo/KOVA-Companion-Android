package no.juliannordli.kovacomp

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object CalendarHelper {
    fun addEvent(context: Context, event: KovaEvent) {
        val date = runCatching { LocalDate.parse(event.dateIso) }.getOrNull() ?: return
        val time = runCatching { LocalTime.parse(event.time) }.getOrElse { LocalTime.of(9, 0) }
        val start = LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L)
            putExtra(CalendarContract.Events.TITLE, event.description)
            putExtra(CalendarContract.Events.DESCRIPTION, "KOVA • " + event.type + "\n" + event.sourceUrl)
        }
        context.startActivity(intent)
    }
}
