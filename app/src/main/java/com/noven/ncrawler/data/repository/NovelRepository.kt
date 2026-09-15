package com.noven.ncrawler.data.repository

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.noven.ncrawler.data.db.*
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.scraper.FreeWebNovelScraper
import com.noven.ncrawler.data.worker.ChapterDownloadWorker
import kotlinx.coroutines.flow.Flow

class NovelRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val TAG = "NCrawler_Repo"

    private val novelDao           = db.novelDao()
    private val chapterDao         = db.chapterDao()
    private val downloadProgressDao= db.downloadProgressDao()
    private val readingProgressDao = db.readingProgressDao()
    private val scraper            = FreeWebNovelScraper()
    private val workManager        = WorkManager.getInstance(context)

    // ── Browse / Search ───────────────────────────────────────────────────────
    suspend fun fetchHomepage(): List<NovelEntity> {
        val novels = scraper.fetchHomepage()
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun fetchPopular(): List<NovelEntity> {
        val novels = scraper.fetchPopular()
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun fetchGenre(genre: String, page: Int = 1): List<NovelEntity> {
        val novels = scraper.fetchGenre(genre, page)
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun search(query: String): List<NovelEntity> {
        val local  = novelDao.searchLocal(query)
        val remote = try { scraper.search(query) } catch (_: Exception) { emptyList() }
        if (remote.isNotEmpty()) novelDao.upsertAll(remote)
        return remote.ifEmpty { local }
    }

    // ── Novel detail ──────────────────────────────────────────────────────────
    suspend fun getNovel(slug: String): NovelEntity? {
        val cached = novelDao.getBySlug(slug)
        if (cached != null && cached.synopsis.isNotBlank() && cached.chapterUrls.isNotBlank())
            return cached
        val result = scraper.fetchDetail(slug) ?: return cached
        novelDao.upsert(result.first)
        return result.first
    }

    suspend fun getChapterList(slug: String): List<ChapterLink> {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank())
            return parseChapterUrls(novel.chapterUrls)
        val result = scraper.fetchDetail(slug) ?: return emptyList()
        novelDao.upsert(result.first)
        return result.second
    }

    // ── Chapter download ──────────────────────────────────────────────────────
    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        chapterDao.getById("$slug::$chapterNum")?.let { return it }
        val url = resolveChapterUrl(slug, chapterNum)
            ?: scraper.buildChapterUrl(slug, chapterNum)
        val (title, content) = scraper.fetchChapterByUrl(url)
        val entity = ChapterEntity(
            id = "$slug::$chapterNum", novelSlug = slug,
            chapterNum = chapterNum, title = title,
            chapterUrl = url, content = content
        )
        chapterDao.upsert(entity)
        return entity
    }

    // ── Download queue (background) ───────────────────────────────────────────
    suspend fun queueDownloadAll(slug: String) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return

        val downloaded = chapterDao.downloadedCount(slug)
        val total      = chapters.size
        val minNum     = chapters.minOf { it.num }
        val maxNum     = chapters.maxOf { it.num }

        Log.d("NCrawler_Repo", "Queuing download: $slug — $downloaded/$total already done")

        // Init progress row
        downloadProgressDao.upsert(
            DownloadProgress(
                novelSlug         = slug,
                totalChapters     = total,
                downloadedChapters = downloaded,
                status            = DownloadStatus.QUEUED
            )
        )

        // Also add to library automatically
        novelDao.setLibrary(slug, true)

        // Enqueue WorkManager job — unique per novel, replace if already queued
        val request = ChapterDownloadWorker.buildRequest(
            slug         = slug,
            startChapter = minNum,
            endChapter   = maxNum
        )
        workManager.enqueueUniqueWork(
            "download_$slug",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun cancelDownload(slug: String) {
        workManager.cancelAllWorkByTag(slug)
        downloadProgressDao.get(slug)?.let {
            downloadProgressDao.upsert(it.copy(status = DownloadStatus.PAUSED))
        }
    }

    // ── Check for new chapters ────────────────────────────────────────────────
    suspend fun checkForUpdates(slug: String): Int {
        val result = scraper.fetchDetail(slug) ?: return 0
        val (fresh, chapters) = result
        novelDao.upsert(fresh)

        val downloaded = chapterDao.downloadedCount(slug)
        val newChapters = chapters.size - downloaded
        Log.d("NCrawler_Repo", "Update check $slug: ${chapters.size} total, $downloaded downloaded, $newChapters new")

        if (newChapters > 0) {
            // Queue only the new ones
            val downloadedNums = (1..downloaded).toSet()
            val missing = chapters.filter { it.num !in downloadedNums }
            if (missing.isNotEmpty()) {
                val minNew = missing.minOf { it.num }
                val maxNew = missing.maxOf { it.num }

                downloadProgressDao.get(slug)?.let {
                    downloadProgressDao.upsert(
                        it.copy(
                            totalChapters = chapters.size,
                            status = DownloadStatus.QUEUED
                        )
                    )
                }

                workManager.enqueueUniqueWork(
                    "download_$slug",
                    ExistingWorkPolicy.REPLACE,
                    ChapterDownloadWorker.buildRequest(slug, minNew, maxNew)
                )
            }
        }
        return newChapters
    }

    // ── Library ───────────────────────────────────────────────────────────────
    fun libraryFlow(): Flow<List<NovelEntity>> = novelDao.libraryFlow()
    suspend fun setLibrary(slug: String, inLibrary: Boolean) =
        novelDao.setLibrary(slug, inLibrary)

    // ── Download progress ─────────────────────────────────────────────────────
    fun downloadProgressFlow(slug: String) = downloadProgressDao.observe(slug)
    fun allDownloadProgressFlow() = downloadProgressDao.observeAll()
    suspend fun getDownloadProgress(slug: String) = downloadProgressDao.get(slug)

    // ── Reading progress ──────────────────────────────────────────────────────
    suspend fun saveReadingProgress(slug: String, chapterNum: Int, chapterTitle: String, scrollPos: Int = 0) {
        readingProgressDao.upsert(
            ReadingProgress(
                novelSlug       = slug,
                lastChapterNum  = chapterNum,
                lastChapterTitle = chapterTitle,
                scrollPosition  = scrollPos
            )
        )
    }

    suspend fun getReadingProgress(slug: String) = readingProgressDao.get(slug)
    fun allReadingProgressFlow() = readingProgressDao.observeAll()

    // ── Chapters ──────────────────────────────────────────────────────────────
    fun chaptersFlow(slug: String) = chapterDao.chaptersForNovel(slug)
    suspend fun isChapterDownloaded(slug: String, chapterNum: Int) =
        chapterDao.getById("$slug::$chapterNum") != null

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun parseChapterUrls(raw: String): List<ChapterLink> =
        raw.split("\t").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull() ?: return@mapNotNull null
                ChapterLink(num = num, title = "Chapter $num", url = parts[1])
            } else null
        }.sortedByDescending { it.num }

    private suspend fun resolveChapterUrl(slug: String, chapterNum: Int): String? {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank()) {
            parseChapterUrls(novel.chapterUrls)
                .find { it.num == chapterNum }?.url?.let { return it }
        }
        val result = scraper.fetchDetail(slug) ?: return null
        novelDao.upsert(result.first)
        return result.second.find { it.num == chapterNum }?.url
    }
}
