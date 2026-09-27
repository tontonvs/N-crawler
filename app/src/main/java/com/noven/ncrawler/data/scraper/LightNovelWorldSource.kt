package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Scraper for lightnovelworld.org — one of the largest, longest-running
 * light novel aggregators; added as a genuinely new catalog, not a mirror
 * of anything else already in the app, and with no relation to the
 * novelbin/novelarrow/novelping lineage this app just moved away from.
 *
 * Ground truth here comes from two places, NOT a live fetch of my own —
 * same caveat as NovelBuddySource, flagged for the same reason:
 *   1. lightnovel-crawler's maintained NovelPubTemplate (lightnovelworld.com
 *      is built on this shared template in that project) — the search
 *      flow, chapter-list pagination and CSS selectors below come from it.
 *   2. Live Google-indexed snippets of lightnovelworld.org pages, which
 *      independently confirmed the /novel/<slug>/chapters/ pagination
 *      structure AND surfaced a JSON-LD block on the detail page
 *      (schema.org Book markup: name/author/numberOfPages/status) — SEO
 *      structured data like this tends to survive site redesigns that
 *      break CSS classes, so fetchNovelMetadata() below prefers it and
 *      only falls back to the template's CSS selectors when it's absent.
 *
 * Domain note: lncrawl's own source targets lightnovelworld.com; what
 * actually turned up live and indexed this session was lightnovelworld.org
 * showing the same structure. Both are listed as candidates below —
 * .org first since that's what's freshly confirmed live.
 *
 * Confirmed structure:
 *   Detail       : GET /novel/<slug>/
 *   Chapter list : GET /novel/<slug>/chapters/ , then .../chapters/page-N —
 *                  page count read from the pagination links, not guessed
 *   Chapter body : GET the chapter's own URL (real scraped href, not
 *                  synthesized — see buildChapterUrl note below)
 *   Search       : CSRF-token flow — GET /search for a
 *                  __LNRequestVerifyToken, then POST /lnsearchlive with
 *                  that token in a header and the query in the body
 *
 * NOT verified against a live fetch — best-effort only:
 *   fetchHomepage() / fetchPopular() — NovelPubTemplate has no listing
 *   concept (lncrawl is search-and-download, not browse). Implemented as a
 *   best-effort scrape of the homepage and a guessed /sort/hot-novel page
 *   using the template's own search-result-card selector, which is the
 *   only card markup confirmed anywhere in the ground truth above.
 */
class LightNovelWorldSource : NovelSource {

    override val id = "lightnovelworld"
    override val displayName = "LightNovelWorld"
    override val baseUrl get() = BASE

    private val BASE = "https://lightnovelworld.org"
    private val TAG  = "NCrawler_LightNovelWorld"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    private suspend fun fetchDoc(url: String): Document = withContext(Dispatchers.IO) {
        Log.d(TAG, "GET $url")
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        Log.d(TAG, "HTTP ${resp.code} ← $url")
        val body = resp.use { it.body!!.string() }
        Jsoup.parse(body, url)
    }

    // ── Info-only — perf fix ─────────────────────────────────────────────────
    // Metadata only, no chapter-list pagination — same intent as the perf
    // fix added for NovelArrow, doubly worthwhile here since the chapter
    // list is a genuinely separate, potentially multi-page fetch.
    override suspend fun fetchInfo(slug: String): NovelEntity? = try {
        fetchNovelMetadata(slug)
    } catch (e: Exception) {
        Log.e(TAG, "fetchInfo($slug) failed: ${e.message}", e)
        null
    }

    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val base = fetchNovelMetadata(slug) ?: return null
            val chapters = fetchAllChapters(slug)
            Log.d(TAG, "fetchDetail($slug): ${chapters.size} chapters")

