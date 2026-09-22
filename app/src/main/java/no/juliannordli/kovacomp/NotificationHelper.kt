package no.juliannordli.kovacomp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    private fun pendingIntent(
        context: Context,
        target: NotificationTarget?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            if (target != null) {
                putExtra(NotificationTarget.EXTRA_ORG, target.organization)
                putExtra(NotificationTarget.EXTRA_EVENT_ID, target.eventId)
                putExtra(NotificationTarget.EXTRA_KIND, target.kind)
                putExtra(NotificationTarget.EXTRA_DATE_ISO, target.dateIso)
                putExtra(NotificationTarget.EXTRA_DATE_LABEL, target.dateLabel)
                putExtra(NotificationTarget.EXTRA_TIME, target.time)
                putExtra(NotificationTarget.EXTRA_TYPE, target.type)
                putExtra(NotificationTarget.EXTRA_DESCRIPTION, target.description)
                putExtra(NotificationTarget.EXTRA_SOURCE_URL, target.sourceUrl)
            }
        }

        val requestCode = target?.let {
            (it.organization + "|" + it.eventId + "|" + it.kind).hashCode()
        } ?: 0

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun post(
        context: Context,
        title: String,
        text: String,
        target: NotificationTarget? = null
    ) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent(context, target))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify((System.nanoTime() and 0xFFFFFF).toInt(), notification)
    }

    private fun targetFor(
        organization: String,
        kind: String,
        event: KovaEvent
    ) = NotificationTarget(
        organization = organization,
        eventId = event.id,
        kind = kind,
        dateIso = event.dateIso,
        dateLabel = event.dateLabel,
        time = event.time,
        type = event.type,
        description = event.description,
        sourceUrl = event.sourceUrl
    )

    fun postDiff(
        context: Context,
        diff: KovaDiff,
        organization: String
    ) {
        val settings = AppSettings(context)

        if (settings.notifyChanged) {
            diff.changed
                .filter { settings.isEventTypeEnabled(it.new.type) }
                .take(3)
                .forEach {
                    post(
                        context,
                        "KOVA-aktivitet endret",
                        it.new.description + ": " + it.old.dateLabel + " " + it.old.time +
                            " → " + it.new.dateLabel + " " + it.new.time,
                        targetFor(organization, "changed", it.new)
                    )
                }
        }

        if (settings.notifyAdded) {
            diff.added
                .filter { settings.isEventTypeEnabled(it.type) }
                .take(3)
                .forEach {
                    post(
                        context,
                        "Ny KOVA-aktivitet",
                        it.description + " • " + it.dateLabel + " " + it.time,
                        targetFor(organization, "added", it)
                    )
                }
        }

        if (settings.notifyRemoved) {
            diff.removed
                .filter { settings.isEventTypeEnabled(it.type) }
                .take(3)
                .forEach {
                    post(
                        context,
                        "KOVA-aktivitet fjernet",
                        it.description + " • " + it.dateLabel + " " + it.time,
                        targetFor(organization, "removed", it)
                    )
                }
        }
    }
}
