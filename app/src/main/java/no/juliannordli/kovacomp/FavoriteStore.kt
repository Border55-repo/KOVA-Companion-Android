package no.juliannordli.kovacomp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteActivity(
    val organization: String,
    val event: KovaEvent
) {
    val key: String
        get() = organization + "|" + event.semanticKey
}

class FavoriteStore(private val context: Context) {
    companion object {
        private const val PREFS = "kova_favorites"
        private const val KEY = "favorites"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<FavoriteActivity> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val organization = item.optString("organization")
            val event = item.optJSONObject("event") ?: return@mapNotNull null
            if (organization.isBlank()) return@mapNotNull null

            runCatching {
                FavoriteActivity(
                    organization = organization,
                    event = KovaEvent(
                        id = event.getString("id"),
                        dateIso = event.getString("dateIso"),
                        dateLabel = event.getString("dateLabel"),
                        time = event.getString("time"),
                        type = event.getString("type"),
                        description = event.getString("description"),
                        sourceUrl = event.getString("sourceUrl")
                    )
                )
            }.getOrNull()
        }
    }

    fun isFavorite(organization: String, event: KovaEvent): Boolean =
        list().any {
            it.organization == organization &&
                it.event.semanticKey == event.semanticKey
        }

    fun setFavorite(
        organization: String,
        event: KovaEvent,
        favorite: Boolean
    ) {
        val current = list().toMutableList()
        current.removeAll {
            it.organization == organization &&
                it.event.semanticKey == event.semanticKey
        }

        if (favorite) {
            current.add(FavoriteActivity(organization, event))
        }

        save(current)
        ReminderScheduler.syncFavorite(context, organization, event, favorite)
    }

    fun refreshFromEvents(
        organization: String,
        events: List<KovaEvent>
    ) {
        val current = list().toMutableList()
        var changed = false

        current.indices.forEach { index ->
            val favorite = current[index]
            if (favorite.organization != organization) return@forEach

            val fresh = events.firstOrNull {
                it.semanticKey == favorite.event.semanticKey
            } ?: return@forEach

            if (fresh != favorite.event) {
                current[index] = FavoriteActivity(organization, fresh)
                ReminderScheduler.syncFavorite(context, organization, fresh, true)
                changed = true
            }
        }

        if (changed) save(current)
    }

    private fun save(items: List<FavoriteActivity>) {
        val array = JSONArray()

        items
            .distinctBy { it.key }
            .forEach { favorite ->
                array.put(
                    JSONObject().apply {
                        put("organization", favorite.organization)
                        put(
                            "event",
                            JSONObject().apply {
                                put("id", favorite.event.id)
                                put("dateIso", favorite.event.dateIso)
                                put("dateLabel", favorite.event.dateLabel)
                                put("time", favorite.event.time)
                                put("type", favorite.event.type)
                                put("description", favorite.event.description)
                                put("sourceUrl", favorite.event.sourceUrl)
                            }
                        )
                    }
                )
            }

        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
