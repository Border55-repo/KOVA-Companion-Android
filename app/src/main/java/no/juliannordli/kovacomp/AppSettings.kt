package no.juliannordli.kovacomp

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("kova_companion_settings", Context.MODE_PRIVATE)

    var notifyAdded: Boolean
        get() = prefs.getBoolean("notify_added", true)
        set(value) = prefs.edit().putBoolean("notify_added", value).apply()

    var notifyChanged: Boolean
        get() = prefs.getBoolean("notify_changed", true)
        set(value) = prefs.edit().putBoolean("notify_changed", value).apply()

    var notifyRemoved: Boolean
        get() = prefs.getBoolean("notify_removed", true)
        set(value) = prefs.edit().putBoolean("notify_removed", value).apply()

    var showPastEvents: Boolean
        get() = prefs.getBoolean("show_past", false)
        set(value) = prefs.edit().putBoolean("show_past", value).apply()
}
