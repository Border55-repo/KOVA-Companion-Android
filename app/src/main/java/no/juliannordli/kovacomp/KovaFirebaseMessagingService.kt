package no.juliannordli.kovacomp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KovaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "KOVA Companion"
        val body = data["body"] ?: message.notification?.body ?: "Det er en ny oppdatering i KOVA."
        val kind = data["kind"] ?: "unknown"
        val eventType = data["eventType"] ?: ""

        val settings = AppSettings(this)
        val organization = data["organization"] ?: KovaRepository.DEFAULT_ORG

        PushDiagnostics.recordReceived(this, kind, organization, eventType)

        if (!settings.isOrganizationSubscribed(organization)) {
            PushDiagnostics.recordStatus(this, "filtrert: korps ikke fulgt")
            return
        }
        if (!settings.isKindEnabled(kind)) {
            val reason = when (kind) {
                "added" -> "filtrert: Nye aktiviteter er av"
                "changed" -> "filtrert: Endrede aktiviteter er av"
                "removed" -> "filtrert: Fjernede aktiviteter er av"
                else -> "filtrert: varseltype er av"
            }
            PushDiagnostics.recordStatus(this, reason)
            return
        }
        if (!settings.isEventTypeEnabled(eventType)) {
            PushDiagnostics.recordStatus(this, "filtrert: aktivitetstype er av")
            return
        }

        PushDiagnostics.recordStatus(this, "godkjent av lokale filtre")

        val eventId = data["eventId"]
        val target = if (!eventId.isNullOrBlank()) {
            NotificationTarget(
                organization = organization,
                eventId = eventId,
                kind = kind,
                dateIso = data["dateIso"] ?: "",
                dateLabel = data["dateLabel"] ?: "",
                time = data["time"] ?: "",
                type = eventType.ifBlank { "Aktivitet" },
                description = data["description"] ?: body,
                sourceUrl = data["sourceUrl"] ?: KovaRepository.BASE_URL + "UllensakerRKH"
            )
        } else {
            null
        }

        NotificationHelper.post(
            this,
            title,
            body,
            target,
            data["changeId"]
        )
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("kova_push", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}
