package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// NovelFullSource
//
// Unlike NovelArrow, this was NOT built from lncrawl's Python reference —
// that project only implements search/detail/chapter-list/chapter-body
// (it's a "download one novel you already have a URL for" tool, not a
// browse-and-discover app), so it has no concept of homepage/listing pages
// at all. Everything below — including the listing/card structure — was
// checked directly against three real, live-fetched pages: a novel detail
// page (novelfull.com/dual-cultivation.html), a listing page
// (novelfull.com/hot-novel), and a chapter page. Plain server-rendered
// HTML throughout, no anti-bot wall encountered, no JS payload — the
// simplest of this app's three sources so far.
//
// Confidence varies by piece, noted per-method below:
//   HIGH   — novel URL pattern, chapter URL pattern, genre URL encoding,
//            chapter-list pagination, chapter body container. Seen
//            directly in real fetched HTML.
//   MEDIUM — card parsing on listing pages, author/genre/status extraction
//            on the detail page. Inferred from markdown-rendered structure
//            (real tag names/classes weren't visible), using structural
//            matching (URL shape, label text) rather than guessed CSS
//            classes, but not confirmed against raw HTML.
//   LOW    — search. Never saw a live results page; reusing the card
//            parser on faith that the result markup matches.
class NovelFullSource : NovelSource {

    override val id = "novelfull"
    override val displayName = "NovelFull"
    override val baseUrl = "https://novelfull.com"

