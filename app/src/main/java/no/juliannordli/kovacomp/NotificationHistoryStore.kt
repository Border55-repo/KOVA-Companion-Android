package no.juliannordli.kovacomp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NotificationHistoryItem(
    val timestamp: Long,
    val title: String,
    val body: String,
    val kind: String,
    val organization: String,
    val eventId: String,
    val state: String,
    val changeId: String
)

object NotificationHistoryStore {
    private const val PREFS = "kova_notification_history"
    private const val KEY = "items"
    private const val MAX_ITEMS = 100

    @Synchronized
    fun record(
        context: Context,
        title: String,
        body: String,
        kind: String = "unknown",
        organization: String = "",
        eventId: String = "",
        state: String = "delivered",
        changeId: String = ""
    ) {
        val current = list(context).toMutableList()
        current.add(
            0,
            NotificationHistoryItem(
                timestamp = System.currentTimeMillis(),
                title = title,
                body = body,
                kind = kind,
                organization = organization,
                eventId = eventId,
                state = state,
                changeId = changeId
            )
        )
        save(context, current.take(MAX_ITEMS))
    }

    fun list(context: Context): List<NotificationHistoryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            NotificationHistoryItem(
                timestamp = item.optLong("timestamp"),
                title = item.optString("title"),
                body = item.optString("body"),
                kind = item.optString("kind"),
                organization = item.optString("organization"),
                eventId = item.optString("eventId"),
                state = item.optString("state"),
                changeId = item.optString("changeId")
            )
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    private fun save(context: Context, items: List<NotificationHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("timestamp", item.timestamp)
                    put("title", item.title)
                    put("body", item.body)
                    put("kind", item.kind)
                    put("organization", item.organization)
                    put("eventId", item.eventId)
                    put("state", item.state)
                    put("changeId", item.changeId)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
