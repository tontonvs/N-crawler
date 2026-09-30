package com.noven.ncrawler.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.repository.ChapterUnavailableException
import com.noven.ncrawler.data.repository.NovelRepository
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
 *    twice, exactly 10 minutes apart. Now runs as a foreground service
 *    (exempt from that budget) with a live progress notification.
 *
 * 2. The per-chapter catch (e: Exception) also caught
 *    kotlinx.coroutines.CancellationException. CancellationException is now
 *    caught first and rethrown, exactly as kotlinx.coroutines expects.
 *
 * CHANGE (download fix, this pass) — found by reading the code:
 *
 * 3. Failed fetches were saved as chapters. Sources return placeholder text
 *    instead of throwing; the old code stored it, counted it as downloaded and
 *    never retried it. NovelRepository.downloadChapter() now throws for those
 *    (ChapterFetchGuard), so a failure really is a failure here.
 *
 * 4. The 2-second politeness delay ran after EVERY chapter, including ones
 *    already on disk. Resuming a 2,700-chapter range with 2,600 done spent
 *    ~87 minutes doing nothing. The delay now only follows a real network
 *    fetch, and "already saved" chapters are skipped from an in-memory set
 *    loaded once (was: one full-chapter DB read per chapter just to test
 *    existence).
 *
 * 5. Transient failures are retried in place (MAX_ATTEMPTS, growing backoff)
 *    before a chapter counts as failed — so one dropped connection no longer
 *    turns a whole download into ERROR.
 *
 * 6. Paid/locked chapters (ChapterUnavailableException) are skipped, not
 *    failed. Retrying can never help, and counting them as failures meant a
 *    novel with even one locked chapter could never reach COMPLETE.
 *
 * 7. Work is grouped in small batches (BATCH_SIZE chapters). After each batch:
 *    a progress checkpoint is written to the DB and there is a short
 *    cool-down before the next one — gentler on the site's rate limit and on
 *    a low-end phone than one unbroken stream.
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

        // Small batches, checkpointed. See note 7 above.
        private const val BATCH_SIZE = 20
        private const val BATCH_COOLDOWN_MS = 4_000L

        // Per-chapter: 2s between real fetches (unchanged), up to 3 attempts.
        private const val FETCH_DELAY_MS = 2_000L
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 3_000L

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

    private fun foregroundInfo(title: String, downloaded: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            NCrawlerApp.DOWNLOAD_CHANNEL_ID
        )
            // FIX: showed the raw composite id ("novellive::some-slug"); now the title.
            .setContentTitle(title)
            .setContentText("Downloading $downloaded / $total")
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

    // CHANGE (download crash fix): starting the foreground service can be refused
    // (Android 12+ blocks it when the app is in the background, e.g. a job
    // WorkManager re-runs after a process restart). That used to throw straight
    // out of doWork() and leave the row stuck at DOWNLOADING. Now a refusal is
    // logged once and the download carries on as a normal background job — it
    // may hit the ~10-minute budget on huge ranges, but it still makes
    // progress and never wedges the queue.
    private var foregroundOk = true

    private suspend fun promote(info: ForegroundInfo) {
        if (!foregroundOk) return
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            foregroundOk = false
            Log.w(TAG, "Couldn't start foreground service (${e.javaClass.simpleName}: ${e.message}) — continuing in background")
        }
    }

    // Retries a chapter in place. Cancellation and locked chapters are never
    // retried — they propagate immediately.
    private suspend fun downloadWithRetry(repo: NovelRepository, slug: String, chapterNum: Int) {
        var attempt = 1
        while (true) {
            try {
                repo.downloadChapter(slug, chapterNum)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChapterUnavailableException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= MAX_ATTEMPTS) throw e
                Log.w(TAG, "Chapter $chapterNum attempt $attempt/$MAX_ATTEMPTS failed (${e.message}) — retrying")
                delay(RETRY_BASE_DELAY_MS * attempt)
                attempt++
            }
        }
    }

    // CHANGE (download crash fix): any unexpected exception now ends in a
    // proper ERROR row + a hand-off to the next queued download, instead of
    // propagating with the row still marked DOWNLOADING (which blocked every
    // later download — see NovelRepository.reconcileDownloads).
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slug = inputData.getString(SLUG) ?: return@withContext Result.failure()
        try {
            runDownload(slug)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download crashed for $slug: ${e.javaClass.simpleName}: ${e.message}", e)
            withContext(NonCancellable) {
                val app = applicationContext as NCrawlerApp
                try {
                    app.db.downloadProgressDao().updateProgress(
                        slug, app.db.chapterDao().downloadedCount(slug), DownloadStatus.ERROR
                    )
                    app.repository.startNextQueued()
                } catch (inner: Exception) {
                    Log.w(TAG, "Couldn't record failure for $slug: ${inner.message}")
                }
            }
            Result.failure()
        }
    }

    private suspend fun runDownload(slug: String): Result {
        val startChapter = inputData.getInt(START_CHAPTER, 1)
        val endChapter   = inputData.getInt(END_CHAPTER, 1)
        val total        = endChapter - startChapter + 1

        val app        = applicationContext as NCrawlerApp
        val repo       = app.repository
        val dao        = app.db.downloadProgressDao()
        val chapterDao = app.db.chapterDao()

        Log.d(TAG, "Starting download: $slug chapters $startChapter-$endChapter")

        val novelTitle = app.db.novelDao().getBySlug(slug)?.title?.ifBlank { null } ?: "Downloading chapters"

        // CHANGE (reliability fix): promote to a foreground service before
        // doing any work. This is what exempts the job from the ~10-minute
        // background execution budget.
        promote(foregroundInfo(novelTitle, 0, total))

        // Mark as downloading
        dao.get(slug)?.let {
            dao.upsert(it.copy(status = DownloadStatus.DOWNLOADING))
        }

        // CHANGE (note 4): chapter numbers already on disk, loaded once.
        val have = chapterDao.downloadedChapterNums(slug).toHashSet()

        // Chapters of THIS range that are present after each step (existing +
        // newly saved). Drives the progress bar/notification.
        var present = (startChapter..endChapter).count { it in have }
        var failed = 0
        var lockedSkipped = 0
        var processed = 0

        try {
            for (chapterNum in startChapter..endChapter) {
                // Check if worker was cooperatively stopped
                if (isStopped) {
                    Log.d(TAG, "Worker stopped at chapter $chapterNum")
                    break
                }

                var fetchedFromNetwork = false
                try {
                    if (chapterNum !in have) {
                        fetchedFromNetwork = true
                        downloadWithRetry(repo, slug, chapterNum)
                        have.add(chapterNum)
                        present++
                        Log.d(TAG, "Downloaded chapter $chapterNum of $slug")

                        dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.DOWNLOADING)
                        setProgress(workDataOf(
                            PROGRESS_SLUG  to slug,
                            PROGRESS_DONE  to present,
                            PROGRESS_TOTAL to total
                        ))
                        promote(foregroundInfo(novelTitle, present, total))
                    }
                } catch (e: CancellationException) {
                    // Never treat a cancellation as a chapter failure — let it
                    // propagate so the outer catch below can persist PAUSED.
                    throw e
                } catch (e: ChapterUnavailableException) {
                    // CHANGE (note 6): paid/locked — skip, don't count as failure.
                    lockedSkipped++
                    Log.w(TAG, "Chapter $chapterNum skipped (locked): ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed chapter $chapterNum after $MAX_ATTEMPTS attempts: ${e.message}")
                    failed++
                    // Don't fail the whole job — skip and continue. The missing
                    // chapter surfaces as ERROR below so the user can retry.
                }

                processed++

                // CHANGE (note 4): politeness delay only after a real network
                // attempt, and never after the last chapter.
                if (fetchedFromNetwork && chapterNum < endChapter) {
                    delay(FETCH_DELAY_MS)
                }

                // CHANGE (note 7): batch checkpoint + cool-down.
                if (processed % BATCH_SIZE == 0 && chapterNum < endChapter) {
                    dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.DOWNLOADING)
                    promote(foregroundInfo(novelTitle, present, total))
                    Log.d(TAG, "Batch of $BATCH_SIZE done at chapter $chapterNum ($present/$total present, $failed failed) — cooling down")
                    if (fetchedFromNetwork) delay(BATCH_COOLDOWN_MS)
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
            return Result.success()
        }

        // Only report COMPLETE when every fetchable chapter in this range
        // succeeded — otherwise ERROR, which the Downloads screen shows with
        // a real retry action. Locked chapters are excluded from the total so
        // "Complete — N chapters" and the failed count stay honest.
        val onDisk = chapterDao.downloadedCount(slug)
        val finalStatus = if (failed == 0) DownloadStatus.COMPLETE else DownloadStatus.ERROR
        val row = dao.get(slug)
        if (row != null) {
            val newTotal = (row.totalChapters - lockedSkipped).coerceAtLeast(onDisk)
            dao.upsert(
                row.copy(
                    totalChapters      = newTotal,
                    downloadedChapters = onDisk,
                    status             = finalStatus,
                    lastUpdated        = System.currentTimeMillis()
                )
            )
        }
        Log.d(TAG, "Download finished: $slug — $present/$total present in range, $failed failed, $lockedSkipped locked, status=$finalStatus")

        // FIX: a download that hit the concurrency limit was left QUEUED with
        // no work enqueued, so nothing ever started it. Hand the freed slot on.
        try {
            repo.startNextQueued()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start next queued download: ${e.message}")
        }
        return Result.success()
    }
}
