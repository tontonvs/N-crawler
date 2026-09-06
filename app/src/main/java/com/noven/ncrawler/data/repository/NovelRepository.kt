package com.noven.ncrawler.data.repository

import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.scraper.NovelArrowScraper
import kotlinx.coroutines.flow.Flow

class NovelRepository(db: AppDatabase) {

    private val novelDao   = db.novelDao()
    private val chapterDao = db.chapterDao()
    private val scraper    = NovelArrowScraper()

    // ── Browse ────────────────────────────────────────────────────────────────
    suspend fun fetchHomepage(): List<NovelEntity> {
        val novels = scraper.fetchHomepage()
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    // ── Search ────────────────────────────────────────────────────────────────
    suspend fun search(query: String): List<NovelEntity> {
        val local  = novelDao.searchLocal(query)
        val remote = try { scraper.search(query) } catch (_: Exception) { emptyList() }
        if (remote.isNotEmpty()) novelDao.upsertAll(remote)
        return remote.ifEmpty { local }
    }

    // ── Detail — returns novel + chapter list with real URLs ──────────────────
    suspend fun getNovel(slug: String): NovelEntity? {
        val cached = novelDao.getBySlug(slug)
        // Use cache only if we have synopsis AND chapter URLs
        if (cached != null && cached.synopsis.isNotBlank() && cached.chapterUrls.isNotBlank()) {
            return cached
        }
        val result = scraper.fetchDetail(slug) ?: return cached
        val (fresh, _) = result
        novelDao.upsert(fresh)
        return fresh
    }

    // Returns ordered chapter list for a novel (from cached URL map or fresh fetch)
    suspend fun getChapterList(slug: String): List<ChapterLink> {
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank()) {
            return parseChapterUrls(novel.chapterUrls)
        }
        val result = scraper.fetchDetail(slug) ?: return emptyList()
        val (fresh, links) = result
        novelDao.upsert(fresh)
        return links
    }

    private fun parseChapterUrls(raw: String): List<ChapterLink> =
        raw.split("\t").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull() ?: return@mapNotNull null
                ChapterLink(num = num, title = "Chapter $num", url = parts[1])
            } else null
        }.sortedByDescending { it.num }

    // ── Library ───────────────────────────────────────────────────────────────
    fun libraryFlow(): Flow<List<NovelEntity>> = novelDao.libraryFlow()
    suspend fun setLibrary(slug: String, inLibrary: Boolean) =
        novelDao.setLibrary(slug, inLibrary)

    // ── Chapters ──────────────────────────────────────────────────────────────
    fun chaptersFlow(slug: String) = chapterDao.chaptersForNovel(slug)

    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        // Check local cache first
        chapterDao.getById("$slug::$chapterNum")?.let { return it }

        // Get the real URL for this chapter
        val chapterUrl = resolveChapterUrl(slug, chapterNum)
            ?: return ChapterEntity(
                id = "$slug::$chapterNum", novelSlug = slug,
                chapterNum = chapterNum, title = "Chapter $chapterNum",
                chapterUrl = "", content = "Could not find chapter URL. Try refreshing the novel."
            )

        val (title, content) = scraper.fetchChapterByUrl(chapterUrl)
        val entity = ChapterEntity(
            id         = "$slug::$chapterNum",
            novelSlug  = slug,
            chapterNum = chapterNum,
            title      = title,
            chapterUrl = chapterUrl,
            content    = content
        )
        chapterDao.upsert(entity)
        return entity
    }

    private suspend fun resolveChapterUrl(slug: String, chapterNum: Int): String? {
        // Try from cached novel's URL map
        val novel = novelDao.getBySlug(slug)
        if (novel != null && novel.chapterUrls.isNotBlank()) {
            val links = parseChapterUrls(novel.chapterUrls)
            links.find { it.num == chapterNum }?.url?.let { return it }
        }
        // Re-fetch detail to get chapter URLs
        val result = scraper.fetchDetail(slug) ?: return null
        val (fresh, links) = result
        novelDao.upsert(fresh)
        return links.find { it.num == chapterNum }?.url
    }
}
