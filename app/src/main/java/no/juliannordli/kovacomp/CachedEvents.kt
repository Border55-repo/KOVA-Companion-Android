package no.juliannordli.kovacomp

import org.json.JSONArray

// A damaged cached item must not prevent the app opening or fetching fresh data.
internal fun decodeCachedEvents(raw: String): List<KovaEvent> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            KovaEvent(item.getString("id"), item.getString("dateIso"),
                item.getString("dateLabel"), item.getString("time"),
                item.getString("type"), item.getString("description"), item.getString("sourceUrl"))
        }.getOrNull()
    }
}.getOrDefault(emptyList())
