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
        // CHANGE: NovelFull added — plain server-rendered HTML throughout,
        // no anti-bot wall hit during verification. Confidence varies by
        // method; see NovelFullSource.kt header for what was confirmed
        // live vs. inferred structurally.
        NovelFullSource(),
        // CHANGE: NovelArrow removed. That site rebranded twice inside a
        // year (novelbin → novelarrow.com → novelping.com, the last one
        // just days after NovelArrowSource.kt was written) — left
        // unregistered rather than deleted, since the reverse-engineering
        // in that file (the React-streamed chapter body format especially)
        // took real investigation and might be worth revisiting if that
        // lineage ever settles down. NovelArrowSource(),
        //
        // CHANGE: two replacements added in its place —
        // NovelBuddySource — mirrors the existing NovelFull catalog on an
        //   independent domain (redundancy: NovelFull breaking doesn't cost
        //   this content too), and
        NovelBuddySource(),
        // LightNovelWorldSource — a new, previously-uncovered catalog, from
        //   a long-established aggregator with no relation to the
        //   novelbin/novelarrow/novelping lineage above.
        LightNovelWorldSource(),
    )

    fun all(): List<NovelSource> = all

    fun byId(id: String): NovelSource =
        all.find { it.id == id } ?: all.first { it.id == DEFAULT_SOURCE_ID }
}
