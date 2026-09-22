package no.juliannordli.kovacomp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL = "kova_changes"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "KOVA-endringer",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
        }
    }

    fun post(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify((System.nanoTime() and 0xFFFFFF).toInt(), notification)
    }

    fun postDiff(context: Context, diff: KovaDiff) {
        diff.changed.take(3).forEach {
            post(
                context,
                "KOVA-aktivitet endret",
                it.new.description + ": " + it.old.dateLabel + " " + it.old.time +
                    " → " + it.new.dateLabel + " " + it.new.time
            )
        }
        diff.added.take(3).forEach {
            post(context, "Ny KOVA-aktivitet", it.description + " • " + it.dateLabel + " " + it.time)
        }
        diff.removed.take(3).forEach {
            post(context, "KOVA-aktivitet fjernet", it.description + " • " + it.dateLabel + " " + it.time)
        }
    }
}
