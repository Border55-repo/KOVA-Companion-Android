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

    var remind24Hours: Boolean
        get() = prefs.getBoolean("remind_24h", true)
        set(value) = prefs.edit().putBoolean("remind_24h", value).apply()

    var remind2Hours: Boolean
        get() = prefs.getBoolean("remind_2h", true)
        set(value) = prefs.edit().putBoolean("remind_2h", value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var disabledEventTypes: Set<String>
        get() = prefs.getStringSet("disabled_event_types", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("disabled_event_types", value.toSet()).apply()

    var subscribedOrganizations: Set<String>
        get() = prefs.getStringSet(
            "subscribed_organizations",
            setOf(KovaRepository.DEFAULT_ORG)
        )?.toSet() ?: setOf(KovaRepository.DEFAULT_ORG)
        set(value) = prefs.edit()
            .putStringSet("subscribed_organizations", value.toSet())
            .apply()

    fun isEventTypeEnabled(type: String): Boolean =
        type.isBlank() || type !in disabledEventTypes

    fun setEventTypeEnabled(type: String, enabled: Boolean) {
        val updated = disabledEventTypes.toMutableSet()
        if (enabled) updated.remove(type) else updated.add(type)
        disabledEventTypes = updated
    }

    fun isOrganizationSubscribed(code: String): Boolean =
        code in subscribedOrganizations

    fun setOrganizationSubscribed(code: String, enabled: Boolean) {
        val updated = subscribedOrganizations.toMutableSet()
        if (enabled) updated.add(code) else updated.remove(code)
        subscribedOrganizations = updated
    }

    fun isKindEnabled(kind: String): Boolean =
        when (kind) {
            "added" -> notifyAdded
            "changed" -> notifyChanged
            "removed" -> notifyRemoved
            else -> true
        }
}
