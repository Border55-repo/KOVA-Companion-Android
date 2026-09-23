package no.juliannordli.kovacomp

import android.content.Context
import java.text.DateFormat
import java.util.Date

data class PushDiagnosticSnapshot(
    val kind: String,
    val organization: String,
    val eventType: String,
    val status: String,
    val timestamp: Long
) {
    fun label(): String {
        if (timestamp <= 0L || kind.isBlank()) return "ingen push registrert ennå"
        val time = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
        ).format(Date(timestamp))
        val org = organization.ifBlank { "ukjent korps" }
        val type = eventType.ifBlank { "ukjent type" }
        return "$kind • $org • $type • $status • $time"
    }
}

object PushDiagnostics {
    private const val PREFS = "kova_push_diagnostics"

    fun recordReceived(
        context: Context,
        kind: String,
        organization: String,
        eventType: String
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("kind", kind)
            .putString("organization", organization)
            .putString("event_type", eventType)
            .putString("status", "mottatt")
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
    }

    fun recordStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("status", status)
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
    }

    fun snapshot(context: Context): PushDiagnosticSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PushDiagnosticSnapshot(
            kind = prefs.getString("kind", "").orEmpty(),
            organization = prefs.getString("organization", "").orEmpty(),
            eventType = prefs.getString("event_type", "").orEmpty(),
            status = prefs.getString("status", "").orEmpty(),
            timestamp = prefs.getLong("timestamp", 0L)
        )
    }
}
