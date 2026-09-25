package no.juliannordli.kovacomp

/** Normalized client boundary. Never embed API client secrets. */
fun interface ScheduleSource {
    fun fetch(organization: String): List<KovaEvent>
}

fun dataFreshnessLabel(checkedAt: String?, offline: Boolean, now: java.time.Instant = java.time.Instant.now()): String {
    if (offline) return "Kunne ikke oppdatere – viser lagrede data"
    val checked = runCatching { java.time.OffsetDateTime.parse(checkedAt).toInstant() }.getOrNull()
        ?: return "Sist kontrollert: ukjent"
    return if (java.time.Duration.between(checked, now).toMinutes() > 35)
        "Data kan være gamle – siste kontroll er over 35 minutter siden"
    else "Data kontrollert nylig"
}
