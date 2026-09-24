package no.juliannordli.kovacomp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KovaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Kova Companion"
        val body = data["body"] ?: message.notification?.body ?: "Det er en ny oppdatering i KOVA."
        val kind = data["kind"] ?: "unknown"
        val eventType = data["eventType"] ?: ""
        val changeId = data["changeId"] ?: ""

        val settings = AppSettings(this)
        val organization = data["organization"] ?: KovaRepository.DEFAULT_ORG
        val eventId = data["eventId"] ?: ""

        PushDiagnostics.recordReceived(this, kind, organization, eventType)

        fun filtered(reason: String) {
            PushDiagnostics.recordStatus(this, reason)
            NotificationHistoryStore.record(
                this,
                title,
                body,
                kind,
                organization,
                eventId,
                "filtered",
                changeId
            )
        }

        if (!NotificationPolicy.allowsOrganization(kind, organization, settings.favoriteOrganizations)) {
            filtered("filtrert: korps er ikke favoritt")
            return
        }
        if (!settings.isKindEnabled(kind)) {
            val reason = when (kind) {
                "added" -> "filtrert: Nye aktiviteter er av"
                "changed" -> "filtrert: Endrede aktiviteter er av"
                "removed" -> "filtrert: Fjernede aktiviteter er av"
                else -> "filtrert: varseltype er av"
            }
            filtered(reason)
            return
        }
        if (!settings.isEventTypeEnabled(eventType)) {
            filtered("filtrert: aktivitetstype er av")
            return
        }
        if (kind != "reminder" && settings.isQuietNow()) {
            filtered("filtrert: stille periode")
            return
        }

        PushDiagnostics.recordStatus(this, "godkjent av lokale filtre")

        val target = if (eventId.isNotBlank()) {
            NotificationTarget(
                organization = organization,
                eventId = eventId,
                kind = kind,
                dateIso = data["dateIso"] ?: "",
                dateLabel = data["dateLabel"] ?: "",
                time = data["time"] ?: "",
                type = eventType.ifBlank { "Aktivitet" },
                description = data["description"] ?: body,
                sourceUrl = data["sourceUrl"] ?: KovaRepository.BASE_URL + organization,
                changeSummary = data["changeSummary"] ?: ""
            )
        } else {
            null
        }

        NotificationHelper.post(
            this,
            title,
            body,
            target,
            changeId.ifBlank { null }
        )
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("kova_push", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}