            val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
            val novel = base.copy(
                chapterCount = chapters.size.takeIf { it > 0 } ?: base.chapterCount,
                chapterUrls  = urlMap
            )
            Pair(novel, chapters)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) failed: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchNovelMetadata(slug: String): NovelEntity? {
        val doc = fetchDoc("$BASE/novel/$slug/")

        // Prefer the JSON-LD schema.org block — more resilient across
        // redesigns than CSS classes, see class doc. Falls back to the
        // NovelPubTemplate selectors when a page doesn't have it.
        val ld = doc.select("script[type=application/ld+json]").firstOrNull()
            ?.let { runCatching { JSONObject(it.data()) }.getOrNull() }

        val title = ld?.optString("name")?.ifBlank { null }
            ?: doc.select("article#novel .novel-title").firstOrNull()?.text()?.trim()
            ?: return null

        val status = ld?.optString("status")?.ifBlank { null }
            ?: doc.select("article#novel .header-stats span").firstOrNull()?.text()?.trim()
            ?: ""

        val author = ld?.optJSONObject("author")?.optString("name")
            ?: doc.select("article#novel span[itemprop='author']").firstOrNull()?.text()?.trim()
            ?: ""

        val cover = doc.select("article#novel figure.cover > img").firstOrNull()
            ?.attr("abs:src") ?: ""
        val genres = doc.select("article#novel .categories a").eachText().joinToString(", ")
        val synopsis = doc.select("article#novel .summary .content").firstOrNull()
            ?.text()?.trim() ?: ""

        Log.d(TAG, "Detail: title=$title status=$status author=$author")

        return NovelEntity(
            slug = slug, title = title, coverUrl = cover, synopsis = synopsis,
            status = status, rating = "", genres = genres,
            chapterCount = 0, latestChapter = ""
        )
    }

    // ── Chapter list (paginated) ─────────────────────────────────────────────
    private suspend fun fetchAllChapters(slug: String): List<ChapterLink> {
        val listUrl = "$BASE/novel/$slug/chapters/"
        val firstPage = fetchDoc(listUrl)

        val result = mutableListOf<ChapterLink>()
        fun harvest(doc: Document) {
            doc.select("ul.chapter-list li a[href]").forEach { a ->
                val href = a.attr("abs:href")
                val label = a.select(".chapter-title").firstOrNull()?.text()?.trim()
                    ?: a.text().trim()
                if (href.isNotBlank() && label.isNotBlank()) {
                    result.add(ChapterLink(num = result.size + 1, title = label, url = href))
                }
            }
        }
        harvest(firstPage)

        // Page count from the pagination links — confirmed pattern from
        // NovelPubTemplate: href contains "page-<N>" or "page=<N>".
        val pageRegex = Regex("page[-,=](\\d+)")
        var pageCount = 1
        firstPage.select(".pagination-container li a[href]").forEach { a ->
            pageRegex.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it > pageCount) pageCount = it
            }
        }
        Log.d(TAG, "fetchAllChapters($slug): page 1 gave ${result.size}, pageCount=$pageCount")

        for (p in 2..pageCount) {
            try {
                harvest(fetchDoc("$listUrl/page-$p"))
            } catch (e: Exception) {
                Log.w(TAG, "chapter list page $p failed: ${e.message}")
            }
        }
        return result
    }

    // ── Search — CSRF-token flow ─────────────────────────────────────────────
    override suspend fun search(query: String): List<NovelEntity> {
        return try {
            val searchPage = fetchDoc("$BASE/search")
            val token = searchPage.select("#novelSearchForm input[name=__LNRequestVerifyToken]")
                .firstOrNull()?.attr("value")
            if (token.isNullOrBlank()) {
                Log.w(TAG, "search('$query'): no CSRF token found on /search")
                return emptyList()
            }

            val body = FormBody.Builder().add("inputContent", query).build()
            val resp = withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url("$BASE/lnsearchlive")
                        .header("lnrequestverifytoken", token)
                        .header("referer", "$BASE/search")
                        .post(body)
                        .build()
                ).execute()
            }
            val json = JSONObject(resp.use { it.body!!.string() })
            val resultHtml = json.optString("resultview")
            if (resultHtml.isBlank()) return emptyList()

            val doc = Jsoup.parse(resultHtml, BASE)
            doc.select(".novel-list .novel-item a").mapNotNull { a ->
                val href = a.attr("abs:href")
                val slug = slugFromUrl(href) ?: return@mapNotNull null
                val title = a.select(".novel-title").firstOrNull()?.text()?.trim()
                    ?: a.text().trim().ifBlank { return@mapNotNull null }
                val info = a.select(".novel-stats").firstOrNull()?.text()?.trim() ?: ""
                NovelEntity(
                    slug = slug, title = title, coverUrl = "", synopsis = "",
                    status = info, rating = "", genres = "", chapterCount = 0, latestChapter = ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Homepage / Popular — NOT verified live, see class doc ───────────────
    override suspend fun fetchHomepage(): List<NovelEntity> = try {
        parseCardList(fetchDoc(BASE))
    } catch (e: Exception) {
        Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
        emptyList()
    }

    override suspend fun fetchPopular(): List<NovelEntity> = try {
        parseCardList(fetchDoc("$BASE/sort/hot-novel"))
    } catch (e: Exception) {
        Log.w(TAG, "fetchPopular failed, falling back to homepage: ${e.message}")
        try { parseCardList(fetchDoc(BASE)) } catch (e2: Exception) { emptyList() }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> = emptyList()

    private fun parseCardList(doc: Document): List<NovelEntity> =
        doc.select(".novel-list .novel-item a").mapNotNull { a ->
            val href = a.attr("abs:href")
            val slug = slugFromUrl(href) ?: return@mapNotNull null
            val title = a.select(".novel-title").firstOrNull()?.text()?.trim()
                ?: a.text().trim().ifBlank { return@mapNotNull null }
            NovelEntity(
                slug = slug, title = title, coverUrl = a.select("img").firstOrNull()?.attr("abs:src") ?: "",
                synopsis = "", status = "", rating = "", genres = "",
                chapterCount = 0, latestChapter = ""
            )
        }.distinctBy { it.slug }.take(60)

    // ── Chapter content ───────────────────────────────────────────────────────
    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        return try {
            val doc = fetchDoc(url)
            val title = doc.select(".chapter-title").firstOrNull()?.text()?.trim()?.ifBlank { null }
                ?: doc.select("h1").firstOrNull()?.text()?.trim()?.ifBlank { null }
                ?: "Chapter"

            val content = doc.select(".chapter-content").firstOrNull()
                ?.select("p")?.map { it.text().trim() }?.filter { it.isNotBlank() }
                ?.joinToString("\n\n")
                ?: doc.select(".chapter-content").firstOrNull()?.text()?.trim().orEmpty()

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

    // ── Predictable chapter URL builder ───────────────────────────────────────
    // No confirmed numeric pattern — chapter URLs carry a title slug (e.g.
    // "chapter-229-to-westeros"), not just a number. This fallback is only
    // ever hit if a chapter is missing from the cached URL map, which
    // fetchDetail()'s full pagination walk always populates.
    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/novel/$slug/chapter-$chapterNum/"

    private fun slugFromUrl(url: String): String? =
        Regex("/novel/([^/?#]+)").find(url)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
