package no.juliannordli.kovacomp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object NotificationDedup {
    private const val PREFS = "kova_notification_dedup"
    private const val KEY_JSON = "seen_change_ids_v2"
    private const val LEGACY_KEY = "seen_change_ids"
    private const val MAX_IDS = 1000
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    @Synchronized
    fun shouldNotify(context: Context, changeId: String?): Boolean {
        if (changeId.isNullOrBlank()) return true

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val seen = linkedMapOf<String, Long>()

        val raw = prefs.getString(KEY_JSON, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val at = item.optLong("at")
            if (id.isNotBlank() && now - at <= MAX_AGE_MS) {
                seen[id] = at
            }
        }

        prefs.getStringSet(LEGACY_KEY, emptySet())
            ?.forEach { id -> if (id.isNotBlank() && id !in seen) seen[id] = now }

        if (changeId in seen) return false

        seen[changeId] = now
        val trimmed = seen.entries
            .sortedByDescending { it.value }
            .take(MAX_IDS)

        val out = JSONArray()
        trimmed.forEach { entry ->
            out.put(
                JSONObject().apply {
                    put("id", entry.key)
                    put("at", entry.value)
                }
            )
        }

        prefs.edit()
            .putString(KEY_JSON, out.toString())
            .remove(LEGACY_KEY)
            .apply()
        return true
    }
}
