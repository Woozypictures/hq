package uk.woozy.hq

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh. WorkManager's minimum interval is 15 minutes and Android decides the exact run time
 * (battery optimisation can delay it), so the interval is a target, not a promise.
 */
class RefreshWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Cfg.get(applicationContext)
        return try { Repo.refresh(applicationContext, manual = false); Result.success() } catch (e: Exception) { Result.retry() }
    }

    companion object {
        private const val NAME = "hq-refresh"

        private fun request(min: Int) = PeriodicWorkRequestBuilder<RefreshWorker>(min.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()

        /** Makes sure the job exists if auto-refresh is on. Does not disturb an existing schedule. */
        fun ensure(c: Context) {
            val cfg = Cfg.get(c)
            val wm = WorkManager.getInstance(c)
            if (cfg.autoRefresh) wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request(cfg.intervalMin))
            else wm.cancelUniqueWork(NAME)
        }

        /** Call after the user changes auto-refresh or the interval. */
        fun reschedule(c: Context) {
            val cfg = Cfg.get(c)
            val wm = WorkManager.getInstance(c)
            if (cfg.autoRefresh) wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request(cfg.intervalMin))
            else wm.cancelUniqueWork(NAME)
        }
    }
}
