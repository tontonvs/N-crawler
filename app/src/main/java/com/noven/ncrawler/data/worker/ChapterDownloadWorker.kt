package com.noven.ncrawler.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that downloads chapters for a novel one at a time.
 *
 * Samsung battery optimisation note:
 * We use FOREGROUND_SERVICE_TYPE_DATA_SYNC via setExpedited() for the first
 * chapter, then fall back to normal constraints so Samsung's Task Manager
 * doesn't kill us. The 2-second delay between chapters also keeps us under
 * FreeWebNovel's rate limit.
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

        for (chapterNum in startChapter..endChapter) {
            // Check if worker was cancelled
            if (isStopped) {
                Log.d(TAG, "Worker stopped at chapter $chapterNum")
                // CHANGE (Downloads overhaul): report the real novel-wide
                // downloaded count from the DB, not this job's local
                // counter — matters when this run was only topping up a
                // few new chapters on an already partly-downloaded novel;
                // the old code overwrote downloadedChapters with just this
                // run's small count, losing the rest.
                dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.PAUSED)
                return@withContext Result.success()
            }

            try {
                // Check if already downloaded
                val existing = chapterDao.getById("$slug::$chapterNum")
                if (existing == null) {
                    repo.downloadChapter(slug, chapterNum)
                    Log.d(TAG, "Downloaded chapter $chapterNum of $slug")
                }
                downloaded++

                // Update progress (real DB count, see note above)
                dao.updateProgress(slug, chapterDao.downloadedCount(slug), DownloadStatus.DOWNLOADING)

                // Report progress to observers
                setProgress(workDataOf(
                    PROGRESS_SLUG  to slug,
                    PROGRESS_DONE  to downloaded,
                    PROGRESS_TOTAL to total
                ))

                // 2-second delay between chapters — avoids rate limiting
                if (chapterNum < endChapter) {
                    kotlinx.coroutines.delay(2000)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed chapter $chapterNum: ${e.message}")
                failed++
                // Don't fail the whole job — skip and continue. The
                // missing chapter now surfaces as ERROR status below so
                // the user can retry, instead of the run silently
                // reporting done with a gap in it.
            }
        }

        // CHANGE (Downloads overhaul): only report COMPLETE when every
        // chapter in this range actually succeeded — otherwise ERROR,
        // which the Downloads screen now shows with a real retry action.
        val finalStatus = if (failed == 0) DownloadStatus.COMPLETE else DownloadStatus.ERROR
        dao.updateProgress(slug, chapterDao.downloadedCount(slug), finalStatus)
        Log.d(TAG, "Download finished: $slug — $downloaded/$total this run, $failed failed, status=$finalStatus")
        Result.success()
    }
}
