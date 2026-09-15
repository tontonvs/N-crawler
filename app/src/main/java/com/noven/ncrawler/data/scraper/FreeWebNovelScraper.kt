package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Scraper for freewebnovel.com — all selectors verified from live HTML.
 *
 * Confirmed URL patterns:
 *   Homepage    : /sort/latest-release/english-novel
 *   Search      : /search/?searchkey=<query>
 *   Novel detail: /novel/<slug>
 *   Chapter     : /novel/<slug>/chapter-<N>   (no .html extension)
 *
 * Confirmed selectors:
 *   Novel listing cards : h3 > a[href*='/novel/']
 *   Cover (listing)     : img above each h3 card
 *   Cover (detail)      : meta[property=og:image]
 *   Title (detail)      : meta[property=og:title] or h3 in .det-info
 *   Synopsis            : meta[property=og:description]
 *   Genres              : meta[property=og:novel:genre]
 *   Status              : meta[property=og:novel:status]
 *   Latest chapter URL  : meta[property=og:novel:lastest_chapter_url]
 *   Chapter links       : a[href*='/novel/<slug>/chapter-']
 *   Chapter content     : div.txt p  (plain paragraphs, verified in live HTML)
 *   Chapter title       : h1 or the breadcrumb last item
 */
class FreeWebNovelScraper {

