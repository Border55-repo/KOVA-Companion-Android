package no.juliannordli.kovacomp

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

object PushManager {
    private const val PREFS = "kova_push"
    private const val TOPICS = "subscribed_topics"

    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun topicFor(org: String): String =
        "kova_" + org
            .lowercase()
            .replace(Regex("[^a-z0-9_.~%-]"), "_")

    fun setSubscriptions(
        context: Context,
        organizations: Set<String>,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!isConfigured(context)) {
            onResult(false)
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Global topic is used for release notes and important app-wide changes.
        val desiredTopics = organizations.map(::topicFor).toSet() + "kova_all_users"
        val currentTopics = prefs.getStringSet(TOPICS, emptySet())?.toSet() ?: emptySet()

        val toSubscribe = desiredTopics - currentTopics
        val toUnsubscribe = currentTopics - desiredTopics
        val total = toSubscribe.size + toUnsubscribe.size

        if (total == 0) {
            onResult(true)
            return
        }

        val remaining = AtomicInteger(total)
        val failed = AtomicBoolean(false)

        fun completeOne(success: Boolean) {
            if (!success) failed.set(true)
            if (remaining.decrementAndGet() == 0) {
                if (!failed.get()) {
                    prefs.edit().putStringSet(TOPICS, desiredTopics).apply()
                }
                onResult(!failed.get())
            }
        }

        toUnsubscribe.forEach { topic ->
            FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(topic)
                .addOnCompleteListener { task -> completeOne(task.isSuccessful) }
        }

        toSubscribe.forEach { topic ->
            FirebaseMessaging.getInstance()
                .subscribeToTopic(topic)
                .addOnCompleteListener { task -> completeOne(task.isSuccessful) }
        }
    }

    fun currentTopics(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(TOPICS, emptySet())
            ?.toSet()
            ?: emptySet()
}
