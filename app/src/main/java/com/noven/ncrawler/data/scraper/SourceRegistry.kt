package com.noven.ncrawler.data.scraper

/**
 * Every source the app knows how to scrape. Adding a new site means writing
 * one class implementing NovelSource, then adding one line here.
 */
object SourceRegistry {

    /** Used for legacy bare-slug rows (pre-multi-source library/reading history) and as the fallback default. */
    const val DEFAULT_SOURCE_ID = "freewebnovel"

    private val all: List<NovelSource> = listOf(
        FreeWebNovelScraper(),
        NovelLiveSource(),
        // CHANGE: NovelArrow added — clean JSON API for detail + search (no
        // Cloudflare wall, live-verified), HTML scraping for listings,
        // JSON-embedded-HTML extraction for chapter content. See
        // NovelArrowSource.kt header for the full confirmed structure.
        NovelArrowSource(),
    )

    fun all(): List<NovelSource> = all

    fun byId(id: String): NovelSource =
        all.find { it.id == id } ?: all.first { it.id == DEFAULT_SOURCE_ID }
}
