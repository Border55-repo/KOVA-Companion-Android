package no.juliannordli.kovacomp

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object PushManager {
    private const val PREFS = "kova_push"
    private const val CURRENT_TOPIC = "current_topic"

    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun topicFor(org: String): String =
        "kova_" + org
            .lowercase()
            .replace(Regex("[^a-z0-9_.~%-]"), "_")

    fun subscribeToOrganization(
        context: Context,
        org: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!isConfigured(context)) {
            onResult(false)
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val newTopic = topicFor(org)
        val oldTopic = prefs.getString(CURRENT_TOPIC, null)

        fun subscribeNew() {
            FirebaseMessaging.getInstance()
                .subscribeToTopic(newTopic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        prefs.edit().putString(CURRENT_TOPIC, newTopic).apply()
                    }
                    onResult(task.isSuccessful)
                }
        }

        if (!oldTopic.isNullOrBlank() && oldTopic != newTopic) {
            FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(oldTopic)
                .addOnCompleteListener { subscribeNew() }
        } else {
            subscribeNew()
        }
    }

    fun currentTopic(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CURRENT_TOPIC, null)
}
