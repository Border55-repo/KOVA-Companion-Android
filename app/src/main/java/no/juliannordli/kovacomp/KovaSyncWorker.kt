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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val repo = KovaRepository(applicationContext)
            val settings = AppSettings(applicationContext)
            val organizations = settings.subscribedOrganizations
                .ifEmpty { setOf(repo.organization()) }

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
