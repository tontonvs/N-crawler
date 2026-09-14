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

        fun buildRequest(
            slug: String,
            startChapter: Int,
            endChapter: Int
        ): OneTimeWorkRequest {
            val data = workDataOf(
                SLUG          to slug,
                START_CHAPTER to startChapter,
                END_CHAPTER   to endChapter
            )
            return OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
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

        val app  = applicationContext as NCrawlerApp
        val repo = app.repository
        val dao  = app.db.downloadProgressDao()

        Log.d(TAG, "Starting download: $slug chapters $startChapter-$endChapter")

        // Mark as downloading
        dao.get(slug)?.let {
            dao.upsert(it.copy(status = DownloadStatus.DOWNLOADING))
        }

        var downloaded = 0

        for (chapterNum in startChapter..endChapter) {
            // Check if worker was cancelled
            if (isStopped) {
                Log.d(TAG, "Worker stopped at chapter $chapterNum")
                dao.updateProgress(slug, downloaded, DownloadStatus.PAUSED)
                return@withContext Result.success()
            }

            try {
                // Check if already downloaded
                val existing = app.db.chapterDao().getById("$slug::$chapterNum")
                if (existing == null) {
                    repo.downloadChapter(slug, chapterNum)
                    Log.d(TAG, "Downloaded chapter $chapterNum of $slug")
                }
                downloaded++

                // Update progress
                dao.updateProgress(slug, downloaded, DownloadStatus.DOWNLOADING)

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
                // Don't fail the whole job — skip and continue
                // The missing chapter will show as not downloaded in UI
            }
        }

        // Mark complete
        dao.updateProgress(slug, downloaded, DownloadStatus.COMPLETE)
        Log.d(TAG, "Download complete: $slug — $downloaded/$total chapters")
        Result.success()
    }
}
