package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Scraper for novelarrow.com.
 *
 * Confirmed live:
 *   Detail       : GET /api-web/novels/<slug>                                → clean JSON
 *   Chapter list : GET /api-web/novels/<slug>/chapters?page=&limit=          → clean JSON,
 *                  REAL chapter_id per chapter (title-suffixed, e.g.
 *                  "chapter-1-chen-xuan-system-activation") — the `limit`
 *                  param isn't reliably honored by the API (a 234-chapter
 *                  novel returned all 234 in one page despite limit=50), so
 *                  this always checks pagination.totalPages and loops if >1
 *                  rather than trusting a single page is ever guaranteed.
 *   Search       : GET /api-web/novels?...&sort=SEARCH_KEYWORD&keyword=<q>   → clean JSON
 *   Listing      : HTML — <a href="/novel/<slug>"> cards, title in a
 *                  "line-clamp-2 ... font-bold" span, cover <img> inside a
 *                  "novel-cover-frame" wrapper, rating as counted ★ glyphs
 *                  (class contains "text-site-rating"), status as an SVG
 *                  <title>Completed</title> / <title>Ongoing</title>
 *   Chapter body : URL /chapter/<slug>/<real chapter_id> — content is NOT
 *                  plain HTML, it's embedded in a React Server Component
 *                  streaming payload (self.__next_f.push(...)) as an
 *                  escaped HTML string referenced by a numeric id
 *
 * CHANGE: fetchDetail() previously synthesized chapter-<N> URLs using only
 * totalChapter + first/recentChapter's real titles — this broke for any
 * novel whose chapter IDs carry a title suffix (which turned out to be
 * every chapter EXCEPT the one novel used during initial investigation).
 * Confirmed 404 in production: GET /chapter/<slug>/chapter-1 for a novel
 * whose real id was chapter-1-chen-xuan-system-activation. Now uses the
 * real /chapters endpoint for exact ids on every chapter — no guessing.
 *
 * Rating scale: the API's avgPoint is 0–5, but the rest of this app treats
 * NovelEntity.rating as an "out of 10" string (DetailScreen's star widget
 * divides by 2). Stored here as avgPoint × 2 so it renders correctly
 * everywhere without needing to touch DetailScreen.
 *
 * Premium chapters: some novels here have paid/platinum chapters
 * (coin_price > 0). This scraper does not attempt to fetch those — it
 * detects the premium/platinum flag at fetch-content time and returns a
 * clear "requires purchase" message instead of trying to bypass the
 * paywall. (Not yet flagged in the chapter LIST itself — see note below
 * buildChapterUrl.)
 */
class NovelArrowSource : NovelSource {

    override val id = "novelarrow"
    override val displayName = "NovelArrow"
    override val baseUrl get() = BASE