    private val BASE = "https://freewebnovel.com"
    private val TAG  = "NCrawler_Scraper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "$BASE/home")
                .build()
            chain.proceed(req)
        }
        .build()

    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        Log.d(TAG, "GET $url")
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        Log.d(TAG, "HTTP ${resp.code} ← $url")
        val body = resp.use { it.body!!.string() }
        Log.d(TAG, "Body: ${body.length} chars")
        Jsoup.parse(body, url)
    }

    // ── Homepage ──────────────────────────────────────────────────────────────
    // Confirmed URL: /sort/latest-release/english-novel
    suspend fun fetchHomepage(): List<NovelEntity> {
        Log.d(TAG, "fetchHomepage()")
        return try {
            val doc = fetch("$BASE/sort/latest-release/english-novel")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
            throw e
        }
    }

    // ── Popular ───────────────────────────────────────────────────────────────
    // Confirmed URL: /sort/most-popular — same card markup as the homepage
    // listing, just a different sort order, so it reuses parseNovelCards.
    suspend fun fetchPopular(): List<NovelEntity> {
        Log.d(TAG, "fetchPopular()")
        return try {
            val doc = fetch("$BASE/sort/most-popular")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPopular failed: ${e.message}", e)
            throw e
        }
    }

    // ── Genre listing (paginated) ───────────────────────────────────────────
    // URL pattern inferred from the confirmed /sort/latest-release/english-novel
    // pagination (…/english-novel/2, /3, …) — genre pages are built by the same
    // site template, so page 1 is /genre/<Genre> and further pages are
    // /genre/<Genre>/<page>. Not yet verified live against page 2+; if this
    // turns out wrong, the logcat GET/HTTP lines below will show exactly what
    // URL was hit and what came back, same as how the cover bug got diagnosed.
    suspend fun fetchGenre(genre: String, page: Int = 1): List<NovelEntity> {
        Log.d(TAG, "fetchGenre(genre=$genre, page=$page)")
        val encodedGenre = java.net.URLEncoder.encode(genre, "UTF-8").replace("+", "+")
        val url = if (page <= 1) "$BASE/genre/$encodedGenre" else "$BASE/genre/$encodedGenre/$page"
        return try {
            val doc = fetch(url)
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenre('$genre', page=$page) failed: ${e.message}", e)
            throw e
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────
    // Confirmed URL: /search/?searchkey=<query>
    suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val doc = fetch("$BASE/search/?searchkey=$encoded")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            throw e
        }
    }

    // ── Novel detail + chapter list ───────────────────────────────────────────
    // Confirmed URL: /novel/<slug>
    suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val doc = fetch("$BASE/novel/$slug")

            // Use og: meta tags — most reliable, confirmed present in live HTML
            val title    = doc.select("meta[property=og:title]").attr("content")
                .ifBlank { doc.select("h3").firstOrNull()?.text() ?: return null }
                .removeSuffix(" | Free Web Novel").trim()
            val cover    = doc.select("meta[property=og:image]").attr("content")
            val synopsis = doc.select("meta[property=og:description]").attr("content").trim()
            val genres   = doc.select("meta[property=og:novel:genre]").attr("content")
            val status   = doc.select("meta[property=og:novel:status]").attr("content")

            Log.d(TAG, "Detail: title=$title cover=${cover.take(40)} genres=$genres")

            // Chapter links — confirmed pattern: a[href*='/novel/<slug>/chapter-']
            // Only first 40 shown on page, but URL pattern is fully predictable:
            // /novel/<slug>/chapter-<N>  (no .html)
            val anchorList = doc.select("a[href*='/novel/$slug/chapter-']").toList()
            Log.d(TAG, "Found ${anchorList.size} chapter anchor tags")

            val seenUrls = mutableSetOf<String>()
            val chapters = mutableListOf<ChapterLink>()

            anchorList.forEach { a ->
                val href = a.attr("abs:href").ifBlank { return@forEach }
                val text = a.text().trim().ifBlank { return@forEach }
                if (!seenUrls.add(href)) return@forEach
                val num = Regex("/chapter-(\\d+)$").find(href)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                chapters.add(ChapterLink(num = num, title = text, url = href))
            }
            chapters.sortByDescending { it.num }
            Log.d(TAG, "Parsed ${chapters.size} chapters")

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel  = NovelEntity(
                slug          = slug,
                title         = title,
                coverUrl      = cover,
                synopsis      = synopsis,
                status        = status,
                rating        = "",
                genres        = genres,
                chapterCount  = chapters.size,
                latestChapter = chapters.firstOrNull()?.title ?: "",
                chapterUrls   = urlMap
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    // ── Chapter content ───────────────────────────────────────────────────────
    // Confirmed URL: /novel/<slug>/chapter-<N>  (no .html extension)
    // Confirmed content: chapter text is in plain <p> tags in div.txt
    // Title: confirmed in h1 / breadcrumb area
    suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val doc = fetch(url)

            // Title from h1 or page title meta — .attr() never returns null
            // (empty string when absent), so the old `?: "Chapter"` after it
            // was dead code; .ifBlank{} actually catches the empty case.
            val title = doc.select("h1").firstOrNull()?.text()?.trim()?.ifBlank { null }
                ?: doc.select("meta[property=og:novel:chapter_name]").attr("content").trim().ifBlank { "Chapter" }

            // Content: div.txt p — confirmed in live chapter HTML
            // Each paragraph is a <p> tag inside div.txt
            var paragraphs = doc.select("div.txt p").toList()
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            Log.d(TAG, "div.txt p: got ${paragraphs.size} paragraphs")

            // Fallback: all p tags with substantial text
            if (paragraphs.isEmpty()) {
                paragraphs = doc.select("p").toList()
                    .map { it.text().trim() }
                    .filter { it.length > 20 }
                Log.d(TAG, "Fallback p: got ${paragraphs.size} paragraphs")
            }

            // Strip site watermark lines
            val content = paragraphs
                .filter { p ->
                    !p.contains("freewebnovel", ignoreCase = true) &&
                    !p.contains("libread", ignoreCase = true)
                }
                .joinToString("\n\n")

            if (content.isBlank()) {
                Log.w(TAG, "Content empty after parsing — returning error message")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Log.d(TAG, "Chapter content: ${content.length} chars")
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    // ── Novel card parser ─────────────────────────────────────────────────────
    // Confirmed structure from live HTML:
    //   <img src="...cover..."> inside an <a href="/novel/slug">
    //   <h3><a href="/novel/slug">Title</a></h3>
    //   Rating number as plain text
    //
    // Listing thumbnails are lazy-loaded: the real URL lives in a data-*
    // attribute, and plain `src` is a blank placeholder until the browser
    // scrolls the image into view — which never happens for Jsoup. That's
    // why cards/hero (built from this parser) came back with no cover while
    // the detail page (og:image meta, no lazy-loading involved) worked fine.
    private val coverAttrs = listOf("data-src", "data-original", "data-lazy-src", "data-echo", "src")

    private fun extractCoverUrl(card: Element): String {
        val imgs = card.select("img")
        // Prefer the confirmed article-image path, across any lazy-load attribute
        for (img in imgs) {
            for (attr in coverAttrs) {
                val url = img.attr("abs:$attr")
                if (url.isNotBlank() && url.contains("/files/article/image/")) return url
            }
        }
        // Fallback: first non-blank image URL from any attribute
        for (img in imgs) {
            for (attr in coverAttrs) {
                val url = img.attr("abs:$attr")
                if (url.isNotBlank()) return url
            }
        }
        return ""
    }

    // Confirmed via a live fetch of the listing page: covers are plain,
    // non-lazy `src` attrs pointing straight at /files/article/image/... —
    // so the lazy-load attribute check above is a defensive no-op here, not
    // the actual fix. The real bug: h3's immediate parent does NOT contain
    // the <img> — the cover <a><img></a> and the <h3> title are siblings
    // under a shared row wrapper one or more levels further up (a common
    // "image column / text column" card layout). Climb from h3 until an
    // ancestor's subtree actually contains an <img>, instead of assuming a
    // fixed depth that breaks the moment the markup nests differently.
    private fun findCoverNear(start: Element, maxDepth: Int = 5): String {
        var el: Element? = start
        var depth = 0
        while (el != null && depth < maxDepth) {
            val cover = extractCoverUrl(el)
            if (cover.isNotBlank()) return cover
            el = el.parent()
            depth++
        }
        return ""
    }

    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val result = mutableListOf<NovelEntity>()

        // Select all h3 > a links pointing to /novel/ pages
        val novelLinks = doc.select("h3 > a[href*='/novel/']").toList()
        Log.d(TAG, "parseNovelCards: found ${novelLinks.size} h3>a novel links")

        novelLinks.forEach { a ->
            val href  = a.attr("abs:href")
            val slug  = slugFromUrl(href) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach

            val title = a.text().trim().ifBlank { return@forEach }

            // Cover: climb from the title link until we find an ancestor
            // whose subtree contains the row's <img> (see findCoverNear above)
            val h3     = a.parent() ?: return@forEach
            val cover  = findCoverNear(h3)

            // Rating: text content near the card (plain number like "4.6")
            val infoBlock  = h3.parent() ?: h3
            val ratingText = infoBlock.select("em, [class*=score]")
                .firstOrNull()?.text()?.trim() ?: ""

            // Genres: the 1-2 genre tag links shown per card (e.g. Fantasy, Romance)
            val genresText = infoBlock.select("a[href*='/genre/']")
                .eachText().joinToString(", ")

            Log.d(TAG, "Card: slug=$slug title=$title cover=${cover.takeLast(20)}")

            result.add(NovelEntity(
                slug = slug, title = title, coverUrl = cover,
                synopsis = "", status = "", rating = ratingText,
                genres = genresText, chapterCount = 0, latestChapter = ""
            ))
        }

        Log.d(TAG, "parseNovelCards: returning ${result.size} novels")
        return result.distinctBy { it.slug }.take(60)
    }

    // ── Predictable chapter URL builder ───────────────────────────────────────
    // Confirmed pattern: /novel/<slug>/chapter-<N>
    fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/novel/$slug/chapter-$chapterNum"

    private fun slugFromUrl(url: String): String? =
        Regex("freewebnovel\\.com/novel/([^/?#]+)").find(url)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
}
