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
 * Scraper for novellive.com.
 *
 * ⚠️ UNVERIFIED — NOT live-tested against raw HTML like FreeWebNovelScraper
 * was. What IS confirmed (via search-result snippets, not a raw fetch):
 *   - Identical boilerplate wording to freewebnovel.com ("Read X novel
 *     online free from your Mobile, Table, PC...") — same site template
 *   - Same JS chapter-pagination pattern (C.1-C.40 / C.41-C.80 / ...) —
 *     confirmed present on a real novellive.com detail page
 *   - URL path segment is "/book/<slug>", NOT "/novel/<slug>" like
 *     FreeWebNovel — confirmed from multiple novellive.com URLs seen in
 *     search results (e.g. novellive.com/book/cultivation-online-novel)
 *
 * NOT confirmed: exact CSS/meta selectors (og: tags, h3>a card structure,
 * div.txt chapter content div). This is a best-effort port of
 * FreeWebNovelScraper's proven logic with /novel/ → /book/ swapped
 * everywhere. Expect this to need one round of live debugging via logcat —
 * same workflow that fixed every selector bug in FreeWebNovelScraper.
 */
class NovelLiveSource : NovelSource {

    override val id = "novellive"
    override val displayName = "NovelLive"
    override val baseUrl get() = BASE

    private val BASE = "https://novellive.com"
    private val TAG  = "NCrawler_NovelLive"

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
                .header("Referer", "$BASE/")
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

    // Path assumed identical in SHAPE to FreeWebNovel's (same template),
    // "novel" → "book" swapped, "english-novel" segment dropped since it's
    // unconfirmed here — falls back to bare /sort/latest-release if that 404s.
    override suspend fun fetchHomepage(): List<NovelEntity> {
        Log.d(TAG, "fetchHomepage()")
        return try {
            val doc = fetch("$BASE/sort/latest-release")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomepage failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchPopular(): List<NovelEntity> {
        Log.d(TAG, "fetchPopular()")
        return try {
            val doc = fetch("$BASE/sort/most-popular")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPopular failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun fetchGenre(genre: String, page: Int): List<NovelEntity> {
        Log.d(TAG, "fetchGenre(genre=$genre, page=$page)")
        val encodedGenre = java.net.URLEncoder.encode(genre, "UTF-8")
        val url = if (page <= 1) "$BASE/genre/$encodedGenre" else "$BASE/genre/$encodedGenre/$page"
        return try {
            val doc = fetch(url)
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "fetchGenre('$genre', page=$page) failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val doc = fetch("$BASE/search/?searchkey=$encoded")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            throw e
        }
    }

    // Confirmed URL shape: /book/<slug>
    override suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        return try {
            val doc = fetch("$BASE/book/$slug")

            val title    = doc.select("meta[property=og:title]").attr("content")
                .ifBlank { doc.select("h3").firstOrNull()?.text() ?: return null }
                .trim()
            val cover    = doc.select("meta[property=og:image]").attr("content")
            val synopsis = doc.select("meta[property=og:description]").attr("content").trim()
            val genres   = doc.select("meta[property=og:novel:genre]").attr("content")
            val status   = doc.select("meta[property=og:novel:status]").attr("content")
            val latestChapterUrl = doc.select("meta[property=og:novel:lastest_chapter_url]").attr("content")

            Log.d(TAG, "Detail: title=$title cover=${cover.take(40)} genres=$genres")

            val anchorList = doc.select("a[href*='/book/$slug/chapter-']").toList()
            Log.d(TAG, "Found ${anchorList.size} chapter anchor tags")

            val seenUrls = mutableSetOf<String>()
            val scrapedByNum = mutableMapOf<Int, ChapterLink>()

            anchorList.forEach { a ->
                val href = a.attr("abs:href").ifBlank { return@forEach }
                val text = a.text().trim().ifBlank { return@forEach }
                if (!seenUrls.add(href)) return@forEach
                val num = Regex("/chapter-(\\d+)$").find(href)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                scrapedByNum[num] = ChapterLink(num = num, title = text, url = href)
            }
            Log.d(TAG, "Scraped ${scrapedByNum.size} distinct chapter numbers directly")

            val maxChapterNum = Regex("chapter-(\\d+)$").find(latestChapterUrl)
                ?.groupValues?.get(1)?.toIntOrNull()

            val chapters: MutableList<ChapterLink> = if (maxChapterNum != null && maxChapterNum > 0) {
                (1..maxChapterNum).map { n ->
                    scrapedByNum[n] ?: ChapterLink(
                        num   = n,
                        title = "Chapter $n",
                        url   = buildChapterUrl(slug, n)
                    )
                }.toMutableList()
            } else {
                scrapedByNum.values.toMutableList()
            }
            chapters.sortByDescending { it.num }
            Log.d(TAG, "Final chapter list: ${chapters.size} (maxChapterNum=$maxChapterNum)")

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

    override suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapter: $url")
        return try {
            val doc = fetch(url)

            val title = doc.select("h1").firstOrNull()?.text()?.trim()?.ifBlank { null }
                ?: doc.select("meta[property=og:novel:chapter_name]").attr("content").trim().ifBlank { "Chapter" }

            var paragraphs = doc.select("div.txt p").toList()
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            Log.d(TAG, "div.txt p: got ${paragraphs.size} paragraphs")

            if (paragraphs.isEmpty()) {
                paragraphs = doc.select("p").toList()
                    .map { it.text().trim() }
                    .filter { it.length > 20 }
                Log.d(TAG, "Fallback p: got ${paragraphs.size} paragraphs")
            }

            val content = paragraphs
                .filter { p ->
                    !p.contains("novellive", ignoreCase = true) &&
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

    private val coverAttrs = listOf("data-src", "data-original", "data-lazy-src", "data-echo", "src")

    private fun extractCoverUrl(card: Element): String {
        val imgs = card.select("img")
        for (img in imgs) {
            for (attr in coverAttrs) {
                val url = img.attr("abs:$attr")
                if (url.isNotBlank()) return url
            }
        }
        return ""
    }

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

        val novelLinks = doc.select("h3 > a[href*='/book/']").toList()
        Log.d(TAG, "parseNovelCards: found ${novelLinks.size} h3>a novel links")

        novelLinks.forEach { a ->
            val href  = a.attr("abs:href")
            val slug  = slugFromUrl(href) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach

            val title = a.text().trim().ifBlank { return@forEach }

            val h3     = a.parent() ?: return@forEach
            val cover  = findCoverNear(h3)

            val infoBlock  = h3.parent() ?: h3
            val ratingText = infoBlock.select("em, [class*=score]")
                .firstOrNull()?.text()?.trim() ?: ""

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

    override fun buildChapterUrl(slug: String, chapterNum: Int) =
        "$BASE/book/$slug/chapter-$chapterNum"

    private fun slugFromUrl(url: String): String? =
        Regex("novellive\\.com/book/([^/?#]+)").find(url)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
}
