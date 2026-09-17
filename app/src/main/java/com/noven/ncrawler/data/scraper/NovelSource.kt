package com.noven.ncrawler.data.scraper

import com.noven.ncrawler.data.db.NovelEntity

/**
 * The contract every site-specific scraper implements. Adding a new source
 * means writing one class that implements this interface — nothing else in
 * the app (repository plumbing aside) needs to know a new site exists.
 *
 * All NovelEntity.slug values returned here should be the REAL site-relative
 * slug (e.g. "cultivation-online"), never source-prefixed — NovelRepository
 * is the only place that adds/strips the "sourceId::slug" composite format
 * used for caching and navigation. Keeping that prefixing out of the source
 * implementations means each scraper only ever has to think about its own
 * site, not the multi-source system wrapped around it.
 */
interface NovelSource {

    /** Stable, lowercase, no-spaces identifier — used as the DB/prefs key. Never change once shipped. */
    val id: String

    /** Human-readable name shown in the source picker, e.g. "FreeWebNovel". */
    val displayName: String

    /** Root URL, e.g. "https://freewebnovel.com" — shown in Settings for transparency. */
    val baseUrl: String

    suspend fun fetchHomepage(): List<NovelEntity>

    suspend fun fetchPopular(): List<NovelEntity>

    suspend fun fetchGenre(genre: String, page: Int = 1): List<NovelEntity>

    suspend fun search(query: String): List<NovelEntity>

    /** Returns null if the novel/slug doesn't exist on this source. */
    suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>?

    /** Returns (chapterTitle, chapterContent). */
    suspend fun fetchChapterByUrl(url: String): Pair<String, String>

    /** Predictable chapter URL builder, used when a chapter isn't in the cached URL map. */
    fun buildChapterUrl(slug: String, chapterNum: Int): String
}
