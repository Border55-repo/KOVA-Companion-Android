package no.juliannordli.kovacomp

import android.content.Context
import java.time.LocalTime

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

    var remind6Hours: Boolean
        get() = prefs.getBoolean("remind_6h", false)
        set(value) = prefs.edit().putBoolean("remind_6h", value).apply()

    var remind2Hours: Boolean
        get() = prefs.getBoolean("remind_2h", true)
        set(value) = prefs.edit().putBoolean("remind_2h", value).apply()

    var remind1Hour: Boolean
        get() = prefs.getBoolean("remind_1h", false)
        set(value) = prefs.edit().putBoolean("remind_1h", value).apply()

    var remind30Minutes: Boolean
        get() = prefs.getBoolean("remind_30m", false)
        set(value) = prefs.edit().putBoolean("remind_30m", value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) = prefs.edit().putBoolean("quiet_hours_enabled", value).apply()

    var quietStartHour: Int
        get() = prefs.getInt("quiet_start_hour", 22).coerceIn(0, 23)
        set(value) = prefs.edit().putInt("quiet_start_hour", value.coerceIn(0, 23)).apply()

    var quietEndHour: Int
        get() = prefs.getInt("quiet_end_hour", 7).coerceIn(0, 23)
        set(value) = prefs.edit().putInt("quiet_end_hour", value.coerceIn(0, 23)).apply()

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

    var favoriteOrganizations: Set<String>
        get() = prefs.getStringSet(
            "favorite_organizations",
            setOf(KovaRepository.DEFAULT_ORG)
        )?.toSet() ?: setOf(KovaRepository.DEFAULT_ORG)
        set(value) = prefs.edit()
            .putStringSet("favorite_organizations", value.toSet())
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

    fun isFavoriteOrganization(code: String): Boolean =
        code in favoriteOrganizations

    fun setFavoriteOrganization(code: String, favorite: Boolean) {
        val updated = favoriteOrganizations.toMutableSet()
        if (favorite) updated.add(code) else updated.remove(code)
        favoriteOrganizations = updated
    }

    fun isKindEnabled(kind: String): Boolean =
        when (kind) {
            "added" -> notifyAdded
            "changed" -> notifyChanged
            "removed" -> notifyRemoved
            else -> true
        }

    fun isQuietNow(now: LocalTime = LocalTime.now()): Boolean {
        if (!quietHoursEnabled) return false
        val start = quietStartHour
        val end = quietEndHour
        if (start == end) return true
        val hour = now.hour
        return if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }
}
