package no.juliannordli.kovacomp

import java.security.MessageDigest
import java.util.Locale

object ChangeFingerprint {
    private fun normalize(value: String): String =
        Regex("\\s+")
            .replace(value.trim().lowercase(Locale.ROOT), " ")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun of(
        organization: String,
        kind: String,
        event: KovaEvent,
        oldEvent: KovaEvent? = null
    ): String {
        val oldDate: String
        val oldTime: String
        val newDate: String
        val newTime: String

        if (kind == "removed") {
            oldDate = event.dateIso
            oldTime = event.time
            newDate = ""
            newTime = ""
        } else {
            oldDate = oldEvent?.dateIso ?: ""
            oldTime = oldEvent?.time ?: ""
            newDate = event.dateIso
            newTime = event.time
        }

        val raw = listOf(
            organization,
            kind,
            normalize(event.type),
            normalize(event.description),
            oldDate,
            oldTime,
            newDate,
            newTime
        ).joinToString("|")

        return sha256(raw).take(24)
    }
}
