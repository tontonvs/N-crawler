package com.noven.ncrawler.data.repository

import com.noven.ncrawler.data.db.AppDatabase
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import com.noven.ncrawler.data.scraper.FreeWebNovelScraper
import kotlinx.coroutines.flow.Flow

class NovelRepository(db: AppDatabase) {

    private val novelDao   = db.novelDao()
    private val chapterDao = db.chapterDao()
    private val scraper    = FreeWebNovelScraper()

    suspend fun fetchHomepage(): List<NovelEntity> {
        val novels = scraper.fetchHomepage()
        if (novels.isNotEmpty()) novelDao.upsertAll(novels)
        return novels
    }

    suspend fun search(query: String): List<NovelEntity> {
        val local  = novelDao.searchLocal(query)
        val remote = try { scraper.search(query) } catch (_: Exception) { emptyList() }
        if (remote.isNotEmpty()) novelDao.upsertAll(remote)
        return remote.ifEmpty { local }
    }

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

    fun libraryFlow(): Flow<List<NovelEntity>> = novelDao.libraryFlow()
    suspend fun setLibrary(slug: String, inLibrary: Boolean) =
        novelDao.setLibrary(slug, inLibrary)
    fun chaptersFlow(slug: String) = chapterDao.chaptersForNovel(slug)

    suspend fun downloadChapter(slug: String, chapterNum: Int): ChapterEntity {
        chapterDao.getById("$slug::$chapterNum")?.let { return it }

        // Resolve URL — from cache first, then predictable pattern (confirmed working)
        val chapterUrl = resolveChapterUrl(slug, chapterNum)
            ?: scraper.buildChapterUrl(slug, chapterNum)

        val (title, content) = scraper.fetchChapterByUrl(chapterUrl)
        val entity = ChapterEntity(
            id = "$slug::$chapterNum", novelSlug = slug,
            chapterNum = chapterNum, title = title,
            chapterUrl = chapterUrl, content = content
        )
        chapterDao.upsert(entity)
        return entity
    }

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