    private val BASE = "https://novelarrow.com"
    private val API  = "$BASE/api-web"
    private val IMG  = "https://images.novelarrow.com"
    private val TAG  = "NCrawler_NovelArrow"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "$BASE/")
            // Only default to */* when the call didn't already ask for
            // something specific (the chapter-content API needs
            // "application/json" — see CHAPTER_API_HEADERS below).
            if (original.header("Accept") == null) {
                builder.header("Accept", "*/*")
            }
            chain.proceed(builder.build())
        }
        .build()

    private suspend fun fetchText(
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "GET $url")
        val builder = Request.Builder().url(url)
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        val resp = client.newCall(builder.build()).execute()
        Log.d(TAG, "HTTP ${resp.code} ← $url")
        resp.use { it.body!!.string() }
    }

    private suspend fun fetchDoc(url: String): Document = Jsoup.parse(fetchText(url), url)

    // ── Homepage / Popular — HTML scraping (no JSON endpoint exists) ──────────
    override suspend fun fetchHomepage(): List<NovelEntity> {
        Log.d(TAG, "fetchHomepage()")
        return try {
            parseNovelCards(fetchDoc(BASE))
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchPopular(): List<NovelEntity> {
        Log.d(TAG, "fetchPopular()")
        return try {
            parseNovelCards(fetchDoc("$BASE/novels/hot"))
        } catch (e: Exception) {
            Log.w(TAG, "fetchPopular: /novels/hot failed (${e.message}), falling back to homepage")
            parseNovelCards(fetchDoc(BASE))
        }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> {
        Log.d(TAG, "fetchGenre(genre=$genre, page=$page)")
        val url = if (page <= 1) "$BASE/genre/$genre" else "$BASE/genre/$genre/$page"
        return try {
            parseNovelCards(fetchDoc(url))
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenre('$genre', page=$page) failed: ${e.message}", e)
            throw e
        }
    }

    // ── Search — confirmed clean JSON API, no Cloudflare wall ─────────────────
    override suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$API/novels?limit=30&page=1&status=all&sort=SEARCH_KEYWORD&genre=ALL&keyword=$encoded"
        return try {
            val json  = JSONObject(fetchText(url))
            val items = json.optJSONArray("items") ?: JSONArray()
            Log.d(TAG, "search('$query'): ${items.length()} items")
            (0 until items.length()).mapNotNull { i -> novelFromApiItem(items.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            throw e
        }
    }

    private fun novelFromApiItem(item: JSONObject): NovelEntity? {
        val slug  = item.optString("novel_id").ifBlank { return null }
        val title = item.optString("novel_name").ifBlank { return null }
        val genres = item.optJSONArray("novel_genres")?.let { arr ->
            (0 until arr.length()).joinToString(", ") { arr.getString(it) }
        } ?: ""
        val rating = ratingOutOfTen(item.optJSONObject("avgPoint"))
        val recentChapterName = item.optJSONObject("recentChapter")?.optString("chapter_name") ?: ""
        val statusCode = item.optInt("novel_status", 0)

        return NovelEntity(
            slug = slug, title = title, coverUrl = coverUrlFor(slug),
            synopsis = "", status = statusFromCode(statusCode), rating = rating,
            genres = genres, chapterCount = item.optInt("totalChapter", 0),
            latestChapter = recentChapterName
        )
    }

    // ── Detail — confirmed clean JSON API ──────────────────────────────────────
    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val json = JSONObject(fetchText("$API/novels/$slug"))
            val item = json.optJSONObject("item") ?: return null
            val info = item.optJSONObject("novelInfo") ?: return null

            val title = info.optString("novel_name").ifBlank { return null }
            val synopsis = htmlToPlainText(info.optString("novel_desc"))
            val genres = info.optJSONArray("novel_genres")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.getString(it) }
            } ?: ""
            val rating        = ratingOutOfTen(info.optJSONObject("avgPoint"))
            val totalChapters = info.optInt("totalChapter", 0)
            val statusCode    = info.optInt("novel_status", 0)
            val recentChapterName = info.optJSONObject("recentChapter")?.optString("chapter_name") ?: ""

            Log.d(TAG, "Detail: title=$title totalChapters=$totalChapters rating=$rating")

            // Real chapter list — every id/title/url straight from the site's
            // own data, nothing synthesized or guessed.
            val chapters = fetchAllChapters(slug)
            Log.d(TAG, "fetchAllChapters returned ${chapters.size} chapters (expected $totalChapters)")

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel = NovelEntity(
                slug = slug, title = title, coverUrl = coverUrlFor(slug),
                synopsis = synopsis, status = statusFromCode(statusCode), rating = rating,
                genres = genres, chapterCount = chapters.size.takeIf { it > 0 } ?: totalChapters,
                latestChapter = recentChapterName, chapterUrls = urlMap
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    // ── Chapter list — confirmed clean JSON API ─────────────────────────────────
    // GET /api-web/novels/<slug>/chapters?page=&limit= — `limit` isn't
    // reliably honored (observed a 234-chapter novel return everything in
    // one page despite limit=50), so this always checks pagination and
    // loops rather than assuming a single request is ever guaranteed
    // complete. Capped at 20 pages (10,000+ chapters at limit=500) as a
    // sanity bound against a pathological response, not a realistic ceiling.
    private suspend fun fetchAllChapters(slug: String): List<ChapterLink> {
        val all = mutableListOf<ChapterLink>()
        var page = 1
        var totalPages = 1

        while (page <= totalPages && page <= 20) {
            val json = JSONObject(fetchText("$API/novels/$slug/chapters?page=$page&limit=500"))
            val items = json.optJSONArray("items") ?: JSONArray()
            totalPages = json.optJSONObject("pagination")?.optInt("totalPages", 1) ?: 1

            for (i in 0 until items.length()) {
                val ch = items.getJSONObject(i)
                val chapterId = ch.optString("chapter_id")
                if (chapterId.isBlank()) continue
                val chapterName = ch.optString("chapter_name").ifBlank { "Chapter" }
                // Extract the leading number for sorting/display; falls back
                // to list position if a chapter_id is ever non-numeric-prefixed.
                val num = Regex("^chapter-(\\d+)").find(chapterId)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: (all.size + 1)
                all.add(ChapterLink(num = num, title = chapterName, url = chapterUrlFor(slug, chapterId)))
            }
            page++
        }
        return all.sortedByDescending { it.num }
    }

    // ── Chapter content — confirmed clean JSON API ──────────────────────────────
    // GET /api-web/novels/<slug>/chapters/<chapterId> returns the chapter's
    // content directly as clean HTML inside JSON. This replaced an earlier
    // approach that scraped chapter_content out of the page's raw Next.js
    // streaming payload (self.__next_f.push(...) chunks) — that data is
    // real but awkward to parse correctly (chunk references, variable
    // byte-length framing split across separate <script> tags), and
    // consistently failed in practice. This endpoint sidesteps all of it;
    // confirmed via curl to return {"item":{"chapterInfo":{"chapter_content":
    // "<h4>...</h4><p>...</p>...", ...}}} directly, with no scraping needed.
    private val CHAPTER_API_HEADERS = mapOf(
        "Accept" to "application/json",
        "x-client-platform" to "web-desktop",
        "x-device-type" to "desktop",
        "x-site-host" to "novelarrow.com",
        "x-version-app" to "web-desktop"
    )

    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            // Chapter URLs are always $BASE/chapter/<slug>/<chapterId> —
            // see chapterUrlFor() below, which builds exactly this shape.
            val parts = url.removePrefix(BASE).trim('/').split("/")
            val chapterIdx = parts.indexOf("chapter")
            if (chapterIdx == -1 || parts.size < chapterIdx + 3) {
                throw IllegalArgumentException("Unrecognized chapter URL shape: $url")
            }
            val slug = parts[chapterIdx + 1]
            val chapterId = parts[chapterIdx + 2]

            val json = JSONObject(
                fetchText("$API/novels/$slug/chapters/$chapterId", CHAPTER_API_HEADERS)
            )
            // Response shape has shown up both flat and nested in testing —
            // fall back to the object itself at each level rather than
            // assuming one specific wrapper is always present.
            val item = json.optJSONObject("item") ?: json
            val info = item.optJSONObject("chapterInfo") ?: item

            val title = info.optString("chapter_name").ifBlank { "Chapter" }

            val isPremium = info.optBoolean("premium_content", false) ||
                             info.optBoolean("platinum_content", false)
            if (isPremium) {
                Log.d(TAG, "Chapter is premium/platinum — skipping content fetch")
                return Pair(title, "This chapter requires purchase on NovelArrow.")
            }

            val content = htmlToPlainText(info.optString("chapter_content"))
            if (content.isBlank()) {
                Log.w(TAG, "chapter_content was empty in API response — url=$url")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Log.d(TAG, "Chapter content: ${content.length} chars")
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    private fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        val paragraphs = Jsoup.parse(html).select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        return paragraphs.joinToString("\n\n")
    }

    private fun coverUrlFor(slug: String) = "$IMG/novel/$slug.jpg"
    private fun chapterUrlFor(slug: String, chapterId: String) = "$BASE/chapter/$slug/$chapterId"

    private fun ratingOutOfTen(avgPoint: JSONObject?): String {
        val raw = avgPoint?.optString("\$numberDecimal")?.toDoubleOrNull() ?: return ""
        return "%.1f".format(raw * 2)
    }

    private fun statusFromCode(code: Int): String = if (code == 0) "Ongoing" else "Completed"

    // NOT authoritative — real chapter URLs need the full title-suffixed
    // chapter_id (see fetchAllChapters), which this signature doesn't have
    // access to. This is only ever hit by NovelRepository as a last-resort
    // fallback when a chapter number is missing from the cached URL map —
    // which fetchAllChapters populating the COMPLETE list on every detail
    // fetch should make essentially unreachable in practice. Kept as a
    // best-effort guess (matches chapters whose real id has no title
    // suffix) rather than throwing, so a stale/partial cache degrades
    // gracefully instead of crashing.
    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/chapter/$slug/chapter-$chapterNum"

    // ── Novel card parser (homepage/genre HTML listings) ──────────────────────
    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val result = mutableListOf<NovelEntity>()
        val links = doc.select("a[href^=/novel/]").toList()
        Log.d(TAG, "parseNovelCards: found ${links.size} novel links")

        links.forEach { a ->
            val href = a.attr("href")
            val slug = Regex("^/novel/([^/?#]+)").find(href)?.groupValues?.get(1) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach

            // FIX: /novels/hot (and possibly other listing pages) render the
            // title inside an <h2> styled with "truncate", not
            // "line-clamp-2" — the old selector matched zero elements there
            // (confirmed: 60 links found, 0 kept). Every one of the site's
            // card anchors carries the full title as a semantic `title`
            // attribute regardless of which Tailwind classes wrap it, so
            // that's tried first; the old class-based lookup stays as a
            // fallback rather than being removed, in case some page variant
            // doesn't set the attribute.
            val title = a.attr("title").trim()
                .ifBlank { a.selectFirst("[class*=line-clamp-2]")?.text()?.trim().orEmpty() }
                .ifBlank { return@forEach }

            val cover = a.selectFirst("img")?.attr("abs:src")?.ifBlank { null }
                ?: coverUrlFor(slug)

            val starCount = a.select("[class*=text-site-rating]").count { it.text().contains("★") }
            val rating = if (starCount > 0) (starCount * 2).toString() else ""

            val statusTitle = a.selectFirst("svg title")?.text()?.trim().orEmpty()

            result.add(NovelEntity(
                slug = slug, title = title, coverUrl = cover,
                synopsis = "", status = statusTitle, rating = rating,
                genres = "", chapterCount = 0, latestChapter = ""
            ))
        }

        Log.d(TAG, "parseNovelCards: returning ${result.size} novels")
        return result.distinctBy { it.slug }.take(60)
    }
}
