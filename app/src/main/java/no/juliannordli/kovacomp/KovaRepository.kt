package no.juliannordli.kovacomp

import android.content.Context
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

class KovaRepository(private val context: Context) {
    companion object {
        const val DEFAULT_ORG = "UllensakerRKH"
        const val BASE_URL = "https://www.kova.no/public/schedule.aspx?Organization="
    }

    private val prefs = context.getSharedPreferences("kova_companion", Context.MODE_PRIVATE)

    fun organization(): String = prefs.getString("organization", DEFAULT_ORG) ?: DEFAULT_ORG

    fun setOrganization(value: String) {
        prefs.edit().putString("organization", value.trim()).apply()
    }

    private fun cacheKey(org: String) = "cache_" + org.lowercase()
    private fun syncKey(org: String) = "sync_" + org.lowercase()

    fun lastSync(org: String = organization()): Long = prefs.getLong(syncKey(org), 0L)

    fun sourceUrl(org: String = organization()): String =
        BASE_URL + java.net.URLEncoder.encode(org, "UTF-8")

    fun fetch(org: String = organization()): List<KovaEvent> {
        val url = sourceUrl(org)
        val html = Jsoup.connect(url)
            .userAgent("KOVA Companion Android/0.1.1")
            .timeout(15000)
            .get()
            .html()
        return KovaParser.parse(html, url)
    }

    fun loadCache(org: String = organization()): List<KovaEvent> {
        val raw = prefs.getString(cacheKey(org), "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
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
            if (a.dateIso != b.dateIso || a.time != b.time) EventChange(a, b) else null
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
