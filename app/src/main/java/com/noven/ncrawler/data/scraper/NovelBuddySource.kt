package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Scraper for novelbuddy.com — mirrors the same catalog as NovelFull/
 * ReadNovelFull/NovelHi on an independent domain, added as redundancy: if
 * NovelFull ever breaks or its domain churns, this covers the same books.
 *
 * Ground truth here comes from two places, NOT a live fetch of my own —
 * flagging that distinction clearly since NovelArrow's slow rewrite taught
 * exactly this lesson:
 *   1. lightnovel-crawler's maintained novelbuddy.py source (real, current,
 *      actively used by that project) — the API shape below is taken
 *      directly from it.
 *   2. Live Google-indexed snippets of novelbuddy.com/.io pages, which
 *      independently confirm the bare-slug detail URL and — importantly —
 *      that the rendered detail page only ever shows the newest 50
 *      chapters ("Showing 50 of 2,473 chapters"), which is exactly why the
 *      API is used for the full list instead of the page HTML.
 *
 * Confirmed structure:
 *   Detail URL   : novelbuddy.com/<slug>  (bare slug, no /novel/ or /book/
 *                  prefix) — also live on novelbuddy.io and novelbuddy.me,
 *                  same content, per lncrawl's own base_url list
 *   API          : https://api.novelbuddy.me — shared by all three domain
 *                  mirrors; this is the actual stable integration point,
 *                  not any one of the front-end domains
 *   Slug→id      : GET {API}/titles/by-slug/<slug>
 *                    → data.new_url, e.g. ".../12345-cultivation-online"
 *                    → title_id is the numeric prefix before the first "-"
 *                      of the URL's last path segment
 *   Detail       : GET {API}/titles/<id>          → data.title{name, cover,
 *                  authors[], genres[], summary (HTML), ...}
 *   Chapters     : GET {API}/titles/<id>/chapters  → data.chapters[] —
 *                  NEWEST FIRST, must be reversed for reading order
 *   Search       : GET {API}/titles/search?q=<query> → data.items[]{name,
 *                  url, status}
 *   Chapter body : the actual chapter page HTML, <article> tag — the API
 *                  doesn't carry chapter text, only metadata/nav
 *
 * NOT verified against a live fetch (my own tooling couldn't reach this
 * site this session) — best-effort only, flagged so a future me doesn't
 * mistake this for confirmed:
 *   fetchHomepage() / fetchPopular() — no listing endpoint appears in the
 *   lncrawl source (it's a download tool, not a browser, so it never
 *   needed one). Implemented as a generic card scrape of the homepage;
 *   if this returns nothing, that's the first thing to check with a real
 *   fetch.
 */
class NovelBuddySource : NovelSource {

    override val id = "novelbuddy"
    override val displayName = "NovelBuddy"
    override val baseUrl get() = BASE

    private val BASE = "https://novelbuddy.com"
    private val API  = "https://api.novelbuddy.me"
    private val TAG  = "NCrawler_NovelBuddy"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/html;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
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

    private suspend fun fetchJson(url: String): JSONObject = JSONObject(fetchText(url))

    private suspend fun fetchDoc(url: String): Document = Jsoup.parse(fetchText(url), url)

    // ── Info-only — perf fix ─────────────────────────────────────────────────
    // Same shape as the perf fix added for NovelArrow: metadata without the
    // chapter list. Here it's not just an optimization, it's a natural fit —
    // the API already splits title-info and chapters into two calls, so
    // skipping the chapters call is a genuinely separate step, not extra
    // work carved out of one atomic fetch.
    override suspend fun fetchInfo(slug: String): NovelEntity? = try {
        val titleId = resolveTitleId(slug)
        titleId?.let { fetchNovelMetadata(slug, it) }
    } catch (e: Exception) {
        Log.e(TAG, "fetchInfo($slug) failed: ${e.message}", e)
        null
    }

    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val titleId = resolveTitleId(slug) ?: return null
            val base = fetchNovelMetadata(slug, titleId) ?: return null

            val chaptersJson = fetchJson("$API/titles/$titleId/chapters")
            val items = (chaptersJson.optJSONObject("data")
                ?.optJSONArray("chapters")) ?: return Pair(base, emptyList())

