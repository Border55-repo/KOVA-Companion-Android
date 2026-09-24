package no.juliannordli.kovacomp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChangelogInfo(
    val title: String,
    val body: String,
    val updatedAt: String?
)

object ChangelogRepository {
    private const val URL_VALUE =
        "https://firestore.googleapis.com/v1/projects/kova-companion/databases/(default)/documents/publicConfig/changelog"

    fun fetch(): ChangelogInfo? {
        val connection = URL(URL_VALUE).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val fields = json.optJSONObject("fields") ?: return null
            ChangelogInfo(
                title = fields.optJSONObject("title")?.optString("stringValue").orEmpty(),
                body = fields.optJSONObject("body")?.optString("stringValue").orEmpty(),
                updatedAt = fields.optJSONObject("updatedAt")?.optString("timestampValue")
            ).takeIf { it.title.isNotBlank() || it.body.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
}
