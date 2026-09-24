package com.noven.ncrawler.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that downloads chapters for a novel one at a time.
 *
 * CHANGE (Downloads overhaul — reliability fix, 2026-09-23):
 * Two real bugs were found via logcat, not just theorised:
 *
 * 1. This worker never called setForeground(). A plain background
 *    CoroutineWorker is subject to Android's ~10-minute execution budget —
 *    confirmed directly in logs: a download stalled at the same chapter
 *    twice, exactly 10 minutes apart. For anything more than a couple
 *    hundred chapters this made completion essentially impossible. Now runs
 *    as a foreground service (exempt from that budget) with a live progress
 *    notification.
 *
 * 2. The per-chapter catch (e: Exception) also caught
 *    kotlinx.coroutines.CancellationException, which is an Exception
 *    subtype. That meant a system-forced cancellation (the old 10-minute
 *    cutoff, or the user backgrounding the app) got logged and counted as a
 *    plain "failed chapter" instead of a clean stop — polluting the
 *    failed-chapter count and misreporting status. CancellationException is
 *    now caught first and rethrown, exactly as kotlinx.coroutines expects.
 *
 * Samsung battery optimisation note (kept from before):
 * The 2-second delay between chapters keeps us under rate limits site-side;
 * the foreground service above is the actual defence against Samsung/Android
 * killing a long-running background job.
 *
 * Input data keys:
 *   SLUG          — novel slug
 *   START_CHAPTER — first chapter to download
 *   END_CHAPTER   — last chapter to download (inclusive)
 */
class ChapterDownloadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val TAG = "NCrawler_Worker"

    companion object {
        const val SLUG          = "slug"
        const val START_CHAPTER = "start_chapter"
        const val END_CHAPTER   = "end_chapter"
        const val PROGRESS_SLUG = "progress_slug"
        const val PROGRESS_DONE = "progress_done"
        const val PROGRESS_TOTAL= "progress_total"

        // Single fixed notification id — fine as long as DownloadPreferences'
        // concurrent-download limit stays at 1 (its default). If that limit
        // is ever raised, this needs to become per-slug (e.g. slug.hashCode())
        // so concurrent downloads don't stomp on each other's notification.
        private const val NOTIFICATION_ID = 4201

        // CHANGE (Downloads overhaul): wifiOnly now decides the network
        // constraint instead of always allowing any connection.
        // NovelRepository reads DownloadPreferences and passes the result
        // in here — the worker itself stays free of any Context/
        // SharedPreferences access, same separation of concerns as before.
        fun buildRequest(
            slug: String,
            startChapter: Int,
            endChapter: Int,
            wifiOnly: Boolean
        ): OneTimeWorkRequest {
            val data = workDataOf(
                SLUG          to slug,
                START_CHAPTER to startChapter,
                END_CHAPTER   to endChapter
            )
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            return OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .addTag(slug)          // tag by slug so we can cancel per-novel
                .addTag("download")    // tag all downloads for global cancel
                .build()
        }
    }

    private fun foregroundInfo(slug: String, downloaded: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            NCrawlerApp.DOWNLOAD_CHANNEL_ID
        )
            .setContentTitle("Downloading chapters")
            .setContentText("$slug: $downloaded / $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), downloaded, false)
            .build()

        // minSdk is 30, so the type-aware constructor is always available —
        // required on API 34+ to match the FOREGROUND_SERVICE_DATA_SYNC
        // permission declared in the manifest.
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slug         = inputData.getString(SLUG) ?: return@withContext Result.failure()
        val startChapter = inputData.getInt(START_CHAPTER, 1)
        val endChapter   = inputData.getInt(END_CHAPTER, 1)
        val total        = endChapter - startChapter + 1

        val app        = applicationContext as NCrawlerApp
        val repo       = app.repository
        val dao        = app.db.downloadProgressDao()
        val chapterDao = app.db.chapterDao()

        Log.d(TAG, "Starting download: $slug chapters $startChapter-$endChapter")

        // CHANGE (reliability fix): promote to a foreground service before
        // doing any work. This is what exempts the job from the ~10-minute
        // background execution budget.
        setForeground(foregroundInfo(slug, 0, total))

        // Mark as downloading
        dao.get(slug)?.let {
            dao.upsert(it.copy(status = DownloadStatus.DOWNLOADING))
        }

        var downloaded = 0

        // CHANGE (Downloads overhaul): track failures separately from
        // successes so the final status can honestly reflect whether
        // everything in this range actually landed (COMPLETE) or something
        // is still missing (ERROR). Previously this always reported
        // COMPLETE regardless of skipped chapters — DownloadStatus.ERROR
        // was defined but never set anywhere.
        var failed = 0

        try {
            for (chapterNum in startChapter..endChapter) {
                // Check if worker was cooperatively stopped
                if (isStopped) {
                    Log.d(TAG, "Worker stopped at chapter $chapterNum")
                    break
                }

                try {
                    // Check if already downloaded
                    val existing = chapterDao.getById("$slug::$chapterNum")
                    if (existing == null) {
                        repo.downloadChapter(slug, chapterNum)
                        Log.d(TAG, "Downloaded chapter $chapterNum of $slug")
                    }
                    downloaded++

                    // Update progress (real DB count — matters when this run
                    // was only topping up a few new chapters on an
                    // already-partly-downloaded novel)
                    dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.DOWNLOADING)

                    // Report progress to observers
                    setProgress(workDataOf(
                        PROGRESS_SLUG  to slug,
                        PROGRESS_DONE  to downloaded,
                        PROGRESS_TOTAL to total
                    ))
                    setForeground(foregroundInfo(slug, downloaded, total))

                    // 2-second delay between chapters — avoids rate limiting
                    if (chapterNum < endChapter) {
                        delay(2000)
                    }

                } catch (e: CancellationException) {
                    // CHANGE (reliability fix): never treat a cancellation as
                    // a chapter failure — let it propagate so the outer
                    // catch below can persist a clean PAUSED status.
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed chapter $chapterNum: ${e.message}")
                    failed++
                    // Don't fail the whole job — skip and continue. The
                    // missing chapter now surfaces as ERROR status below so
                    // the user can retry, instead of the run silently
                    // reporting done with a gap in it.
                }
            }
        } catch (e: CancellationException) {
            // System-forced stop (app backgrounded and killed, user swiped
            // the app away, etc.). Persist PAUSED using NonCancellable so
            // this write survives a scope that's already being cancelled,
            // then rethrow so WorkManager sees a real cancellation rather
            // than a job that silently returned success.
            withContext(NonCancellable) {
                dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.PAUSED)
            }
            throw e
        }

        if (isStopped) {
            dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.PAUSED)
            return@withContext Result.success()
        }

        // CHANGE (Downloads overhaul): only report COMPLETE when every
        // chapter in this range actually succeeded — otherwise ERROR,
        // which the Downloads screen shows with a real retry action.
        val finalStatus = if (failed == 0) DownloadStatus.COMPLETE else DownloadStatus.ERROR
        dao.updateProgress(slug, chapterDao.downloadedCount(slug), finalStatus)
        Log.d(TAG, "Download finished: $slug — $downloaded/$total this run, $failed failed, status=$finalStatus")
        Result.success()
    }
}
