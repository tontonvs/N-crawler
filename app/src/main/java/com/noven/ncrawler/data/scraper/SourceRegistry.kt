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
        // CHANGE: NovelPing added (novelping.com — the site novelarrow.com became).
        // Detail/search/chapter list use the JSON API family NovelArrowSource
        // reverse-engineered, with HTML fallbacks; chapter text comes from the
        // page's chr-content block. For existing users it starts DISABLED
        // (SourcePreferences never enables a new source silently) — switch it
        // on in Settings > Sources.
        NovelPingSource(),
    )

    fun all(): List<NovelSource> = all

    /** Group key for novels whose source is no longer registered (e.g. the retired "novelarrow"). */
    const val UNKNOWN_SOURCE_ID = "unknown"

    /**
     * CHANGE (source folders): the one place that reads the source out of a composite
     * "<sourceId>::<realSlug>" slug. Bare legacy slugs are FreeWebNovel; ids that are
     * no longer registered collapse into UNKNOWN_SOURCE_ID so they share one folder
     * instead of each retired site getting its own.
     */
    fun sourceIdOf(slug: String): String {
        val idx = slug.indexOf("::")
        val id = if (idx == -1) DEFAULT_SOURCE_ID else slug.substring(0, idx)
        return if (all.any { it.id == id }) id else UNKNOWN_SOURCE_ID
    }

    /** Human-readable name for a group key from sourceIdOf(). Never throws. */
    fun displayNameOf(sourceId: String): String =
        all.find { it.id == sourceId }?.displayName ?: "Unknown source"

    // FIX: no silent fallback to the default source. An unknown id (old
    // "novelarrow::" library rows) used to fetch the wrong site with a slug it
    // never had; now it fails with a clear message and cached chapters still
    // open offline.
    fun byId(id: String): NovelSource =
        all.find { it.id == id }
            ?: throw IllegalStateException("The source \"$id\" is no longer supported, so this novel can't be refreshed.")
}
