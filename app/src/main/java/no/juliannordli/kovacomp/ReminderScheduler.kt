package no.juliannordli.kovacomp

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val PREFIX = "kova-reminder"

    fun syncFavorite(
        context: Context,
        organization: String,
        event: KovaEvent,
        favorite: Boolean
    ) {
        cancel(context, organization, event)
        if (!favorite) return

        val settings = AppSettings(context)
        if (settings.remind24Hours) {
            schedule(context, organization, event, 24 * 60, "24 timer")
        }
        if (settings.remind2Hours) {
            schedule(context, organization, event, 2 * 60, "2 timer")
        }
    }

    fun rescheduleAll(context: Context) {
        FavoriteStore(context).list().forEach { favorite ->
            syncFavorite(context, favorite.organization, favorite.event, true)
        }
    }

    private fun schedule(
        context: Context,
        organization: String,
        event: KovaEvent,
        leadMinutes: Int,
        leadLabel: String
    ) {
        val eventTime = eventDateTime(event) ?: return
        val trigger = eventTime.minusMinutes(leadMinutes.toLong())
        val now = LocalDateTime.now()
        if (!trigger.isAfter(now)) return

        val delayMs = Duration.between(now, trigger).toMillis()
        if (delayMs <= 0) return

        val data = Data.Builder()
            .putString("organization", organization)
            .putString("eventId", event.id)
            .putString("dateIso", event.dateIso)
            .putString("dateLabel", event.dateLabel)
            .putString("time", event.time)
            .putString("type", event.type)
            .putString("description", event.description)
            .putString("sourceUrl", event.sourceUrl)
            .putString("leadLabel", leadLabel)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tagFor(organization, event))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(organization, event, leadMinutes),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(
        context: Context,
        organization: String,
        event: KovaEvent
    ) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(workName(organization, event, 24 * 60))
        manager.cancelUniqueWork(workName(organization, event, 2 * 60))
    }

    private fun eventDateTime(event: KovaEvent): LocalDateTime? {
        val date = runCatching { LocalDate.parse(event.dateIso) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(event.time) }.getOrNull() ?: return null
        return LocalDateTime.of(date, time)
    }

    private fun safeKey(organization: String, event: KovaEvent): String =
        (organization + "|" + event.semanticKey).hashCode().toUInt().toString()

    private fun workName(
        organization: String,
        event: KovaEvent,
        leadMinutes: Int
    ): String = PREFIX + "-" + safeKey(organization, event) + "-" + leadMinutes

    private fun tagFor(
        organization: String,
        event: KovaEvent
    ): String = PREFIX + "-" + safeKey(organization, event)
}
