package no.juliannordli.kovacomp

import android.content.Context

object NotificationDedup {
    private const val PREFS = "kova_notification_dedup"
    private const val KEY_IDS = "seen_change_ids"
    private const val MAX_IDS = 750

    @Synchronized
    fun shouldNotify(context: Context, changeId: String?): Boolean {
        if (changeId.isNullOrBlank()) return true

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (changeId in current) return false

        if (current.size >= MAX_IDS) {
            current.clear()
        }

        current.add(changeId)
        prefs.edit().putStringSet(KEY_IDS, current).apply()
        return true
    }
}
