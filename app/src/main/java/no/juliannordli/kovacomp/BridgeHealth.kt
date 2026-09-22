package no.juliannordli.kovacomp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BridgeHealth(
    val status: String,
    val checkedAt: String?,
    val pendingPushes: Int = 0,
    val consecutiveFailureRuns: Int = 0,
    val lastPushAt: String? = null,
    val source: String = "bridge-health"
) {
    val isHealthy: Boolean
        get() = status == "ok"

    private fun formatTime(value: String?): String? {
        value ?: return null
        return runCatching {
            OffsetDateTime.parse(value)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        }.getOrNull()
    }

    fun label(): String {
        val time = formatTime(checkedAt)
        val queue = if (pendingPushes > 0) " • $pendingPushes i kø" else ""

        return when (status) {
            "ok" -> if (time != null) "Bridge: OK • $time$queue" else "Bridge: OK$queue"
            "degraded" -> if (time != null) "Bridge: redusert • $time$queue" else "Bridge: redusert$queue"
            "error" -> if (time != null) "Bridge: FEIL • $time$queue" else "Bridge: FEIL$queue"
            "running" -> "Bridge: kjører$queue"
            else -> "Bridge: ukjent$queue"
        }
    }
}

object BridgeHealthRepository {
    private const val HEALTH_URL =
        "https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data/health.json"

    private const val ACTIONS_URL =
        "https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml/runs?per_page=1"

    fun fetch(): BridgeHealth {
        return runCatching { fetchBridgeHealth() }
            .getOrElse { fetchActionsFallback() }
    }

    private fun fetchBridgeHealth(): BridgeHealth {
        val connection = URL(HEALTH_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.7.0")

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Bridge health HTTP $code")

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)

            return BridgeHealth(
                status = root.optString("status", "unknown"),
                checkedAt = root.optString("checkedAt").takeIf { it.isNotBlank() },
                pendingPushes = root.optInt("pendingPushes", 0),
                consecutiveFailureRuns = root.optInt("consecutiveFailureRuns", 0),
                lastPushAt = root.optString("lastPushAt").takeIf {
                    it.isNotBlank() && it != "null"
                },
                source = "bridge-health"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchActionsFallback(): BridgeHealth {
        val connection = URL(ACTIONS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.7.0")

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub health HTTP $code")

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val runs = root.getJSONArray("workflow_runs")
            if (runs.length() == 0) {
                return BridgeHealth("unknown", null, source = "actions-fallback")
            }

            val run = runs.getJSONObject(0)
            val runStatus = run.optString("status", "unknown")
            val conclusion = run.optString("conclusion")
            val status = when {
                runStatus == "in_progress" || runStatus == "queued" -> "running"
                runStatus == "completed" && conclusion == "success" -> "ok"
                runStatus == "completed" -> "error"
                else -> "unknown"
            }

            return BridgeHealth(
                status = status,
                checkedAt = run.optString("updated_at").takeIf { it.isNotBlank() },
                source = "actions-fallback"
            )
        } finally {
            connection.disconnect()
        }
    }
}
