package no.juliannordli.kovacomp

import android.content.Intent

data class NotificationTarget(
    val organization: String,
    val eventId: String,
    val kind: String,
    val dateIso: String,
    val dateLabel: String,
    val time: String,
    val type: String,
    val description: String,
    val sourceUrl: String,
    val changeSummary: String = ""
) {
    fun toEvent(): KovaEvent = KovaEvent(
        id = eventId,
        dateIso = dateIso,
        dateLabel = dateLabel,
        time = time,
        type = type,
        description = description,
        sourceUrl = sourceUrl
    )

    companion object {
        const val EXTRA_ORG = "kova_org"
        const val EXTRA_EVENT_ID = "kova_event_id"
        const val EXTRA_KIND = "kova_kind"
        const val EXTRA_DATE_ISO = "kova_date_iso"
        const val EXTRA_DATE_LABEL = "kova_date_label"
        const val EXTRA_TIME = "kova_time"
        const val EXTRA_TYPE = "kova_type"
        const val EXTRA_DESCRIPTION = "kova_description"
        const val EXTRA_SOURCE_URL = "kova_source_url"
        const val EXTRA_CHANGE_SUMMARY = "kova_change_summary"

        fun fromIntent(intent: Intent?): NotificationTarget? {
            intent ?: return null
            val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return null
            return NotificationTarget(
                organization = intent.getStringExtra(EXTRA_ORG) ?: KovaRepository.DEFAULT_ORG,
                eventId = eventId,
                kind = intent.getStringExtra(EXTRA_KIND) ?: "unknown",
                dateIso = intent.getStringExtra(EXTRA_DATE_ISO) ?: "",
                dateLabel = intent.getStringExtra(EXTRA_DATE_LABEL) ?: "",
                time = intent.getStringExtra(EXTRA_TIME) ?: "",
                type = intent.getStringExtra(EXTRA_TYPE) ?: "Aktivitet",
                description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: "KOVA-aktivitet",
                sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL)
                    ?: KovaRepository.BASE_URL + "UllensakerRKH",
                changeSummary = intent.getStringExtra(EXTRA_CHANGE_SUMMARY) ?: ""
            )
        }
    }
}
