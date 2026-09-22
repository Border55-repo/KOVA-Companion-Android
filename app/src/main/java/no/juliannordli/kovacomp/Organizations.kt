package no.juliannordli.kovacomp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class KovaOrganization(
    val name: String,
    val code: String,
    val category: String = "hjelpekorps"
)

object Organizations {
    val known = listOf(
        KovaOrganization("Ullensaker Røde Kors Hjelpekorps", "UllensakerRKH"),
        KovaOrganization("Eidsvoll og Hurdal Røde Kors Hjelpekorps", "EHRKH"),
        KovaOrganization("Nittedal Røde Kors Hjelpekorps", "Nittedal RKH"),
        KovaOrganization("Skedsmo Røde Kors Hjelpekorps", "Skedsmo RKH")
    )

    fun nameFor(
        code: String,
        organizations: List<KovaOrganization> = known
    ): String =
        organizations.firstOrNull { it.code == code }?.name
            ?: known.firstOrNull { it.code == code }?.name
            ?: code
}

class OrganizationRegistry(private val context: Context) {
    companion object {
        private const val URL_STRING =
            "https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data/organizations.json"
        private const val PREFS = "kova_organization_registry"
        private const val CACHE = "organizations_json"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadCache(): List<KovaOrganization> {
        val raw = prefs.getString(CACHE, null) ?: return Organizations.known
        return parse(raw).ifEmpty { Organizations.known }
    }

    fun fetch(): List<KovaOrganization> {
        val connection = URL(URL_STRING).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "KOVA Companion Android/0.9.1")

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Organization registry HTTP $code")

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val organizations = parse(body)
            if (organizations.size < 4) {
                error("Organization registry is unexpectedly small")
            }

            prefs.edit().putString(CACHE, body).apply()
            return organizations
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String): List<KovaOrganization> {
        val root = JSONObject(raw)
        val array = root.optJSONArray("organizations") ?: JSONArray()

        return (0 until array.length())
            .mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                val code = item.optString("code").trim()
                val category = item.optString("category", "annet").trim()

                if (name.isBlank() || code.isBlank()) null
                else KovaOrganization(name, code, category)
            }
            .distinctBy { it.code }
            .sortedWith(
                compareBy<KovaOrganization>(
                    { if (it.category == "hjelpekorps") 0 else 1 },
                    { it.name.lowercase() }
                )
            )
    }
}
