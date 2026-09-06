package com.noven.ncrawler.data.repository

import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.NovelArrowScraper
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth. ViewModels only touch the Repository.
 *
 * Strategy:
 *  - Network first for homepage / search (fresh content)
 *  - Cache results into Room immediately
 *  - Library is always Room (local, persistent)
 */
class NovelRepository(db: AppDatabase) {

    private val novelDao   = db.novelDao()
    private val chapterDao = db.chapterDao()
    private val scraper    = NovelArrowScraper()

    // ── Browse ────────────────────────────────────────────────────────────
    suspend fun fetchHomepage(): List<NovelEntity> {
        val novels = scraper.fetchHomepage()
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    // ── Search ────────────────────────────────────────────────────────────
    suspend fun search(query: String): List<NovelEntity> {
        // 1. Check local cache first (instant results)
        val local = novelDao.searchLocal(query)

        // 2. Fire network search in parallel — results replace local list
        val remote = try { scraper.search(query) } catch (_: Exception) { emptyList() }
        if (remote.isNotEmpty()) novelDao.upsertAll(remote)

        return remote.ifEmpty { local }
    }

    // ── Detail ────────────────────────────────────────────────────────────
    suspend fun getNovel(slug: String): NovelEntity? {
        val cached = novelDao.getBySlug(slug)
        if (cached != null && cached.synopsis.isNotBlank()) return cached

        val fresh = scraper.fetchDetail(slug) ?: return cached
        novelDao.upsert(fresh)
        return fresh
    }

    // ── Library ───────────────────────────────────────────────────────────
    fun libraryFlow(): Flow<List<NovelEntity>> = novelDao.libraryFlow()

    suspend fun setLibrary(slug: String, inLibrary: Boolean) =
        novelDao.setLibrary(slug, inLibrary)

    // ── Chapters ──────────────────────────────────────────────────────────
    fun chaptersFlow(slug: String) = chapterDao.chaptersForNovel(slug)

    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        val existing = chapterDao.getById("$slug::$chapterNum")
        if (existing != null) return existing

        val (title, content) = scraper.fetchChapter(slug, chapterNum)
        val entity = ChapterEntity(
            id         = "$slug::$chapterNum",
            novelSlug  = slug,
            chapterNum = chapterNum,
            title      = title,
            content    = content
        )
        chapterDao.upsert(entity)
        return entity
    }
}
