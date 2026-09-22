package no.juliannordli.kovacomp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object UpdateDownloader {
    fun enqueue(
        context: Context,
        url: String,
        versionTag: String
    ): Long {
        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val safeTag = versionTag
            .ifBlank { "latest" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("KOVA Companion " + versionTag)
            .setDescription("Laster ned oppdatering")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "KOVA-Companion-" + safeTag + ".apk"
            )

        return manager.enqueue(request)
    }
}
