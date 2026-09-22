package no.juliannordli.kovacomp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KovaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "KOVA Companion"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Det er en ny oppdatering i KOVA."

        NotificationHelper.post(this, title, body)
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("kova_push", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}