    private val BASE = baseUrl
    private val TAG = "NCrawler_NovelFull"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "text/html,*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "$BASE/")
                .build()
            chain.proceed(req)
        }
        .build()

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "GET $url")
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        Log.d(TAG, "HTTP ${resp.code} ← $url")
        resp.use { it.body!!.string() }
    }

    private suspend fun fetchDoc(url: String): Document = Jsoup.parse(fetchText(url), url)

    // ── Homepage / Popular / Genre — all three are the same card list,
    // just a different listing URL. HIGH confidence on the URLs
    // themselves (seen live); MEDIUM on the card parser — see
    // parseNovelCards() below for why.
    override suspend fun fetchHomepage(): List<NovelEntity> {
        Log.d(TAG, "fetchHomepage()")
        return try {
            parseNovelCards(fetchDoc("$BASE/latest-release-novel"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchPopular(): List<NovelEntity> {
        Log.d(TAG, "fetchPopular()")
        return try {
            parseNovelCards(fetchDoc("$BASE/hot-novel"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchPopular failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> {
        Log.d(TAG, "fetchGenre(genre=$genre, page=$page)")
        // HIGH confidence: confirmed live — novelfull.com/genre/<Name>,
        // spaces as '+' (exactly what URLEncoder.encode produces, e.g.
        // "Gender Bender" -> "Gender+Bender").
        val encoded = URLEncoder.encode(genre, "UTF-8")
        val url = if (page <= 1) "$BASE/genre/$encoded" else "$BASE/genre/$encoded?page=$page"
        return try {
            parseNovelCards(fetchDoc(url))
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenre('$genre', page=$page) failed: ${e.message}", e)
            throw e
        }
    }

    // Extra homepage rows beyond Latest/Popular — same generic mechanism
    // built for NovelArrow. This site's listing cards don't carry genre
    // tags either (confirmed: /hot-novel's main card list has none, only
    // its separate numbered sidebar widget does), so knownGenres() below
    // feeds the showcase row instead, and these render through the same
    // flat-GenreRow fallback already in BrowseScreen — no UI changes needed.
    override suspend fun fetchExtraSections(): List<HomeSection> {
        Log.d(TAG, "fetchExtraSections()")
        val specs = listOf(
            "Completed Novels" to "$BASE/completed-novel",
            "Most Popular"     to "$BASE/most-popular"
        )
        return specs.mapNotNull { (title, url) ->
            try {
                val novels = parseNovelCards(fetchDoc(url))
                Log.d(TAG, "fetchExtraSections: '$title' → ${novels.size} novels")
                if (novels.isEmpty()) null else HomeSection(title, novels)
            } catch (e: Exception) {
                Log.w(TAG, "fetchExtraSections: '$title' ($url) failed: ${e.message}")
                null
            }
        }
    }

    // Union of what showed up across two different pages while checking
    // this site (the detail page's nav sidebar showed 36, the /hot-novel
    // page's own genre block showed 43 — some genres only appeared on one
    // or the other, likely a menu-length difference, not a real mismatch).
    override fun knownGenres(): List<String> = listOf(
        "Action", "Adult", "Adventure", "Comedy", "Drama", "Eastern", "Ecchi",
        "Fan-fic", "Fantasy", "Game", "Gender Bender", "Harem", "Historical",
        "History", "Horror", "Josei", "Lolicon", "Magical Realism", "Martial",
        "Martial Arts", "Mature", "Mecha", "Mystery", "Psychological",
        "Psychologic", "Reincarnation", "Romance", "School Life", "Sci-fi",
        "Seinen", "Shoujo", "Shounen", "Shounen Ai", "Slice of Life", "Smut",
        "Sports", "Supernatural", "System", "Tragedy", "Wuxia", "Xianxia",
        "Xuanhuan", "Yaoi", "Yuri"
    )

    // LOW confidence — never saw a live results page for this one. If a
    // query that should clearly have hits comes back empty, this is the
    // first place to look: check whether /search?keyword= even returns
    // cards in the same shape as the other listing pages.
    override suspend fun search(query: String): List<NovelEntity> {
        Log.d(TAG, "search('$query')")
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val novels = parseNovelCards(fetchDoc("$BASE/search?keyword=$encoded"))
            Log.d(TAG, "search('$query'): ${novels.size} items")
            novels
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        Log.d(TAG, "fetchDetail($slug)")
        return try {
            val html = fetchText("$BASE/$slug.html")
            val doc = Jsoup.parse(html, "$BASE/$slug.html")

            val title = doc.selectFirst("h3.title")?.text()?.trim()
                ?: doc.select("h3").firstOrNull { it.text().isNotBlank() }?.text()?.trim()
                ?: return null

            val cover = doc.selectFirst("img[src*=uploads/webp/novel], .book img")
                ?.attr("abs:src").orEmpty()

            // MEDIUM confidence: no raw class names visible to me, so this
            // finds each field by its own label text ("Author:", "Genre:",
            // "Status:" — WITH the colon, which only the detail-page
            // labels have; the nav sidebar's "Genre" toggle has no colon,
            // so this can't accidentally sweep in the ~40-entry nav menu)
            // and reads whatever <a> tags follow it, up to the next
            // heading. Logged counts below so a live mismatch is obvious.
            val author = valuesAfterLabel(html, "Author:", "/author/").joinToString(", ")
            val genres = valuesAfterLabel(html, "Genre:", "/genre/").joinToString(", ")
            val status = valuesAfterLabel(html, "Status:", "/status/").firstOrNull().orEmpty()
            Log.d(TAG, "fetchDetail($slug): author='$author' genres='$genres' status='$status'")

            val rating = Regex("Rating:\\s*\\*\\*([0-9.]+)").find(html)?.groupValues?.get(1)
                ?: Regex("Rating:\\s*([0-9.]+)").find(doc.text())?.groupValues?.get(1)
                ?: ""

            val synopsis = doc.selectFirst(".desc-text, div.desc-text, .desc")
                ?.select("p")?.joinToString("\n\n") { it.text().trim() }
                ?.ifBlank { null }.orEmpty()

            val chapters = fetchAllChapters(slug, doc)
            Log.d(TAG, "Detail: title=$title chapters=${chapters.size} rating=$rating")

            val novel = NovelEntity(
                slug = slug, title = title, coverUrl = cover,
                synopsis = synopsis, status = status, rating = rating,
                genres = genres, chapterCount = chapters.size, latestChapter = ""
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    // HIGH confidence — chapter list is embedded directly in the detail
    // page (no AJAX call needed, unlike what the old lncrawl template
    // suggested — that may be outdated, or an optimization this live
    // structure doesn't need). Confirmed pagination via a real 23-page
    // novel: ?page=N, with a "Last »" link giving the true final page
    // number directly, so no guessing where the list ends. The homepage's
    // "Latest chapters" preview and the real "Chapter List" both link the
    // same chapters — collecting from the whole page and deduping by URL
    // handles the overlap without needing to scope to one section only.
    private suspend fun fetchAllChapters(slug: String, firstPageDoc: Document): List<ChapterLink> {
        val seen = LinkedHashMap<String, ChapterLink>()
        fun collect(doc: Document) {
            doc.select("a[href*=/$slug/]").forEach { a ->
                val href = a.attr("abs:href")
                if (!href.endsWith(".html")) return@forEach
                val title = a.text().trim().ifBlank { return@forEach }
                if (!seen.containsKey(href)) {
                    seen[href] = ChapterLink(num = seen.size + 1, title = title, url = href)
                }
            }
        }
        collect(firstPageDoc)

        val lastPageHref = firstPageDoc.select("a").firstOrNull { it.text().trim() == "Last »" }
            ?.attr("abs:href")
        val totalPages = lastPageHref
            ?.let { Regex("page=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 1
        Log.d(TAG, "fetchAllChapters($slug): page 1 → ${seen.size} chapters so far, totalPages=$totalPages")

        // Safety cap — a genuinely 500+ page novel would be unusual; guard
        // against ever looping on a misparsed page count.
        val pagesToFetch = totalPages.coerceAtMost(300)
        for (page in 2..pagesToFetch) {
            val doc = fetchDoc("$BASE/$slug.html?page=$page")
            collect(doc)
            Log.d(TAG, "fetchAllChapters($slug): page $page/$pagesToFetch → ${seen.size} chapters so far")
        }

        // Chapter numbers assigned in collection order, which follows
        // document order per page — matches ascending chapter order as
        // seen on every page checked.
        return seen.values.toList()
    }

    // Chapter body container confirmed live (#chr-content). A small set
    // of known ad/spam phrases gets filtered out of individual paragraphs
    // — seen one such line inline in real chapter text during
    // verification ("Find authorized novels in Webnovel..."); this list
    // is only as complete as what showed up in that one chapter, so a
    // similar leftover line elsewhere is the likely next thing to add
    // here rather than a sign anything is broken.
    private val AD_PHRASES = listOf(
        "please click www", "faster updates, better experience",
        "find authorized novels", "report chapter"
    )

    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val doc = fetchDoc(url)
            val title = doc.selectFirst("a[href='$url']")?.text()?.trim()
                ?: doc.selectFirst("h2, h3")?.text()?.trim()
                ?: "Chapter"

            val container = doc.selectFirst("#chr-content, #chapter-content")
            val content = container?.select("p")
                ?.map { it.text().trim() }
                ?.filter { it.isNotBlank() }
                ?.filterNot { p -> AD_PHRASES.any { p.contains(it, ignoreCase = true) } }
                ?.joinToString("\n\n")
                .orEmpty()

            if (content.isBlank()) {
                Log.w(TAG, "Chapter content empty — url=$url")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Log.d(TAG, "Chapter content: ${content.length} chars")
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    // Only reached when a chapter's real URL wasn't in the cached
    // chapterUrls map — normal use always has it, since fetchDetail()
    // scrapes real URLs directly. This guess will almost always 404
    // (NovelFull chapter slugs carry a title suffix, e.g.
    // "chapter-1-su-yang.html", not just a number), but it's a better
    // failure than crashing.
    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/$slug/chapter-$chapterNum.html"

    // Finds the given label (WITH its colon — only detail-page field
    // labels have one; the nav sidebar's "Genre" toggle doesn't) and
    // collects hrefContains-matching <a> tags up to the next <h3>
    // heading, so this only ever reads the novel's own field value(s),
    // never the much larger nav menu.
    private fun valuesAfterLabel(html: String, label: String, hrefContains: String): List<String> {
        val labelIdx = html.indexOf(label)
        if (labelIdx == -1) return emptyList()
        val nextHeadingIdx = html.indexOf("<h3", labelIdx + label.length)
            .let { if (it == -1) html.length else it }
        val segment = html.substring(labelIdx, nextHeadingIdx)
        return Regex("href=\"[^\"]*${Regex.escape(hrefContains)}[^\"]*\"[^>]*>([^<]+)<")
            .findAll(segment)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    // ── Card parser for listing pages (Latest/Popular/Genre/Search/extra
    // sections) — MEDIUM confidence. No raw class names were visible to
    // me (markdown-rendered pages hide real tags/classes), so this
    // matches structurally instead of guessing CSS: a novel-detail link
    // is any <a> whose href is exactly one path segment ending in
    // ".html" — confirmed live to never match genre/author/status/nav
    // links (none of those end in .html) or chapter links (those have
    // TWO path segments, "/<slug>/<chapter-slug>.html"). Cover images are
    // paired to title links by position, truncated to however many cover
    // images actually exist — on every listing page checked, the main
    // card list (which has covers) appears before any img-less sidebar
    // widget (which doesn't), so this naturally excludes sidebar
    // duplicates without needing to know its container's class either.
    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        // Matching the href in plain Kotlin rather than through Jsoup's
        // [href~=regex] CSS syntax — that regex's own [a-z0-9-] character
        // class nests inside the CSS selector's own [...] delimiters,
        // which Jsoup's selector parser isn't guaranteed to handle
        // correctly. This also sidesteps a genuine overload ambiguity:
        // .filter{} chained directly off Elements (Jsoup's List<Element>)
        // resolved against Node's own filter(NodeFilter) method instead
        // of the Kotlin stdlib one — .toList() below forces it back to an
        // unambiguous kotlin.collections.List first.
        val novelHrefPattern = Regex("^/[a-z0-9-]+\\.html$")
        val titleLinks = doc.select("a[href]").toList()
            .filter { novelHrefPattern.matches(it.attr("href")) && it.text().isNotBlank() }
        val covers = doc.select("img[src*=uploads/webp/novel]")
        Log.d(TAG, "parseNovelCards: found ${titleLinks.size} title links, ${covers.size} cover images")

        val cardCount = minOf(titleLinks.size, covers.size)
        val result = mutableListOf<NovelEntity>()
        for (i in 0 until cardCount) {
            val a = titleLinks[i]
            val href = a.attr("abs:href")
            val slug = Regex("/([a-z0-9-]+)\\.html$").find(href)?.groupValues?.get(1)
                ?: continue
            if (result.any { it.slug == slug }) continue

            val title = a.text().trim()
            if (title.isBlank()) continue
            val cover = covers.getOrNull(i)?.attr("abs:src").orEmpty()

            result.add(NovelEntity(
                slug = slug, title = title, coverUrl = cover,
                synopsis = "", status = "", rating = "",
                genres = "", chapterCount = 0, latestChapter = ""
            ))
        }
        Log.d(TAG, "parseNovelCards: returning ${result.size} novels")
        return result.distinctBy { it.slug }.take(60)
    }
}
