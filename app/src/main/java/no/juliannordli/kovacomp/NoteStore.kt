package no.juliannordli.kovacomp

import android.content.Context

class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "kova_activity_notes",
        Context.MODE_PRIVATE
    )

    private fun key(organization: String, event: KovaEvent): String =
        organization + "|" + event.semanticKey

    fun get(organization: String, event: KovaEvent): String =
        prefs.getString(key(organization, event), "").orEmpty()

    fun set(organization: String, event: KovaEvent, value: String) {
        val storageKey = key(organization, event)
        if (value.isBlank()) {
            prefs.edit().remove(storageKey).apply()
        } else {
            prefs.edit().putString(storageKey, value).apply()
        }
    }
}