            // API returns newest-first — reverse for reading order (num=1 first).
            // Walked with a manual counter rather than mapIndexedNotNull's idx:
            // idx there counts input position, so any filtered-out entry (blank
            // name/url) would leave a gap in the chapter numbers instead of
            // just being absent.
            val chapters = mutableListOf<ChapterLink>()
            for (i in items.length() - 1 downTo 0) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name").ifBlank { continue }
                val href = item.optString("url").ifBlank { continue }
                chapters.add(ChapterLink(num = chapters.size + 1, title = name, url = absoluteUrl(href)))
            }
            Log.d(TAG, "fetchDetail($slug): ${chapters.size} chapters")

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel = base.copy(chapterCount = chapters.size, chapterUrls = urlMap)
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    private suspend fun resolveTitleId(slug: String): String? {
        val lookup = fetchJson("$API/titles/by-slug/$slug")
        val newUrl = lookup.optJSONObject("data")?.optString("new_url") ?: return null
        val lastSegment = newUrl.trimEnd('/').substringAfterLast('/')
        return lastSegment.substringBefore('-').takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    private suspend fun fetchNovelMetadata(slug: String, titleId: String): NovelEntity? {
        val detail = fetchJson("$API/titles/$titleId")
        val t = detail.optJSONObject("data")?.optJSONObject("title") ?: return null

        val title = t.optString("name").ifBlank { return null }
        val cover = t.optString("cover")
        val genres = t.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
        }?.filter { it.isNotBlank() } ?: emptyList()
        val synopsis = htmlToPlainText(t.optString("summary"))
        val status = t.optString("status").ifBlank { "" }

        Log.d(TAG, "Detail: title=$title titleId=$titleId genres=${genres.size}")

        return NovelEntity(
            slug = slug, title = title, coverUrl = absoluteUrl(cover),
            synopsis = synopsis, status = status, rating = "",
            genres = genres.joinToString(", "), chapterCount = 0, latestChapter = ""
        )
    }

    // ── Search ────────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<NovelEntity> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val json = fetchJson("$API/titles/search?q=$encoded")
            val items = json.optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("name").ifBlank { return@mapNotNull null }
                val href = item.optString("url").ifBlank { return@mapNotNull null }
                val slug = slugFromPath(href) ?: return@mapNotNull null
                NovelEntity(
                    slug = slug, title = name, coverUrl = "", synopsis = "",
                    status = item.optString("status"), rating = "", genres = "",
                    chapterCount = 0, latestChapter = ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Homepage / Popular — NOT verified live, see class doc ───────────────
    override suspend fun fetchHomepage(): List<NovelEntity> = try {
        parseNovelCards(fetchDoc(BASE))
    } catch (e: Exception) {
        Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
        emptyList()
    }

    override suspend fun fetchPopular(): List<NovelEntity> = try {
        parseNovelCards(fetchDoc("$BASE/ranking"))
    } catch (e: Exception) {
        Log.w(TAG, "fetchPopular failed, falling back to homepage: ${e.message}")
        try { parseNovelCards(fetchDoc(BASE)) } catch (e2: Exception) { emptyList() }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> = emptyList()

    // ── Chapter content ───────────────────────────────────────────────────────
    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        return try {
            val doc = fetchDoc(url)
            val title = doc.select("h1").firstOrNull()?.text()?.trim()?.ifBlank { null }
                ?: doc.title().substringBefore(" - ").trim().ifBlank { "Chapter" }

            val article = doc.select("article").firstOrNull()
            val paragraphs = article?.select("p")?.map { it.text().trim() }
                ?.filter { it.isNotBlank() } ?: emptyList()

            val content = paragraphs.joinToString("\n\n").ifBlank {
                // Fallback if <article> isn't a plain <p>-per-line layout
                article?.text()?.trim().orEmpty()
            }

            if (content.isBlank()) {
                Log.w(TAG, "Content empty after parsing — returning error message")
                return Pair(title, "Could not load chapter content. Please try again.")
            }
            Pair(title, content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchChapter failed: ${e.message}", e)
            Pair("Error", "Failed to load chapter: ${e.message}")
        }
    }

    // ── Best-effort card parser (NOT verified live — see class doc) ─────────
    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val links = doc.select("a[href]").filter { a ->
            val href = a.attr("href").trim('/')
            href.isNotBlank() && !href.contains("/") &&
            !href.startsWith("genre") && !href.startsWith("ranking") &&
            !href.startsWith("search") && a.text().isNotBlank()
        }
        return links.distinctBy { it.attr("href") }.mapNotNull { a ->
            val slug = a.attr("href").trim('/')
            val title = a.text().trim().ifBlank { return@mapNotNull null }
            NovelEntity(
                slug = slug, title = title, coverUrl = "", synopsis = "",
                status = "", rating = "", genres = "", chapterCount = 0, latestChapter = ""
            )
        }.take(60)
    }

    // ── Predictable chapter URL builder ───────────────────────────────────────
    // No confirmed numeric pattern (chapter URLs carry the chapter's own
    // title text, not just a number) — this fallback is only ever hit if a
    // chapter is missing from the cached URL map, which fetchDetail()
    // always populates in full.
    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/$slug/chapter-$chapterNum"

    private fun slugFromPath(path: String): String? =
        path.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }

    private fun absoluteUrl(path: String): String =
        if (path.startsWith("http")) path else "$BASE${if (path.startsWith("/")) path else "/$path"}"

    private fun htmlToPlainText(html: String): String =
        Jsoup.parse(html).text().trim()
}
