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
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Scraper for novelping.com (the site novelarrow.com rebranded into).
 *
 * WHAT WAS CONFIRMED (from the diagnostic report, real responses):
 *   Detail API   : GET /api-web/novels/<slug> → 200 JSON, same
 *                  {"item":{"novelInfo":{...}}} shape NovelArrowSource parses
 *                  (novel_name, novel_author, totalChapter, novel_status ...)
 *   Detail HTML  : og:title ("<name> | NovelPing"), og:image, og:description
 *                  (truncated), og:novel:genre / author / status /
 *                  lastest_chapter_name — usable as an API-down fallback
 *   Chapter page : /book/<slug>/chapter-<N> is server-rendered; body lives in
 *                  <div id="chr-content" class="chr-c">
 *   Search HTML  : /search?keyword=<q> → real results (50 /book/ links)
 *   Listings     : /sort/updates, /sort/hot, /sort/popular, /sort/complete,
 *                  /novelping-genres/<genre>
 *
 * NOT CONFIRMED (marked "UNVERIFIED" where used; every one has a fallback):
 *   - /api-web/novels?...keyword=  search endpoint on the new domain
 *   - /api-web/novels/<slug>/chapters  chapter-list endpoint on the new domain
 *   - /api-web/novels/<slug>/chapters/<id>  chapter-content endpoint
 *   - listing-page card markup — parsed structurally (a href="/book/<slug>"),
 *     not by CSS class, so it survives markup changes
 *   - genre pagination (?page=N)
 *
 * NOTE: novelping.com's robots.txt disallows automated access to everything
 * except the homepage and static assets. This source keeps traffic light
 * (single requests, no parallel fan-out, listing calls only for the top-priority
 * source) — but it is a personal-use decision, and worth knowing.
 *
 * Premium/paid chapters are detected and skipped with a clear message;
 * nothing here attempts to bypass a paywall.
 */
class NovelPingSource : NovelSource {

    override val id = "novelping"
    override val displayName = "NovelPing"
    override val baseUrl get() = BASE

