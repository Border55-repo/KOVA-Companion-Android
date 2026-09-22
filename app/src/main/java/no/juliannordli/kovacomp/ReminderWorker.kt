package no.juliannordli.kovacomp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val organization = inputData.getString("organization")
            ?: return Result.success()
        val eventId = inputData.getString("eventId")
            ?: return Result.success()

        val event = KovaEvent(
            id = eventId,
            dateIso = inputData.getString("dateIso") ?: "",
            dateLabel = inputData.getString("dateLabel") ?: "",
            time = inputData.getString("time") ?: "",
            type = inputData.getString("type") ?: "Aktivitet",
            description = inputData.getString("description") ?: "KOVA-aktivitet",
            sourceUrl = inputData.getString("sourceUrl")
                ?: KovaRepository.BASE_URL + "UllensakerRKH"
        )

        val stillFavorite = FavoriteStore(applicationContext)
            .isFavorite(organization, event)

        if (!stillFavorite) return Result.success()

        val leadLabel = inputData.getString("leadLabel") ?: "snart"

        NotificationHelper.post(
            applicationContext,
            "KOVA-påminnelse",
            event.description + " starter om " + leadLabel + " • " +
                event.dateLabel + " " + event.time,
            NotificationTarget(
                organization = organization,
                eventId = event.id,
                kind = "reminder",
                dateIso = event.dateIso,
                dateLabel = event.dateLabel,
                time = event.time,
                type = event.type,
                description = event.description,
                sourceUrl = event.sourceUrl
            )
        )

        return Result.success()
    }
}
