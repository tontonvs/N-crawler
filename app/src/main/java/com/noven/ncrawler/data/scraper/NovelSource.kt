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

    // CHANGE (perf fix): metadata-only fetch, deliberately without the
    // chapter list — lets the Detail screen paint title/cover/synopsis
    // immediately instead of blocking on fetchDetail()'s full chapter fetch
    // (which can mean several sequential paginated requests for a long
    // novel). Defaults to calling fetchDetail() and discarding the chapter
    // list, so every source keeps working exactly as before with zero
    // changes required; only a source actually worth the speed-up needs to
    // override this with a real metadata-only endpoint call.
    suspend fun fetchInfo(slug: String): NovelEntity? = fetchDetail(slug)?.first

    /** Returns (chapterTitle, chapterContent). */
    suspend fun fetchChapterByUrl(url: String): Pair<String, String>

    /** Predictable chapter URL builder, used when a chapter isn't in the cached URL map. */
    fun buildChapterUrl(slug: String, chapterNum: Int): String

    // CHANGE: two purely-additive, defaulted hooks — a source only needs to
    // override these if it actually has extra homepage rows / a published
    // genre list beyond what fetchHomepage()/fetchPopular() already cover.
    // Every existing source (FreeWebNovel, NovelLive) inherits the empty
    // defaults below and is completely unaffected.

    /**
     * Extra homepage rows beyond fetchHomepage()/fetchPopular() — e.g.
     * NovelArrow's own "Completed Novels" / "Ongoing Novels" / "New Novels"
     * sections. Empty by default; the UI renders whatever comes back
     * generically, so a source can add or remove sections here without any
     * other file needing to know or care.
     */
    suspend fun fetchExtraSections(): List<HomeSection> = emptyList()

    /**
     * The site's own genre taxonomy, when it publishes one independently of
     * whatever genre tags happen to be scraped onto novel cards. Empty by
     * default. Not suspend — this is meant to be static, in-memory data
     * (a hardcoded list, not a network call); a source that needs to fetch
     * it remotely should cache that itself rather than block on every call.
     */
    fun knownGenres(): List<String> = emptyList()
}

/** One extra homepage row a source can supply — see fetchExtraSections() above. */
data class HomeSection(
    val title: String,
    val novels: List<NovelEntity>
)
