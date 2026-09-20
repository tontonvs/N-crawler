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

    // ── Chapter content ─────────────────────────────────────────────────────────
    // CHANGE: different chapters nest their SSR payload at DIFFERENT
    // backslash-escaping depths — confirmed live: the chapter used during
    // initial investigation had its data at depth 0 (plain, unescaped JSON
    // in the page), while a different novel's chapter-1 had the exact same
    // keys nested one level deeper (single-backslash-escaped, e.g.
    // \"chapter_content\" instead of "chapter_content"). Extraction no
    // longer assumes a fixed depth — extractJsonStringValue/BooleanValue
    // below detect it per-call by counting the backslashes actually
    // present around each key, so this works regardless of which depth a
    // given chapter's payload happens to use.
    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val html = fetchText(url)

            val title = extractJsonStringValue(html, "chapter_name")
                ?.let { unescapeNextJs(it) }?.ifBlank { null } ?: "Chapter"

            val isPremium = extractJsonBooleanValue(html, "premium_content") == true ||
                             extractJsonBooleanValue(html, "platinum_content") == true
            if (isPremium) {
                Log.d(TAG, "Chapter is premium/platinum — skipping content fetch")
                return Pair(title, "This chapter requires purchase on NovelArrow.")
            }

            // chapter_content's value is one of THREE confirmed shapes:
            //  A. the actual escaped HTML inline, directly as this value
            //     — used for shorter content strings
            //  B. a reference like "$25" pointing at a "25:T<hex>,<html>"
            //     chunk elsewhere on the page, with the full content
            //     present right after the comma, in that SAME push() call
            //  C. the same "$25" reference, but the "25:T<hex>," marker
            //     declares an upcoming byte length with ZERO bytes
            //     actually inline — the server flushed the real payload
            //     as a SEPARATE, immediately-following push() call that
            //     has no id-prefix of its own, as a raw continuation of
            //     that same logical value. Confirmed live: this happens
            //     when the server's stream flushes mid-value.
            val rawValue = extractJsonStringValue(html, "chapter_content")
            val content = rawValue?.let { raw ->
                if (Regex("^\\$[A-Za-z0-9]+$").matches(raw)) {
                    htmlToPlainText(unescapeNextJs(extractStreamedChunk(html, raw.substring(1)) ?: ""))
                } else {
                    htmlToPlainText(unescapeNextJs(raw))
                }
            }

            if (content.isNullOrBlank()) {
                Log.w(TAG, "Could not extract chapter_content — url=$url")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Log.d(TAG, "Chapter content: ${content.length} chars")
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    // Finds a JSON key ANYWHERE in the raw page text and extracts its
    // string value, at whatever backslash-escaping depth that key
    // actually happens to be nested at on this particular page — detected
    // by counting the backslashes immediately surrounding the key itself,
    // rather than assuming a fixed depth (see fetchChapterByUrl comment
    // above for why a fixed assumption broke on a different chapter).
    private fun extractJsonStringValue(html: String, key: String): String? {
        val keyIdx = html.indexOf(key)
        if (keyIdx == -1) return null

        var i = keyIdx - 1
        var depth = 0
        while (i >= 0 && html[i] == '\\') { depth++; i-- }
        if (i < 0 || html[i] != '"') return null

        val delim = "\\".repeat(depth) + "\""
        val afterKey = keyIdx + key.length
        val closeKeyIdx = html.indexOf(delim, afterKey)
        if (closeKeyIdx == -1) return null
        val colonIdx = html.indexOf(':', closeKeyIdx + delim.length)
        if (colonIdx == -1) return null
        val openValueIdx = html.indexOf(delim, colonIdx + 1)
        if (openValueIdx == -1) return null
        val valueStart = openValueIdx + delim.length

        // Find the closing delimiter at the SAME depth — a candidate match
        // at a deeper depth (more backslashes) means it's an escaped quote
        // INSIDE the value's own content, not our real closing delimiter,
        // so skip past it and keep looking.
        var searchFrom = valueStart
        while (true) {
            val candidate = html.indexOf(delim, searchFrom)
            if (candidate == -1) return null
            var j = candidate - 1
            var actualDepth = 0
            while (j >= 0 && html[j] == '\\') { actualDepth++; j-- }
            if (actualDepth == depth) return html.substring(valueStart, candidate - depth)
            searchFrom = candidate + delim.length
        }
    }

    // Same depth-detection approach as extractJsonStringValue, for a plain
    // (unquoted) true/false literal instead of a string value.
    private fun extractJsonBooleanValue(html: String, key: String): Boolean? {
        val keyIdx = html.indexOf(key)
        if (keyIdx == -1) return null

        var i = keyIdx - 1
        var depth = 0
        while (i >= 0 && html[i] == '\\') { depth++; i-- }
        if (i < 0 || html[i] != '"') return null

        val delim = "\\".repeat(depth) + "\""
        val afterKey = keyIdx + key.length
        val closeKeyIdx = html.indexOf(delim, afterKey)
        if (closeKeyIdx == -1) return null
        val colonIdx = html.indexOf(':', closeKeyIdx + delim.length)
        if (colonIdx == -1) return null

        val tail = html.substring(colonIdx + 1, minOf(colonIdx + 10, html.length))
        return when {
            tail.startsWith("true")  -> true
            tail.startsWith("false") -> false
            else -> null
        }
    }

    // Handles shapes B and C from the comment above. A "<refId>:T<hexlen>,"
    // marker declares exactly how many (decoded) bytes belong to this
    // chunk's value. Those bytes can be inline in the SAME push() call
    // (shape B), or flushed as one or more SEPARATE, unlabelled push()
    // calls immediately after it (shape C). The previous version assumed
    // exactly one follow-up call was always enough — not guaranteed for a
    // long chapter — so this now keeps consuming follow-up push() calls
    // until the declared length is actually satisfied, logging targetLen
    // vs. what's been assembled at each step so a future failure shows
    // exactly where it falls short instead of just "could not extract."
    private fun extractStreamedChunk(html: String, refId: String): String? {
        // Lookbehind guards against "25:T" matching as a substring of a
        // longer id elsewhere on the page (e.g. "125:T").
        val markerPattern = Regex(
            "(?<![0-9])" + Regex.escape("$refId:T") + "([0-9a-f]+),(.*?)\"\\]\\)\\s*</script>",
            RegexOption.DOT_MATCHES_ALL
        )
        val markerMatch = markerPattern.find(html) ?: run {
            Log.w(TAG, "extractStreamedChunk($refId): marker not found")
            return null
        }
        val targetLen = markerMatch.groupValues[1].toInt(16)
        val sb = StringBuilder(markerMatch.groupValues[2])
        Log.d(TAG, "extractStreamedChunk($refId): targetLen=$targetLen bytes, inline=${sb.length} chars")

        val nextPushPattern = Regex(
            "self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)\\s*</script>",
            RegexOption.DOT_MATCHES_ALL
        )
        var searchFrom = markerMatch.range.last + 1
        var callsConsumed = 0
        while (unescapeNextJs(sb.toString()).length < targetLen && callsConsumed < 10) {
            val next = nextPushPattern.find(html, searchFrom) ?: break
            sb.append(next.groupValues[1])
            searchFrom = next.range.last + 1
            callsConsumed++
        }

        val decodedLen = unescapeNextJs(sb.toString()).length
        Log.d(TAG, "extractStreamedChunk($refId): assembled ${sb.length} raw chars " +
                "across ${callsConsumed + 1} push call(s), decoded=$decodedLen, target=$targetLen")
        return sb.toString().ifBlank { null }
    }

    private fun unescapeNextJs(s: String): String =
        s.replace("\\u003c", "<")
         .replace("\\u003e", ">")
         .replace("\\u0026", "&")
         .replace("\\\"", "\"")
         .replace("\\\\", "\\")

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
