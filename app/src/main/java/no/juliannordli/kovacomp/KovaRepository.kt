package no.juliannordli.kovacomp

import android.content.Context
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class KovaRepository(private val context: Context, private val authorizedSource: ScheduleSource? = null) {
    companion object {
        const val DEFAULT_ORG = "UllensakerRKH"
        const val BASE_URL = "https://www.kova.no/public/schedule.aspx?Organization="
        const val BRIDGE_BASE =
            "https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data/"
    }

    private val prefs = context.getSharedPreferences("kova_companion", Context.MODE_PRIVATE)

    fun organization(): String = prefs.getString("organization", DEFAULT_ORG) ?: DEFAULT_ORG

    fun setOrganization(value: String) {
        prefs.edit().putString("organization", value.trim()).apply()
    }

    private fun cacheKey(org: String) = "cache_" + org.lowercase()
    private fun syncKey(org: String) = "sync_" + org.lowercase()
    private fun sourceKey(org: String) = "source_" + org.lowercase()

    fun lastChecked(org: String = organization()): String? = prefs.getString("checked_" + org, null)

    fun lastSync(org: String = organization()): Long = prefs.getLong(syncKey(org), 0L)

    fun lastSourceLabel(org: String = organization()): String =
        when (prefs.getString(sourceKey(org), null)) {
            "bridge" -> "KOVA Bridge"
            "direct" -> "Direkte KOVA"
            else -> "Ikke synkronisert"
        }

    fun sourceUrl(org: String = organization()): String =
        BASE_URL + java.net.URLEncoder.encode(org, "UTF-8")

    private fun bridgeSlug(org: String): String =
        org.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')

    fun bridgeUrl(org: String = organization()): String =
        BRIDGE_BASE + bridgeSlug(org) + ".json?ts=" + System.currentTimeMillis()

    private fun upcoming(events: List<KovaEvent>): List<KovaEvent> {
        val today = LocalDate.now()
        return events.filter { event ->
            runCatching { !LocalDate.parse(event.dateIso).isBefore(today) }.getOrDefault(false)
        }
    }

    fun fetch(org: String = organization()): List<KovaEvent> {
        authorizedSource?.let { return upcoming(it.fetch(org)) }
        val events = runCatching {
            fetchBridge(org).also {
                prefs.edit().putString(sourceKey(org), "bridge").apply()
            }
        }.getOrElse {
            fetchDirect(org).also {
                prefs.edit().putString(sourceKey(org), "direct").putString("checked_" + org, java.time.OffsetDateTime.now().toString()).apply()
            }
        }
        return upcoming(events)
    }

    private fun fetchBridge(org: String): List<KovaEvent> {
        val connection = URL(bridgeUrl(org)).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "Kova Companion Android/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        connection.setRequestProperty("Pragma", "no-cache")
        connection.useCaches = false

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                error("Bridge returned HTTP $status")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("status") != "ok") {
                error("Bridge snapshot is not healthy")
            }

            prefs.edit().putString("checked_" + org, root.optString("checkedAt").takeIf { it.isNotBlank() }).apply()
            val array = root.getJSONArray("events")
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                KovaEvent(
                    o.getString("id"),
                    o.getString("dateIso"),
                    o.getString("dateLabel"),
                    o.getString("time"),
                    o.getString("type"),
                    o.getString("description"),
                    o.getString("sourceUrl")
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchDirect(org: String): List<KovaEvent> {
        val url = sourceUrl(org)
        val html = Jsoup.connect(url)
            .userAgent("Kova Companion Android/${BuildConfig.VERSION_NAME}")
            .timeout(15000)
            .get()
            .html()
        return KovaParser.parse(html, url)
    }

    fun loadCache(org: String = organization()): List<KovaEvent> {
        val raw = prefs.getString(cacheKey(org), "[]") ?: "[]"
        return upcoming(decodeCachedEvents(raw))
    }

    fun saveCache(events: List<KovaEvent>, org: String = organization()) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("dateIso", e.dateIso)
                put("dateLabel", e.dateLabel)
                put("time", e.time)
                put("type", e.type)
                put("description", e.description)
                put("sourceUrl", e.sourceUrl)
            })
        }
        prefs.edit()
            .putString(cacheKey(org), arr.toString())
            .putLong(syncKey(org), System.currentTimeMillis())
            .apply()
    }

    fun diff(old: List<KovaEvent>, fresh: List<KovaEvent>): KovaDiff {
        val oldByKey = old.associateBy { it.semanticKey }
        val newByKey = fresh.associateBy { it.semanticKey }
        val changed = (oldByKey.keys intersect newByKey.keys).mapNotNull { key ->
            val a = oldByKey.getValue(key)
            val b = newByKey.getValue(key)
            if (a.dateIso != b.dateIso || a.normalizedTime != b.normalizedTime) {
                EventChange(a, b)
            } else null
        }
        return KovaDiff(
            fresh.filter { it.semanticKey !in oldByKey },
            old.filter { it.semanticKey !in newByKey },
            changed
        )
    }
}

data class EventChange(val old: KovaEvent, val new: KovaEvent)
data class KovaDiff(
    val added: List<KovaEvent>,
    val removed: List<KovaEvent>,
    val changed: List<EventChange>
)
