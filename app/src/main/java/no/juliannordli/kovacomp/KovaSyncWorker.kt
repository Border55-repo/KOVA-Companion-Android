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
            val old = repo.loadCache()
            val fresh = repo.fetch()
            val firstSync = old.isEmpty()
            val diff = repo.diff(old, fresh)
            repo.saveCache(fresh)
            if (!firstSync) NotificationHelper.postDiff(applicationContext, diff, repo.organization())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
