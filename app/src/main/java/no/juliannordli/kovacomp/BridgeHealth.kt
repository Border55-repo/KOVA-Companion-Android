package no.juliannordli.kovacomp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BridgeHealth(
    val status: String,
    val conclusion: String?,
    val updatedAt: String?
) {
    val isHealthy: Boolean
        get() = status == "completed" && conclusion == "success"

    fun label(): String {
        val time = updatedAt?.let {
            runCatching {
                Instant.parse(it)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
            }.getOrNull()
        }

        return when {
            isHealthy && time != null -> "Bridge: OK • " + time
            isHealthy -> "Bridge: OK"
            status == "in_progress" || status == "queued" -> "Bridge: kjører"
            conclusion != null -> "Bridge: " + conclusion.uppercase()
            else -> "Bridge: ukjent"
        }
    }
}

object BridgeHealthRepository {
    private const val URL_STRING =
        "https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml/runs?per_page=1"

    fun fetch(): BridgeHealth {
        val connection = URL(URL_STRING).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.5.0")

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub health HTTP " + code)

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val runs = root.getJSONArray("workflow_runs")
            if (runs.length() == 0) return BridgeHealth("unknown", null, null)

            val run = runs.getJSONObject(0)
            return BridgeHealth(
                status = run.optString("status", "unknown"),
                conclusion = run.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = run.optString("updated_at").takeIf { it.isNotBlank() }
            )
        } finally {
            connection.disconnect()
        }
    }
}
