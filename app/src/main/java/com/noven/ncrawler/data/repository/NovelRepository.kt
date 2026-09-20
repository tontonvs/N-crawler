package com.noven.ncrawler.data.repository

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.noven.ncrawler.data.db.*
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.scraper.HomeSection
import com.noven.ncrawler.data.scraper.NovelSource
import com.noven.ncrawler.data.scraper.SourcePreferences
import com.noven.ncrawler.data.scraper.SourceRegistry
import com.noven.ncrawler.data.worker.ChapterDownloadWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * CHANGE (multi-source): every novel is now identified by a composite slug,
 * "<sourceId>::<realSlug>" (e.g. "novellive::cultivation-online-novel"),
 * stored as-is in NovelEntity.slug / ChapterEntity.novelSlug /
 * ReadingProgress.novelSlug / DownloadProgress.novelSlug. Nothing outside
 * this file needs to know that format exists — NavGraph, every ViewModel,
 * and every screen keep treating "slug" as an opaque String, exactly as
 * before. This repository is the only place that splits it apart to find
 * the right NovelSource and the real site-relative slug.
 *
 * Backward compatibility: rows written before this update have a bare slug
 * with no "::" — splitComposite() treats those as SourceRegistry.DEFAULT_SOURCE_ID
 * (freewebnovel), so existing library entries and reading history keep
 * working exactly as before. They just won't get the new prefixed format
 * until they're re-fetched fresh.
 */
class NovelRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val TAG = "NCrawler_Repo"

    private val novelDao            = db.novelDao()
    private val chapterDao          = db.chapterDao()
    private val downloadProgressDao = db.downloadProgressDao()
    private val readingProgressDao  = db.readingProgressDao()
    private val workManager         = WorkManager.getInstance(context)

    private val sourcePrefs = SourcePreferences(context)

    // ── Composite slug helpers ──────────────────────────────────────────────
    private fun composite(sourceId: String, realSlug: String) = "$sourceId::$realSlug"

    private fun splitComposite(slug: String): Pair<String, String> {
        val idx = slug.indexOf("::")
        return if (idx == -1) Pair(SourceRegistry.DEFAULT_SOURCE_ID, slug)
        else Pair(slug.substring(0, idx), slug.substring(idx + 2))
    }

    private fun sourceFor(slug: String): Pair<NovelSource, String> {
        val (sourceId, realSlug) = splitComposite(slug)
        return Pair(SourceRegistry.byId(sourceId), realSlug)
    }

    /** Enabled sources, in the user's saved priority order. Always ≥1. */
    private fun enabledSources(): List<NovelSource> =
        sourcePrefs.getPriorityOrder().map { SourceRegistry.byId(it) }

    private fun rewrapSlug(novel: NovelEntity, sourceId: String) =
        novel.copy(slug = composite(sourceId, novel.slug))

    private fun rewrapChapters(chapters: List<ChapterLink>): List<ChapterLink> = chapters // URLs are already absolute; no rewrap needed

    // ── Browse / Search ───────────────────────────────────────────────────────
    // Homepage/Popular come from the single top-priority ENABLED source —
    // not merged across sources. Merging multiple full homepage fetches into
    // one feed (dedup, mixed sort orders, N times the network calls on every
    // app open) is a bigger feature on its own; this keeps the home feed fast
    // and predictable while still giving full multi-source reach through
    // Search below. Easy to revisit once there are enough sources that a
    // single-source home feed feels thin.
    suspend fun fetchHomepage(): List<NovelEntity> {
        val source = enabledSources().first()
        val novels = source.fetchHomepage().map { rewrapSlug(it, source.id) }
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun fetchPopular(): List<NovelEntity> {
        val source = enabledSources().first()
        val novels = source.fetchPopular().map { rewrapSlug(it, source.id) }
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun fetchGenre(genre: String, page: Int = 1): List<NovelEntity> {
        val source = enabledSources().first()
        val novels = source.fetchGenre(genre, page).map { rewrapSlug(it, source.id) }
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    // CHANGE: same single-top-priority-source pattern as fetchHomepage/
    // fetchPopular above, for the two new defaulted NovelSource hooks.
    // A source that doesn't override them (FreeWebNovel, NovelLive) just
    // returns the empty defaults, so this is a no-op for them.
    suspend fun fetchExtraSections(): List<HomeSection> {
        val source = enabledSources().first()
        val sections = source.fetchExtraSections()
            .map { section -> section.copy(novels = section.novels.map { rewrapSlug(it, source.id) }) }
        sections.forEach { section ->
            if (section.novels.isNotEmpty()) novelDao.upsertAll(section.novels)
        }
        return sections
    }

    fun knownGenres(): List<String> = enabledSources().first().knownGenres()

    // Search DOES span every enabled source — this is the actual point of
    // the multi-source feature (find a favorite that isn't on the default
    // site). Queried concurrently, one broken source can't block the others,
    // and results are deduped by normalized title (keeping whichever copy
    // came from the higher-priority source) so the same novel from two
    // sites doesn't show up twice.
    suspend fun search(query: String): List<NovelEntity> {
        val local = novelDao.searchLocal(query)

        val remote = try {
            coroutineScope {
                enabledSources().map { source ->
                    async {
                        try {
                            source.search(query).map { rewrapSlug(it, source.id) }
                        } catch (e: Exception) {
                            Log.w(TAG, "search('$query') failed on ${source.id}: ${e.message}")
                            emptyList()
                        }
                    }
                }.map { it.await() }.flatten()
            }
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed entirely: ${e.message}", e)
            emptyList()
        }

        // Dedup by normalized title, first occurrence wins (sources were
        // iterated in priority order, so the kept copy is the preferred one)
        val deduped = remote
            .distinctBy { it.title.trim().lowercase() }
            .take(60)

        if (deduped.isNotEmpty()) novelDao.upsertAll(deduped)
        return deduped.ifEmpty { local }
    }

    // ── Novel detail ──────────────────────────────────────────────────────────
    suspend fun getNovel(slug: String): NovelEntity? {
        val cached = novelDao.getBySlug(slug)
        // Also require coverUrl — a row can have synopsis/chapterUrls filled in
        // from an earlier detail fetch, then get its coverUrl blanked out by a
        // later homepage/genre upsert (Room's REPLACE swaps the whole row).
        // Without this check, a novel opened once during that window would
        // never re-fetch and would stay cover-less forever.
        if (cached != null && cached.synopsis.isNotBlank() && cached.chapterUrls.isNotBlank()
            && cached.coverUrl.isNotBlank())
            return cached

        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return cached
        val novel  = rewrapSlug(result.first, source.id)
        novelDao.upsert(novel)
        return novel
    }

    suspend fun getChapterList(slug: String): List<ChapterLink> {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank())
            return parseChapterUrls(novel.chapterUrls)

        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return emptyList()
        novelDao.upsert(rewrapSlug(result.first, source.id))
        return result.second
    }

    // ── Chapter download ──────────────────────────────────────────────────────
    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        val chapterId = "$slug::$chapterNum"
        chapterDao.getById(chapterId)?.let { return it }

        val (source, realSlug) = sourceFor(slug)
        val url = resolveChapterUrl(slug, chapterNum)
            ?: source.buildChapterUrl(realSlug, chapterNum)
        val (title, content) = source.fetchChapterByUrl(url)
        val entity = ChapterEntity(
            id = chapterId, novelSlug = slug,
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

        Log.d(TAG, "Queuing download: $slug — $downloaded/$total already done")

        downloadProgressDao.upsert(
            DownloadProgress(
                novelSlug          = slug,
                totalChapters      = total,
                downloadedChapters = downloaded,
                status             = DownloadStatus.QUEUED
            )
        )

        novelDao.setLibrary(slug, true)

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
        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return 0
        val (fresh, chapters) = result
        novelDao.upsert(rewrapSlug(fresh, source.id))

        val downloaded = chapterDao.downloadedCount(slug)
        val newChapters = chapters.size - downloaded
        Log.d(TAG, "Update check $slug: ${chapters.size} total, $downloaded downloaded, $newChapters new")

        if (newChapters > 0) {
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
                novelSlug        = slug,
                lastChapterNum   = chapterNum,
                lastChapterTitle = chapterTitle,
                scrollPosition   = scrollPos
            )
        )
    }

    suspend fun getReadingProgress(slug: String) = readingProgressDao.get(slug)
    fun allReadingProgressFlow() = readingProgressDao.observeAll()

    // ── Chapters ──────────────────────────────────────────────────────────────
    fun chaptersFlow(slug: String) = chapterDao.chaptersForNovel(slug)
    suspend fun isChapterDownloaded(slug: String, chapterNum: Int) =
        chapterDao.getById("$slug::$chapterNum") != null

    // ── Sources (for the Settings screen) ───────────────────────────────────
    fun availableSources(): List<NovelSource> = SourceRegistry.all()
    fun sourcePreferences(): SourcePreferences = sourcePrefs

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
        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return null
        novelDao.upsert(rewrapSlug(result.first, source.id))
        return result.second.find { it.num == chapterNum }?.url
    }
}
