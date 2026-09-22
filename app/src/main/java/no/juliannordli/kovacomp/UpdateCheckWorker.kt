package no.juliannordli.kovacomp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val latest = withContext(Dispatchers.IO) { ReleaseChecker.fetchLatest() }
                ?: return Result.success()

            if (!latest.isNewerThan(BuildConfig.VERSION_NAME)) {
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(
                "kova_update_check",
                Context.MODE_PRIVATE
            )
            val lastNotified = prefs.getString("last_notified_tag", null)

            if (lastNotified != latest.tagName) {
                NotificationHelper.postUrl(
                    applicationContext,
                    "Ny KOVA Companion-versjon",
                    latest.name + " er tilgjengelig. Trykk for å oppdatere.",
                    latest.apkUrl ?: latest.htmlUrl
                )
                prefs.edit().putString("last_notified_tag", latest.tagName).apply()
            }

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
