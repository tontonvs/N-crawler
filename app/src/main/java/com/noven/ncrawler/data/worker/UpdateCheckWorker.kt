package com.noven.ncrawler.data.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.newChapterCount
import com.noven.ncrawler.data.local.DownloadNetwork
import com.noven.ncrawler.data.local.DownloadPreferences
import java.util.concurrent.TimeUnit

/**
 * Periodic background check for new chapters of favourites and downloaded novels.
 * It never downloads — it only records the new range (see NovelRepository.
 * checkForUpdatesDetailed) and posts one notification summarising what's new.
 *
 * Gentle on purpose: roughly twice a day, only with a network, not on low battery,
 * on Wi-Fi when the user chose "Wi-Fi only" for downloads, and novels checked in the
 * last few hours (by a manual refresh, say) are skipped.
 */
class UpdateCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "NCrawler_UpdateCheck"
        const val UNIQUE_NAME = "update_check_periodic"
        private const val NOTIFICATION_ID = 4301
        private const val PERIOD_HOURS = 12L
        private const val MIN_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** Idempotent: KEEP leaves an already-scheduled job alone, so calling it every launch is fine. */
        fun schedule(context: Context) {
            val network = if (DownloadPreferences(context).getNetworkMode() == DownloadNetwork.WIFI_ONLY)
                NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(network)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as NCrawlerApp
        return try {
            val grown = app.repository.checkAllForUpdates(MIN_INTERVAL_MS)
            Log.d(TAG, "Background check done: ${grown.size} novel(s) with new chapters")
            if (grown.isNotEmpty()) notify(grown)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background check failed: ${e.message}")
            Result.retry()
        }
    }

    private fun notify(grown: List<com.noven.ncrawler.data.db.NovelEntity>) {
        // Notifications switched off (or permission denied): the Library badges still work.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName) ?: return
        val tap = PendingIntent.getActivity(
            applicationContext, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun plural(n: Int) = if (n == 1) "1 new chapter" else "$n new chapters"

        val builder = NotificationCompat.Builder(applicationContext, NCrawlerApp.UPDATES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (grown.size == 1) {
            val n = grown.first()
            builder.setContentTitle(n.title.ifBlank { "A novel you follow" })
                .setContentText(plural(n.newChapterCount()) + " available")
        } else {
            builder.setContentTitle("${grown.size} novels have new chapters")
                .setContentText(grown.take(3).joinToString(", ") { it.title })
            val inbox = NotificationCompat.InboxStyle()
            grown.take(5).forEach { inbox.addLine("${it.title} · ${plural(it.newChapterCount())}") }
            if (grown.size > 5) inbox.setSummaryText("+${grown.size - 5} more")
            builder.setStyle(inbox)
        }

        try {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification refused: ${e.message}")
        }
    }
}
