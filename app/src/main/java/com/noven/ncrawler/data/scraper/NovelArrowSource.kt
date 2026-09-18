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
 * Unlike FreeWebNovel/NovelLive (plain server-rendered HTML), this is a
 * Next.js site with a genuinely clean backing JSON API for detail + search —
 * both live-verified via direct curl, no Cloudflare wall on either. Only the
 * homepage/genre LISTINGS and chapter CONTENT still require HTML scraping,
 * since no equivalent JSON endpoint was found for those (confirmed via
 * DevTools: the homepage's only real API call is an analytics ping).
 *
 * Confirmed live:
 *   Detail  : GET /api-web/novels/<slug>                         → clean JSON
 *   Search  : GET /api-web/novels?...&sort=SEARCH_KEYWORD&keyword=<q>  → clean JSON
 *   Listing : HTML — <a href="/novel/<slug>"> cards with title in a
 *             "line-clamp-2 ... font-bold" span, cover <img> inside a
 *             "novel-cover-frame" wrapper, rating as counted ★ glyphs
 *             (class contains "text-site-rating"), status as an SVG
 *             <title>Completed</title> / <title>Ongoing</title>
 *   Chapter : URL /chapter/<slug>/chapter-<N> — content is NOT plain HTML,
 *             it's embedded in a React Server Component streaming payload
 *             (self.__next_f.push(...)) as an escaped HTML string referenced
 *             by a numeric id (e.g. "chapter_content":"$25" → a "25:T<hex>,"
 *             chunk elsewhere in the page holding the actual <p> HTML)
 *
 * Rating scale: the API's avgPoint is 0–5, but the rest of this app treats
 * NovelEntity.rating as an "out of 10" string (DetailScreen's star widget
 * divides by 2). Stored here as avgPoint × 2 so it renders correctly
 * everywhere without needing to touch DetailScreen.
 *
 * Premium chapters: some novels here have paid/platinum chapters
 * (coin_price > 0). This scraper does not attempt to fetch those — it
 * detects the premium/platinum flag and returns a clear "requires purchase"
 * message instead of trying to bypass the paywall.
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
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
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

    // /novels/hot is referenced from the site's own 404 page ("Hot Novels"
    // link) but its card markup hasn't been live-verified the way the
    // homepage's has — falls back to the homepage listing if it 404s or the
    // page shape turns out different than expected.
    override suspend fun fetchPopular(): List<NovelEntity> {
        Log.d(TAG, "fetchPopular()")
        return try {
            parseNovelCards(fetchDoc("$BASE/novels/hot"))
        } catch (e: Exception) {
            Log.w(TAG, "fetchPopular: /novels/hot failed (${e.message}), falling back to homepage")
            parseNovelCards(fetchDoc(BASE))
        }
    }

    // Genre route_name values are lowercase/hyphenated (e.g. "sci-fi",
    // "anime-&-comics") per the confirmed genre list — caller passes the
    // route_name, not the display name. Page markup assumed same template
    // as the homepage; not independently live-verified.
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
    // GET /api-web/novels?limit=&page=&status=all&sort=SEARCH_KEYWORD&genre=ALL&keyword=
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
    // GET /api-web/novels/<slug>
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
            val rating         = ratingOutOfTen(info.optJSONObject("avgPoint"))
            val totalChapters  = info.optInt("totalChapter", 0)
            val statusCode     = info.optInt("novel_status", 0)
            val recentChapterName = info.optJSONObject("recentChapter")?.optString("chapter_name") ?: ""
            val firstChapterName  = info.optJSONObject("firstChapter")?.optString("chapter_name") ?: ""

            Log.d(TAG, "Detail: title=$title totalChapters=$totalChapters rating=$rating")

            // Chapter list synthesis — totalChapter here is authoritative
            // (straight from the site's DB, not a URL-regex guess), so this
            // range is exact. Real titles are only known for the first and
            // most recent chapter from this endpoint; everything in between
            // gets a plain "Chapter N" placeholder until actually opened.
            val chapters = (1..totalChapters).map { n ->
                val realTitle = when (n) {
                    1              -> firstChapterName.ifBlank { null }
                    totalChapters  -> recentChapterName.ifBlank { null }
                    else           -> null
                }
                ChapterLink(num = n, title = realTitle ?: "Chapter $n", url = buildChapterUrl(slug, n))
            }.sortedByDescending { it.num }

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel = NovelEntity(
                slug = slug, title = title, coverUrl = coverUrlFor(slug),
                synopsis = synopsis, status = statusFromCode(statusCode), rating = rating,
                genres = genres, chapterCount = totalChapters,
                latestChapter = recentChapterName, chapterUrls = urlMap
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    // ── Chapter content ─────────────────────────────────────────────────────────
    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val html = fetchText(url)

            val title = Regex("\"chapter_name\":\"([^\"]*)\"").find(html)
                ?.groupValues?.get(1)?.ifBlank { null } ?: "Chapter"

            // Premium/platinum check — scoped to the chapterInfo block only
            // (anchored on a substring confirmed unique to it) so this can't
            // false-positive on unrelated flags elsewhere on the page (e.g.
            // chapterAds.internalApp.enabled).
            val infoAnchor = html.indexOf("\"chapterInfo\":{\"chapter_status\"")
            if (infoAnchor != -1) {
                val window = html.substring(infoAnchor, minOf(infoAnchor + 400, html.length))
                val isPremium = window.contains("\"premium_content\":true") ||
                                window.contains("\"platinum_content\":true")
                if (isPremium) {
                    Log.d(TAG, "Chapter is premium/platinum — skipping content fetch")
                    return Pair(title, "This chapter requires purchase on NovelArrow.")
                }
            }

            // Find the RSC reference id for chapter_content (e.g. "$25" → "25"),
            // then pull that numbered chunk's raw string: "25:T<hex>,<html...>"
            val refId = Regex("\"chapter_content\":\"\\$(\\w+)\"").find(html)?.groupValues?.get(1)

            val content = refId?.let { id ->
                val chunkPattern = Regex(
                    Regex.escape("$id:T") + "[0-9a-f]+,(.*?)\"\\]\\)\\s*</script>",
                    RegexOption.DOT_MATCHES_ALL
                )
                chunkPattern.find(html)?.groupValues?.get(1)?.let { raw ->
                    htmlToPlainText(unescapeNextJs(raw))
                }
            }

            if (content.isNullOrBlank()) {
                Log.w(TAG, "Could not extract chapter_content — refId=$refId")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Log.d(TAG, "Chapter content: ${content.length} chars")
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    // Next.js escapes a chapter's HTML for safe embedding inside a JS string
    // literal — unescape just the handful of sequences confirmed present
    // (angle brackets, ampersand, quotes, backslash), not a full JS decoder.
    private fun unescapeNextJs(s: String): String =
        s.replace("\\u003c", "<")
         .replace("\\u003e", ">")
         .replace("\\u0026", "&")
         .replace("\\\"", "\"")
         .replace("\\\\", "\\")

    // Shared by synopsis (novel_desc) and chapter content — both arrive as
    // an HTML string ("<p>...</p><p>...</p>") rather than plain text.
    private fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        val paragraphs = Jsoup.parse(html).select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        return paragraphs.joinToString("\n\n")
    }

    private fun coverUrlFor(slug: String) = "$IMG/novel/$slug.jpg"

    // avgPoint from the API is 0–5; the rest of the app treats
    // NovelEntity.rating as out-of-10 (DetailScreen halves it for its
    // 5-star widget) — doubled here so it renders correctly everywhere
    // without needing to change that shared logic.
    private fun ratingOutOfTen(avgPoint: JSONObject?): String {
        val raw = avgPoint?.optString("\$numberDecimal")?.toDoubleOrNull() ?: return ""
        return "%.1f".format(raw * 2)
    }

    // novel_status mapping is INFERRED, not documented — 0 has only been
    // observed on novels still actively receiving new chapters. Adjust here
    // if a completed novel is later found with a different code.
    private fun statusFromCode(code: Int): String = if (code == 0) "Ongoing" else "Completed"

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

            val title = a.selectFirst("[class*=line-clamp-2]")?.text()?.trim()
                .orEmpty().ifBlank { return@forEach }

            val cover = a.selectFirst("img")?.attr("abs:src")?.ifBlank { null }
                ?: coverUrlFor(slug)

            // Rating: count filled ★ glyphs in elements whose class contains
            // "text-site-rating" (confirmed on both listing and detail pages).
            // Scaled ×2 for the same out-of-10 convention as ratingOutOfTen().
            val starCount = a.select("[class*=text-site-rating]").count { it.text().contains("★") }
            val rating = if (starCount > 0) (starCount * 2).toString() else ""

            // Status badge: SVG <title> text, confirmed "Completed"/"Ongoing"
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
