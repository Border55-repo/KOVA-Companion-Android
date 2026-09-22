package no.juliannordli.kovacomp

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

object CalendarHelper {
    fun addEvent(context: Context, event: KovaEvent) {
        val date = runCatching { LocalDate.parse(event.dateIso) }.getOrNull() ?: return
        val parsedTime = event.time
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.description)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "KOVA • " + event.type + "\n" + event.sourceUrl
            )

            if (parsedTime == null) {
                val start = date.atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
                val end = date.plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()

                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                putExtra(CalendarContract.Events.ALL_DAY, true)
            } else {
                val start = LocalDateTime.of(date, parsedTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                putExtra(
                    CalendarContract.EXTRA_EVENT_END_TIME,
                    start + 60 * 60 * 1000L
                )
            }
        }
        context.startActivity(intent)
    }
}
