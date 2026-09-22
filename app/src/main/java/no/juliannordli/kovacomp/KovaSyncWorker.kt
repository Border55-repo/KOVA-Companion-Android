package no.juliannordli.kovacomp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KovaSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val FALLBACK_BUCKETS = 4
        private const val FIFTEEN_MINUTES_MS = 15L * 60L * 1000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val repo = KovaRepository(applicationContext)
            val settings = AppSettings(applicationContext)
            val current = repo.organization()
            val subscribed = settings.subscribedOrganizations
                .ifEmpty { setOf(current) }

            val bucket = ((System.currentTimeMillis() / FIFTEEN_MINUTES_MS) %
                FALLBACK_BUCKETS).toInt()

            val organizations = subscribed
                .filter { code ->
                    code == current ||
                        Math.floorMod(code.hashCode(), FALLBACK_BUCKETS) == bucket
                }
                .toSet()
                .ifEmpty { setOf(current) }

            organizations.forEach { org ->
                val old = repo.loadCache(org)
                val fresh = repo.fetch(org)
                val firstSync = old.isEmpty()
                val diff = repo.diff(old, fresh)

                repo.saveCache(fresh, org)

                if (!firstSync) {
                    NotificationHelper.postDiff(
                        applicationContext,
                        diff,
                        org
                    )
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