    private val BASE = "https://novelping.com"
    private val API  = "$BASE/api-web"
    private val IMG  = "https://images.novelping.com"
    private val TAG  = "NCrawler_NovelPing"

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
        val body = resp.use { it.body?.string().orEmpty() }
        if (!resp.isSuccessful) {
            throw java.io.IOException("HTTP ${resp.code} for $url")
        }
        body
    }

    private suspend fun fetchDoc(url: String): Document = Jsoup.parse(fetchText(url), url)

    private val JSON_HEADERS = mapOf(
        "Accept" to "application/json",
        "x-client-platform" to "web-desktop",
        "x-device-type" to "desktop",
        "x-site-host" to "novelping.com",
        "x-version-app" to "web-desktop"
    )

    // ── Listings (HTML) ───────────────────────────────────────────────────────
    override suspend fun fetchHomepage(): List<NovelEntity> = listing("$BASE/sort/updates", "fetchHomepage")

    override suspend fun fetchPopular(): List<NovelEntity> = listing("$BASE/sort/popular", "fetchPopular")

    private suspend fun listing(url: String, label: String): List<NovelEntity> {
        Log.d(TAG, "$label()")
        return try {
            parseNovelCards(fetchDoc(url))
        } catch (e: Exception) {
            Log.e(TAG, "$label failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> {
        Log.d(TAG, "fetchGenre(genre=$genre, page=$page)")
        // Site slugs are lowercase with dashes ("slice-of-life"); "&" and "+"
        // ("anime-&-comics", "lgbt+") must be percent-encoded in a path.
        val slug = genre.trim().lowercase().replace(' ', '-')
            .replace("&", "%26").replace("+", "%2B")
        // UNVERIFIED: page 2+ is assumed to be ?page=N.
        val url = if (page <= 1) "$BASE/novelping-genres/$slug"
                  else "$BASE/novelping-genres/$slug?page=$page"
        return try {
            parseNovelCards(fetchDoc(url))
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenre('$genre', page=$page) failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchExtraSections(): List<HomeSection> {
        Log.d(TAG, "fetchExtraSections()")
        val specs = listOf(
            "Hot Novels"       to "$BASE/sort/hot",
            "Completed Novels" to "$BASE/sort/complete"
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

    // Taken verbatim from the site's own genre list (homepage, this session).
    override fun knownGenres(): List<String> = listOf(
        "Action", "Adult", "Adventure", "Anime & comics", "Comedy", "Drama",
        "Eastern", "Ecchi", "Fan-fic", "Fan-fiction", "Fantasy", "Game",
        "Gender bender", "Harem", "Historical", "Horror", "Isekai", "Josei",
        "Lgbt+", "Litrpg", "Magic", "Magical realism", "Martial arts",
        "Mature", "Mecha", "Military", "Modern life", "Mystery",
        "Psychological", "Realistic", "Reincarnation", "Romance",
        "School life", "Sci-fi", "Seinen", "Shoujo", "Shoujo ai", "Shounen",
        "Shounen ai", "Slice of life", "Smut", "Sports", "Supernatural",
        "System", "Thriller", "Tragedy", "Urban", "Urban fantasy",
        "Video games", "War", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", "Yuri"
    )

    // ── Search: JSON API first (UNVERIFIED on this domain), HTML fallback (verified) ──
    override suspend fun search(query: String): List<NovelEntity> {
        val encoded = URLEncoder.encode(query, "UTF-8")

        val fromApi: List<NovelEntity> = try {
            val url = "$API/novels?limit=30&page=1&status=all&sort=SEARCH_KEYWORD&genre=ALL&keyword=$encoded"
            val json  = JSONObject(fetchText(url, JSON_HEADERS))
            val items = json.optJSONArray("items") ?: JSONArray()
            Log.d(TAG, "search('$query') API: ${items.length()} items")
            (0 until items.length()).mapNotNull { i -> novelFromApiItem(items.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.w(TAG, "search('$query') API failed (${e.message}) — falling back to HTML")
            emptyList()
        }
        if (fromApi.isNotEmpty()) return fromApi

        return try {
            val novels = parseNovelCards(fetchDoc("$BASE/search?keyword=$encoded"))
            Log.d(TAG, "search('$query') HTML: ${novels.size} novels")
            novels
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
        return NovelEntity(
            slug = slug, title = title, coverUrl = coverUrlFor(slug),
            synopsis = "", status = statusFromCode(item.optInt("novel_status", 0)),
            rating = ratingOutOfTen(item.optJSONObject("avgPoint")),
            genres = genres, chapterCount = item.optInt("totalChapter", 0),
            latestChapter = item.optJSONObject("recentChapter")?.optString("chapter_name") ?: ""
        )
    }

    // ── Info-only (perf): same metadata as fetchDetail, no chapter list ──────
    override suspend fun fetchInfo(slug: String): NovelEntity? = try {
        fetchNovelMetadata(slug)
    } catch (e: Exception) {
        Log.e(TAG, "fetchInfo($slug) failed: ${e.message}", e)
        null
    }

    // ── Detail: JSON API first (confirmed alive), HTML og:* fallback ─────────
    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val base = fetchNovelMetadata(slug) ?: return null

            var chapters = try {
                fetchAllChapters(slug)
            } catch (e: Exception) {
                Log.w(TAG, "chapter list API failed (${e.message}) — using count-based fallback")
                emptyList()
            }
            if (chapters.isEmpty()) chapters = synthesizedChapters(slug, base.chapterCount)
            Log.d(TAG, "chapters: ${chapters.size} (expected ${base.chapterCount})")

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel = base.copy(
                chapterCount = chapters.size.takeIf { it > 0 } ?: base.chapterCount,
                latestChapter = base.latestChapter.ifBlank { chapters.firstOrNull()?.title ?: "" },
                chapterUrls  = urlMap
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchNovelMetadata(slug: String): NovelEntity? {
        // 1) JSON API — confirmed alive in the diagnostic report.
        try {
            val json = JSONObject(fetchText("$API/novels/$slug", JSON_HEADERS))
            val info = json.optJSONObject("item")?.optJSONObject("novelInfo")
            val title = info?.optString("novel_name").orEmpty()
            if (info != null && title.isNotBlank()) {
                val genres = info.optJSONArray("novel_genres")?.let { arr ->
                    (0 until arr.length()).joinToString(", ") { arr.getString(it) }
                } ?: ""
                Log.d(TAG, "Detail API: title=$title totalChapter=${info.optInt("totalChapter", 0)}")
                return NovelEntity(
                    slug = slug, title = title, coverUrl = coverUrlFor(slug),
                    synopsis = htmlToPlainText(info.optString("novel_desc")),
                    status = statusFromCode(info.optInt("novel_status", 0)),
                    rating = ratingOutOfTen(info.optJSONObject("avgPoint")),
                    genres = genres,
                    chapterCount = info.optInt("totalChapter", 0),
                    latestChapter = info.optJSONObject("recentChapter")?.optString("chapter_name") ?: "",
                    author = info.optString("novel_author").trim()
                )
            }
            Log.w(TAG, "Detail API returned no novelInfo — falling back to HTML")
        } catch (e: Exception) {
            Log.w(TAG, "Detail API failed (${e.message}) — falling back to HTML")
        }

        // 2) HTML og:* tags — confirmed present in the diagnostic report.
        val doc = fetchDoc("$BASE/book/$slug")
        val title = doc.select("meta[property=og:novel:novel_name]").attr("content").trim()
            .ifBlank { doc.select("meta[property=og:title]").attr("content").substringBefore(" | ").trim() }
            .ifBlank { return null }
        val latestName = doc.select("meta[property=og:novel:lastest_chapter_name]").attr("content").trim()
        val latestNum = Regex("(\\d+)").find(latestName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return NovelEntity(
            slug = slug, title = title,
            coverUrl = doc.select("meta[property=og:image]").attr("content").ifBlank { coverUrlFor(slug) },
            // og:description is truncated by the site (ends with "...") — best available in this path.
            synopsis = doc.select("meta[property=og:description]").attr("content").trim(),
            status = doc.select("meta[property=og:novel:status]").attr("content").trim(),
            rating = "",
            genres = doc.select("meta[property=og:novel:genre]").attr("content")
                .split(",").joinToString(", ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } },
            chapterCount = latestNum,
            latestChapter = latestName,
            author = doc.select("meta[property=og:novel:author]").attr("content").trim()
        )
    }

    // UNVERIFIED on this domain: /api-web/novels/<slug>/chapters (proven on the
    // old novelarrow.com backend). Loops over pagination — `limit` is not
    // reliably honored by this API family. Capped at 20 pages as a sanity bound.
    private suspend fun fetchAllChapters(slug: String): List<ChapterLink> {
        val all = mutableListOf<ChapterLink>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages && page <= 20) {
            val json = JSONObject(fetchText("$API/novels/$slug/chapters?page=$page&limit=500", JSON_HEADERS))
            val items = json.optJSONArray("items") ?: JSONArray()
            totalPages = json.optJSONObject("pagination")?.optInt("totalPages", 1) ?: 1
            for (i in 0 until items.length()) {
                val ch = items.getJSONObject(i)
                val chapterId = ch.optString("chapter_id")
                if (chapterId.isBlank()) continue
                val name = ch.optString("chapter_name").ifBlank { "Chapter" }
                val num = Regex("chapter-(\\d+)").find(chapterId)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: (all.size + 1)
                all.add(ChapterLink(num = num, title = name, url = chapterUrlFor(slug, chapterId)))
            }
            page++
        }
        return all.distinctBy { it.num }.sortedByDescending { it.num }
    }

    // Fallback only: bare chapter-N URLs (confirmed to exist on the site's own
    // detail-page links) for 1..count, when the chapter-list API is unavailable.
    private fun synthesizedChapters(slug: String, count: Int): List<ChapterLink> =
        (1..count.coerceAtMost(20000)).map { n ->
            ChapterLink(num = n, title = "Chapter $n", url = buildChapterUrl(slug, n))
        }.sortedByDescending { it.num }

    // ── Chapter content ──────────────────────────────────────────────────────
    // Chapter URLs are always $BASE/book/<slug>/<chapterId>.
    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val parts = url.removePrefix(BASE).trim('/').split("/")
            val bookIdx = parts.indexOf("book")
            if (bookIdx == -1 || parts.size < bookIdx + 3) {
                throw IllegalArgumentException("Unrecognized chapter URL shape: $url")
            }
            val slug = parts[bookIdx + 1]
            val chapterId = parts[bookIdx + 2]

            // 1) JSON API (UNVERIFIED on this domain) — clean HTML-in-JSON.
            var apiTitle = ""
            try {
                val json = JSONObject(fetchText("$API/novels/$slug/chapters/$chapterId", JSON_HEADERS))
                val item = json.optJSONObject("item") ?: json
                val info = item.optJSONObject("chapterInfo") ?: item
                apiTitle = info.optString("chapter_name")
                val isPremium = info.optBoolean("premium_content", false) ||
                                info.optBoolean("platinum_content", false)
                if (isPremium) {
                    Log.d(TAG, "Chapter is premium/platinum — not fetching")
                    return Pair(apiTitle.ifBlank { "Chapter" }, "This chapter requires purchase on NovelPing.")
                }
                val text = htmlToPlainText(info.optString("chapter_content"))
                if (text.isNotBlank()) {
                    Log.d(TAG, "Chapter via API: ${text.length} chars")
                    return Pair(apiTitle.ifBlank { "Chapter" }, text)
                }
                Log.w(TAG, "API chapter_content empty — falling back to HTML")
            } catch (e: Exception) {
                Log.w(TAG, "Chapter API failed (${e.message}) — falling back to HTML")
            }

            // 2) HTML page — <div id="chr-content" class="chr-c"> (confirmed).
            val doc = fetchDoc(url)
            val title = apiTitle.ifBlank {
                doc.selectFirst(".chr-title, h2, h1")?.text()?.trim().orEmpty()
            }.ifBlank { "Chapter" }

            val container = doc.selectFirst("#chr-content") ?: doc.selectFirst("div.chr-c")
            val paragraphs = container?.select("p")?.map { it.text().trim() }
                ?.filter { it.isNotBlank() } ?: emptyList()
            Log.d(TAG, "Chapter via HTML: ${paragraphs.size} paragraphs")

            if (paragraphs.isEmpty()) {
                if (doc.selectFirst(".chapter-pin-error") != null) {
                    return Pair(title, "This chapter is locked on NovelPing.")
                }
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Pair(title, paragraphs.joinToString("\n\n"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    private fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        return Jsoup.parse(html).select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun coverUrlFor(slug: String) = "$IMG/novel/$slug.jpg"
    private fun chapterUrlFor(slug: String, chapterId: String) = "$BASE/book/$slug/$chapterId"

    private fun ratingOutOfTen(avgPoint: JSONObject?): String {
        val raw = avgPoint?.optString("\$numberDecimal")?.toDoubleOrNull() ?: return ""
        return "%.1f".format(raw * 2)
    }

    private fun statusFromCode(code: Int): String = if (code == 0) "Ongoing" else "Completed"

    // Bare chapter-N URLs exist on the site's own detail-page links.
    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/book/$slug/chapter-$chapterNum"

    // ── Listing parser ───────────────────────────────────────────────────────
    // Structural, not class-based: a novel card is any link whose href is
    // exactly /book/<slug> (chapter links have a further path segment and are
    // ignored). A card usually has two links to the same slug (cover + title),
    // so results are merged per slug, keeping the best title/cover found.
    private val novelHref = Regex("^(?:https?://novelping\\.com)?/book/([^/?#]+)/?$")

    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val bySlug = LinkedHashMap<String, NovelEntity>()

        val links = doc.select("a[href*=/book/]").toList()
        Log.d(TAG, "parseNovelCards: ${links.size} /book/ links")

        links.forEach { a ->
            val href = a.attr("href").trim()
            val slug = novelHref.find(href)?.groupValues?.get(1) ?: return@forEach

            val title = a.attr("title").trim()
                .ifBlank { a.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
                .ifBlank { a.text().trim() }

            val cover = findCover(a)
            val card = cardContainer(a)
            val genres = card?.select("a[href*=novelping-genres]")
                ?.eachText()?.joinToString(", ") ?: ""

            val existing = bySlug[slug]
            if (existing == null) {
                if (title.isBlank()) return@forEach
                bySlug[slug] = NovelEntity(
                    slug = slug, title = title,
                    coverUrl = cover.ifBlank { coverUrlFor(slug) },
                    synopsis = "", status = "", rating = "",
                    genres = genres, chapterCount = 0, latestChapter = ""
                )
            } else {
                bySlug[slug] = existing.copy(
                    title = if (existing.title.length >= title.length) existing.title else title,
                    coverUrl = if (existing.coverUrl.endsWith("/$slug.jpg") && cover.isNotBlank()) cover else existing.coverUrl,
                    genres = existing.genres.ifBlank { genres }
                )
            }
        }

        val result = bySlug.values.toList().take(60)
        Log.d(TAG, "parseNovelCards: returning ${result.size} novels")
        return result
    }

    private val coverAttrs = listOf("data-src", "data-original", "data-lazy-src", "src")

    private fun findCover(a: Element): String {
        var el: Element? = a
        var depth = 0
        while (el != null && depth < 3) {
            for (img in el.select("img")) {
                for (attr in coverAttrs) {
                    val u = img.attr("abs:$attr")
                    if (u.isNotBlank() && !u.startsWith("data:")) return u
                }
            }
            el = el.parent()
            depth++
        }
        return ""
    }

    /** Nearest ancestor (max 4 up) that wraps exactly one distinct novel — the card. */
    private fun cardContainer(a: Element): Element? {
        var el: Element? = a.parent()
        var depth = 0
        var best: Element? = null
        while (el != null && depth < 4) {
            val distinct = el.select("a[href*=/book/]")
                .mapNotNull { novelHref.find(it.attr("href").trim())?.groupValues?.get(1) }
                .toSet()
            if (distinct.size == 1) best = el else if (distinct.size > 1) break
            el = el.parent()
            depth++
        }
        return best
    }
}
