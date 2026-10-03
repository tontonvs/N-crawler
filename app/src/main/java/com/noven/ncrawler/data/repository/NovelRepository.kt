package com.noven.ncrawler.data.repository

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.noven.ncrawler.data.db.*
import com.noven.ncrawler.data.local.DownloadPreferences
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.scraper.HomeSection
import com.noven.ncrawler.data.scraper.NovelSource
import com.noven.ncrawler.data.scraper.SourcePreferences
import com.noven.ncrawler.data.scraper.SourceRegistry
import com.noven.ncrawler.data.worker.ChapterDownloadWorker
import com.noven.ncrawler.data.worker.TxtExportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val sourcePrefs   = SourcePreferences(context)

    // CHANGE (Downloads overhaul): same pattern as sourcePrefs above.
    private val downloadPrefs = DownloadPreferences(context)

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

    // FIX: every scrape result used to be written with a plain @Upsert, which
    // replaces the whole row — so any browse/search/refresh reset isInLibrary
    // to false (the novel vanished from Library and Downloads) and blanked
    // fields the new scrape didn't carry (chapter list, synopsis, cover).
    // All writes now go through here: the library flag always survives, and a
    // blank/zero incoming field never overwrites a cached real value.
    private fun mergeWithCached(fresh: NovelEntity, old: NovelEntity?): NovelEntity {
        if (old == null) return fresh
        return fresh.copy(
            coverUrl      = fresh.coverUrl.ifBlank { old.coverUrl },
            synopsis      = fresh.synopsis.ifBlank { old.synopsis },
            status        = fresh.status.ifBlank { old.status },
            rating        = fresh.rating.ifBlank { old.rating },
            genres        = fresh.genres.ifBlank { old.genres },
            chapterCount  = if (fresh.chapterCount > 0) fresh.chapterCount else old.chapterCount,
            latestChapter = fresh.latestChapter.ifBlank { old.latestChapter },
            author        = fresh.author.ifBlank { old.author },
            chapterUrls   = fresh.chapterUrls.ifBlank { old.chapterUrls },
            isInLibrary   = old.isInLibrary
        )
    }

    private suspend fun saveNovels(novels: List<NovelEntity>): List<NovelEntity> {
        if (novels.isEmpty()) return novels
        val cached = novelDao.getBySlugs(novels.map { it.slug }.distinct()).associateBy { it.slug }
        val merged = novels.map { mergeWithCached(it, cached[it.slug]) }
        novelDao.upsertAll(merged)
        return merged
    }

    private suspend fun saveNovel(novel: NovelEntity): NovelEntity = saveNovels(listOf(novel)).first()

    /** DB-only lookup — safe to call from UI flows (never touches the network). */
    suspend fun getCachedNovel(slug: String): NovelEntity? = novelDao.getBySlug(slug)

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
        return saveNovels(source.fetchHomepage().map { rewrapSlug(it, source.id) })
    }

    suspend fun fetchPopular(): List<NovelEntity> {
        val source = enabledSources().first()
        return saveNovels(source.fetchPopular().map { rewrapSlug(it, source.id) })
    }

    suspend fun fetchGenre(genre: String, page: Int = 1): List<NovelEntity> {
        val source = enabledSources().first()
        return saveNovels(source.fetchGenre(genre, page).map { rewrapSlug(it, source.id) })
    }

    // CHANGE: same single-top-priority-source pattern as fetchHomepage/
    // fetchPopular above, for the two new defaulted NovelSource hooks.
    // A source that doesn't override them (FreeWebNovel, NovelLive) just
    // returns the empty defaults, so this is a no-op for them.
    suspend fun fetchExtraSections(): List<HomeSection> {
        val source = enabledSources().first()
        return source.fetchExtraSections().map { section ->
            section.copy(novels = saveNovels(section.novels.map { rewrapSlug(it, source.id) }))
        }
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

        val saved = saveNovels(deduped)
        return saved.ifEmpty { local }
    }

    // ── Novel detail ──────────────────────────────────────────────────────────

    // CHANGE (perf fix): metadata-only path for DetailViewModel's initial
    // paint. Deliberately does NOT require chapterUrls to be cached — a
    // novel whose chapter list hasn't loaded yet (or hasn't ever) still
    // serves this instantly. Chapters are fetched separately and afterward
    // via getChapterList() below.
    suspend fun getNovelInfo(slug: String): NovelEntity? {
        val cached = novelDao.getBySlug(slug)
        if (cached != null && cached.synopsis.isNotBlank() && cached.coverUrl.isNotBlank())
            return cached

        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchInfo(realSlug) ?: return cached
        // fetchInfo() never returns chapterUrls; saveNovel() keeps the cached
        // chapter list (and library flag) when the incoming row lacks them.
        return saveNovel(rewrapSlug(result, source.id))
    }

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
        return saveNovel(rewrapSlug(result.first, source.id))
    }

    // Real titles of chapters saved on this device (num -> title). The cached
    // chapter list only stores "num|url", so rows fall back to "Chapter N";
    // this fills in the real title wherever the chapter has been downloaded.
    suspend fun downloadedChapterTitles(slug: String): Map<Int, String> =
        withContext(Dispatchers.IO) {
            chapterDao.downloadedTitles(slug).associate { it.chapterNum to it.title }
        }

    suspend fun getChapterList(slug: String): List<ChapterLink> {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank())
            return parseChapterUrls(novel.chapterUrls)

        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return emptyList()
        saveNovel(rewrapSlug(result.first, source.id))
        return result.second
    }

    // ── Chapter download ──────────────────────────────────────────────────────
    //
    // CHANGE (download fix): two things.
    //  1) ensurePlaceholdersPurged() — one-time removal of chapters an older
    //     build saved from an error/paywall placeholder string.
    //  2) ChapterFetchGuard.check() — sources catch their own errors and
    //     RETURN fake chapter text ("Failed to load chapter: ..."). That used
    //     to be saved as a real chapter and never retried. Now it throws
    //     before anything is written, so the worker/reader sees a real
    //     failure and the chapter stays missing (= retryable).
    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        ensurePlaceholdersPurged()
        val chapterId = "$slug::$chapterNum"
        chapterDao.getById(chapterId)?.let { return it }

        val (source, realSlug) = sourceFor(slug)
        val url = resolveChapterUrl(slug, chapterNum)
            ?: source.buildChapterUrl(realSlug, chapterNum)
        val (title, content) = source.fetchChapterByUrl(url)
        ChapterFetchGuard.check(content)
        val entity = ChapterEntity(
            id = chapterId, novelSlug = slug,
            chapterNum = chapterNum, title = title,
            chapterUrl = url, content = content
        )
        chapterDao.upsert(entity)
        return entity
    }

    // ── One-time cleanup of poisoned chapters ────────────────────────────────
    private val purgeMutex = Mutex()
    @Volatile private var purgeChecked = false

    // Deletes chapters saved from a placeholder string (see ChapterFetchGuard)
    // and repairs the progress rows that counted them as downloaded: a novel
    // that said COMPLETE but is now missing chapters flips to ERROR, so the
    // Downloads screen offers Retry. Runs once per install (prefs flag);
    // afterwards this is just a boolean check.
    private suspend fun ensurePlaceholdersPurged() {
        if (purgeChecked) return
        purgeMutex.withLock {
            if (purgeChecked) return
            if (!downloadPrefs.isPlaceholderPurgeDone()) {
                val removed = chapterDao.purgePlaceholderChapters()
                Log.d(TAG, "Placeholder cleanup: removed $removed poisoned chapter(s)")
                if (removed > 0) {
                    for (row in downloadProgressDao.observeAll().first()) {
                        val real = chapterDao.downloadedCount(row.novelSlug)
                        val status = if (row.status == DownloadStatus.COMPLETE && real < row.totalChapters)
                            DownloadStatus.ERROR else row.status
                        downloadProgressDao.updateProgress(row.novelSlug, real, status)
                    }
                }
                downloadPrefs.setPlaceholderPurgeDone()
            }
            purgeChecked = true
        }
    }

    // ── Launch-time reconcile (download fix) ─────────────────────────────────
    // A DOWNLOADING row is only true while a worker is actually running. If the
    // process was killed (or the worker failed before it could write a final
    // status) the row stays DOWNLOADING forever, and because the concurrency
    // guard counts DOWNLOADING rows, every later download just sits QUEUED.
    // Here: any DOWNLOADING row with no live WorkManager job for it is demoted
    // to PAUSED (the user sees Resume). A job WorkManager has re-scheduled
    // (ENQUEUED) is left alone — it continues by itself. Then the freed slot is
    // handed to the oldest QUEUED download.
    suspend fun reconcileDownloads() = withContext(Dispatchers.IO) {
        var demoted = 0
        for (row in downloadProgressDao.observeAll().first()) {
            if (row.status != DownloadStatus.DOWNLOADING) continue
            val live = try {
                workManager.getWorkInfosForUniqueWork("download_${row.novelSlug}").get().any {
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
                }
            } catch (e: Exception) {
                true // can't tell — leave the row alone rather than guess
            }
            if (!live) {
                downloadProgressDao.updateProgress(
                    row.novelSlug, chapterDao.downloadedCount(row.novelSlug), DownloadStatus.PAUSED
                )
                demoted++
            }
        }
        Log.d(TAG, "reconcileDownloads: $demoted stale DOWNLOADING row(s) set to PAUSED")
        startNextQueued()
    }

    // ── TXT export (see TxtExportWorker) ──────────────────────────────────────
    fun hasExportFolder(): Boolean = downloadPrefs.getExportTreeUri() != null

    fun setExportFolder(treeUri: String) = downloadPrefs.setExportTreeUri(treeUri)

    // KEEP: tapping Export twice while one is running must not restart it.
    // A finished export doesn't block a new one.
    fun enqueueTxtExport(slug: String) {
        workManager.enqueueUniqueWork(
            "export_$slug",
            ExistingWorkPolicy.KEEP,
            TxtExportWorker.buildRequest(slug)
        )
    }

    // ── Download queue (background) ───────────────────────────────────────────

    // CHANGE (partial downloads): queueDownloadAll is now a thin wrapper —
    // the real logic lives in queueDownloadRange below so "download all",
    // "download last N", and "download this range/volume" all share one
    // path instead of three near-duplicate copies of the progress-row /
    // concurrency-guard bookkeeping.
    suspend fun queueDownloadAll(slug: String) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return
        queueDownloadRange(slug, chapters.minOf { it.num }, chapters.maxOf { it.num })
    }

    // CHANGE (partial downloads): downloads only the most recent [count]
    // chapters — e.g. "Last 200". Clamped to the novel's actual chapter
    // range so asking for more than exists just downloads everything.
    suspend fun queueDownloadLast(slug: String, count: Int) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return
        val maxNum = chapters.maxOf { it.num }
        val minNum = chapters.minOf { it.num }
        val start  = (maxNum - count + 1).coerceAtLeast(minNum)
        queueDownloadRange(slug, start, maxNum)
    }

    // CHANGE (partial downloads): the shared entry point. [startChapter]/
    // [endChapter] are inclusive chapter numbers, not list positions — used
    // directly by the Detail screen's custom-range slider and volume chips,
    // and internally by queueDownloadAll/queueDownloadLast above.
    //
    // totalChapters is set to the size of the UNION of chapters already on
    // disk and the newly-requested range, not just the requested range's
    // size — so downloading "Last 50" after already having 300 chapters
    // shows "300 / 300", not a confusing "0 / 50" that ignores what's
    // already there. downloadedChapters is the real on-disk count *before*
    // this job starts; ChapterDownloadWorker updates it to the real count
    // as chapters land (see worker's own fix from the previous pass).
    suspend fun queueDownloadRange(slug: String, startChapter: Int, endChapter: Int) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return

        val allNums       = chapters.map { it.num }.toSet()
        val requestedNums = (startChapter..endChapter).filter { it in allNums }
        if (requestedNums.isEmpty()) return

        queueDownloadInternal(slug, requestedNums, selection = null)
    }

    // First [count] chapters (lowest numbers first).
    suspend fun queueDownloadFirst(slug: String, count: Int) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return
        queueDownloadChapters(slug, chapters.map { it.num }.sorted().take(count).toSet())
    }

    // Everything in the novel that isn't on disk yet. Returns how many chapters
    // were queued (0 = nothing missing) so the UI can say so.
    suspend fun queueDownloadMissing(slug: String): Int {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return 0
        val have    = chapterDao.downloadedChapterNums(slug).toSet()
        val missing = chapters.map { it.num }.filter { it !in have }
        if (missing.isEmpty()) return 0
        queueDownloadChapters(slug, missing.toSet())
        return missing.size
    }

    // Any set of chapters — the multi-select in the chapter list, "First N",
    // "Missing only". Unlike a range these can be non-adjacent; the exact set
    // is kept in DownloadPreferences (see saveSelection) for the worker.
    suspend fun queueDownloadChapters(slug: String, nums: Set<Int>) {
        val chapters = getChapterList(slug)
        if (chapters.isEmpty()) return
        val allNums   = chapters.map { it.num }.toSet()
        val requested = nums.filter { it in allNums }.sorted()
        if (requested.isEmpty()) return
        queueDownloadInternal(slug, requested, selection = requested)
    }

    // Shared body for range and selection downloads: progress row, saved
    // range/selection (for Resume + queued starts), concurrency guard, enqueue.
    private suspend fun queueDownloadInternal(slug: String, requestedNums: List<Int>, selection: List<Int>?) {
        val existingNums = chapterDao.downloadedChapterNums(slug).toSet()
        val unionSize     = (existingNums + requestedNums).size
        val existingRow   = downloadProgressDao.get(slug)   // read BEFORE the upsert below

        // Remember what was asked for so a queued download can start later
        // and Resume/Retry re-downloads this range, not the whole novel.
        downloadPrefs.saveRange(slug, requestedNums.min(), requestedNums.max())
        if (selection != null) downloadPrefs.saveSelection(slug, selection)
        else downloadPrefs.clearSelection(slug)

        Log.d(
            TAG,
            "Queuing download: $slug — chapters ${requestedNums.min()}..${requestedNums.max()} " +
            "(${requestedNums.size} requested, ${existingNums.size} already done, $unionSize total once complete)"
        )

        downloadProgressDao.upsert(
            DownloadProgress(
                novelSlug          = slug,
                totalChapters      = unionSize,
                downloadedChapters = existingNums.size,
                status             = DownloadStatus.QUEUED
            )
        )

        // CHANGE: downloading no longer adds the novel to the Library/Favourites.
        // Favourites are a deliberate choice (the heart); downloaded novels live
        // under "Your novels" in the Downloads tab instead.

        // Same concurrency guard as before — see enqueueDownloadWork's
        // callers and DownloadPreferences.getConcurrentLimit().
        // This novel's own running job doesn't count against the limit — a new
        // request for it simply replaces that job (see enqueueDownloadWork).
        val selfActive  = if (existingRow?.status == DownloadStatus.DOWNLOADING) 1 else 0
        val activeCount = downloadProgressDao.countByStatus(DownloadStatus.DOWNLOADING) - selfActive
        if (activeCount >= downloadPrefs.getConcurrentLimit()) {
            // Stays QUEUED; startNextQueued() picks it up when a slot frees.
            Log.d(TAG, "Concurrency limit reached ($activeCount active) — $slug stays queued")
            return
        }

        enqueueDownloadWork(slug, requestedNums.min(), requestedNums.max())
    }

    // Resume/Retry: re-run the range the user originally asked for. This used
    // to call queueDownloadAll, so resuming a "Last 50" download pulled the
    // entire novel.
    suspend fun resumeDownload(slug: String) {
        val selection = downloadPrefs.getSelection(slug)
        val range     = downloadPrefs.getRange(slug)
        when {
            selection != null -> queueDownloadChapters(slug, selection)
            range != null     -> queueDownloadRange(slug, range.first, range.second)
            else              -> queueDownloadAll(slug)
        }
    }

    // Called whenever a download slot frees up (finished, paused, deleted, or
    // the Wi-Fi setting changed). Previously a request that hit the concurrency
    // limit was left QUEUED with no work enqueued and nothing ever started it.
    suspend fun startNextQueued() {
        if (downloadProgressDao.countByStatus(DownloadStatus.DOWNLOADING) >= downloadPrefs.getConcurrentLimit()) return
        val next = downloadProgressDao.firstWithStatus(DownloadStatus.QUEUED) ?: return

        val range = downloadPrefs.getRange(next.novelSlug) ?: run {
            // Queued before ranges were stored: fall back to whatever is missing.
            val have    = chapterDao.downloadedChapterNums(next.novelSlug).toSet()
            val missing = getChapterList(next.novelSlug).filter { it.num !in have }
            if (missing.isEmpty()) {
                downloadProgressDao.updateProgress(next.novelSlug, have.size, DownloadStatus.COMPLETE)
                return
            }
            missing.minOf { it.num } to missing.maxOf { it.num }
        }
        Log.d(TAG, "Starting queued download: ${next.novelSlug} (${range.first}..${range.second})")
        enqueueDownloadWork(next.novelSlug, range.first, range.second)
    }

    // CHANGE (Downloads overhaul): shared by every queue* method above and
    // checkForUpdates so wifi-only handling lives in exactly one place.
    private fun enqueueDownloadWork(slug: String, startChapter: Int, endChapter: Int) {
        val request = ChapterDownloadWorker.buildRequest(
            slug         = slug,
            startChapter = startChapter,
            endChapter   = endChapter,
            network      = downloadPrefs.getNetworkMode()
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
        startNextQueued()
    }

    // CHANGE (Downloads overhaul): frees a novel's downloaded chapters to
    // reclaim storage. Cancels any in-flight work first so a running
    // worker can't recreate a chapter mid-delete. Library membership is
    // left untouched on purpose — deleting a download frees space, it does
    // not remove the novel from the user's Library.
    suspend fun deleteDownload(slug: String) {
        workManager.cancelAllWorkByTag(slug)
        chapterDao.deleteForNovel(slug)
        downloadProgressDao.delete(slug)
        downloadPrefs.clearRange(slug)
        downloadPrefs.clearSelection(slug)
        startNextQueued()
    }

    // CHANGE (Downloads overhaul): approximate downloaded size for a novel,
    // for the Downloads screen. See ChapterDao.totalContentBytes for the
    // "approximate" caveat.
    suspend fun downloadedSizeBytes(slug: String): Long = chapterDao.totalContentBytes(slug) ?: 0L

    fun downloadPreferences(): DownloadPreferences = downloadPrefs

    // ── Check for new chapters ────────────────────────────────────────────────
    // FIX: "new" used to mean "every chapter not on disk", so after "Last 50"
    // an update check reported (and queued) the whole rest of the novel. It now
    // means chapters newer than the list we already knew about, and they are
    // only auto-downloaded when the user already has a download for this novel.
    suspend fun checkForUpdates(slug: String): Int {
        val (source, realSlug) = sourceFor(slug)

        val previousMax = novelDao.getBySlug(slug)?.chapterUrls
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> parseChapterUrls(raw).maxOfOrNull { it.num } }

        val result = source.fetchDetail(realSlug) ?: return 0
        val (fresh, chapters) = result
        saveNovel(rewrapSlug(fresh, source.id))

        // No earlier chapter list to compare against — nothing can be "new".
        if (previousMax == null) return 0

        val newChapters = chapters.filter { it.num > previousMax }
        Log.d(TAG, "Update check $slug: ${chapters.size} total, previous newest $previousMax, ${newChapters.size} new")
        if (newChapters.isEmpty()) return 0

        if (downloadProgressDao.get(slug) != null) {
            val have  = chapterDao.downloadedChapterNums(slug).toSet()
            val toGet = newChapters.filter { it.num !in have }
            if (toGet.isNotEmpty()) queueDownloadRange(slug, toGet.minOf { it.num }, toGet.maxOf { it.num })
        }
        return newChapters.size
    }

    // ── Library ───────────────────────────────────────────────────────────────
    fun libraryFlow(): Flow<List<NovelEntity>> = novelDao.libraryFlow()
    fun isInLibraryFlow(slug: String): Flow<Boolean> = novelDao.isInLibraryFlow(slug).map { it ?: false }
    fun downloadedNovelsFlow(): Flow<List<NovelEntity>> = novelDao.downloadedNovelsFlow()
    fun downloadedNumsFlow(slug: String): Flow<List<Int>> = chapterDao.downloadedNumsFlow(slug)
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

    // Speed: resolveChapterUrl() used to split + sort the novel's whole chapter
    // list string for EVERY chapter (thousands of entries x thousands of
    // chapters). The parsed number->url map is now kept per novel and rebuilt
    // only when the stored list actually changes (compared by content hash).
    private val chapterUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Map<Int, String>>>()

    private fun cachedChapterUrl(slug: String, raw: String, chapterNum: Int): String? {
        val hash   = raw.hashCode()
        val cached = chapterUrlCache[slug]
        val map = if (cached != null && cached.first == hash) cached.second
        else parseChapterUrls(raw).associate { it.num to it.url }.also { chapterUrlCache[slug] = hash to it }
        return map[chapterNum]
    }

    private suspend fun resolveChapterUrl(slug: String, chapterNum: Int): String? {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank()) {
            cachedChapterUrl(slug, novel.chapterUrls, chapterNum)?.let { return it }
        }
        val (source, realSlug) = sourceFor(slug)
        val result = source.fetchDetail(realSlug) ?: return null
        saveNovel(rewrapSlug(result.first, source.id))
        return result.second.find { it.num == chapterNum }?.url
    }
}
