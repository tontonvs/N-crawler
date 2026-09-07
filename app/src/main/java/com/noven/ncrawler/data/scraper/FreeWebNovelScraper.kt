package com.noven.ncrawler.data.scraper

import android.util.Log
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

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
                .header("Referer", BASE)
                .build()
            chain.proceed(req)
        }
        .build()

    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching: $url")
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        Log.d(TAG, "Response ${resp.code} for $url")
        val body = resp.use { it.body!!.string() }
        Log.d(TAG, "Body length: ${body.length} chars")
        Jsoup.parse(body, url)
    }

    suspend fun fetchHomepage(): List<NovelEntity> {
        Log.d(TAG, "fetchHomepage() called")
        return try {
            val doc    = fetch("$BASE/latest-release-novel/")
            val novels = parseNovelCards(doc)
            Log.d(TAG, "fetchHomepage() parsed ${novels.size} novel cards")
            novels
        } catch (e: Exception) {
            Log.e(TAG, "fetchHomepage() exception: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return try {
            val doc = fetch("$BASE/search/?searchkey=$encoded")
            parseNovelCards(doc)
        } catch (e: Exception) {
            Log.e(TAG, "search() exception: ${e.message}", e)
            throw e
        }
    }

    suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        val doc = try {
            fetch("$BASE/$slug/")
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetail($slug) exception: ${e.message}", e)
            return null
        }

        val title = doc.select("h1.tit").firstOrNull()?.text()?.trim()
            ?: doc.select("h1").firstOrNull()?.text()?.trim()
            ?: return null

        val cover = doc.select("meta[property=og:image]").attr("content").ifBlank {
            doc.select(".pic img, .book-img img").firstOrNull()?.attr("abs:src") ?: ""
        }
        val synopsis = doc.select(".describe .inner, .synopsis .inner")
            .firstOrNull()?.text()?.trim() ?: ""
        val status   = doc.select("[class*=status], .book-state")
            .firstOrNull()?.text()?.trim() ?: ""
        val rating   = doc.select("p.score, em.s1, [class*=score]")
            .firstOrNull()?.text()?.trim() ?: ""
        val genres   = doc.select("a[href*='/genre/'], .book-label a")
            .joinToString(",") { it.text().trim() }

        val anchorList: List<org.jsoup.nodes.Element> = run {
            val primary = doc.select("div.m-newest2 ul#idData li a.con")
            if (primary.isNotEmpty()) return@run primary.toList()
            val fallback1 = doc.select("ul#idData li a")
            if (fallback1.isNotEmpty()) return@run fallback1.toList()
            doc.select("[class*=chapter-list] li a").toList()
        }

        Log.d(TAG, "fetchDetail($slug): found ${anchorList.size} chapter links")

        val seenUrls = mutableSetOf<String>()
        val chapters = mutableListOf<ChapterLink>()
        anchorList.forEachIndexed { idx, a ->
            val href = a.attr("abs:href").ifBlank { return@forEachIndexed }
            val text = a.text().trim().ifBlank { "Chapter ${idx + 1}" }
            if (seenUrls.contains(href)) return@forEachIndexed
            seenUrls.add(href)
            val num = extractChapterNum(href, text) ?: (idx + 1)
            chapters.add(ChapterLink(num = num, title = text, url = href))
        }
        chapters.sortByDescending { it.num }

        val urlMap = chapters.joinToString("\t") { "${it.num}|${it.url}" }
        val novel  = NovelEntity(
            slug = slug, title = title, coverUrl = cover,
            synopsis = synopsis, status = status, rating = rating,
            genres = genres, chapterCount = chapters.size,
            latestChapter = chapters.firstOrNull()?.title ?: "",
            chapterUrls = urlMap
        )
        return Pair(novel, chapters)
    }

    suspend fun fetchChapterByUrl(url: String): Pair<String, String> {
        Log.d(TAG, "fetchChapterByUrl: $url")
        val doc = try { fetch(url) }
            catch (e: Exception) {
                Log.e(TAG, "fetchChapterByUrl exception: ${e.message}", e)
                return Pair("Error", "Failed to load: ${e.message}")
            }

        val title = doc.select("div.top span.chapter, h1, [class*=chapter-title]")
            .firstOrNull()?.text()?.trim() ?: "Chapter"

        var content = doc.select("div.txt div#article").firstOrNull()?.text()?.trim() ?: ""
        Log.d(TAG, "Primary selector got ${content.length} chars")

        if (content.length < 200) {
            val candidates = doc.select("div#article, div.txt, div.chapter-content").toList()
            content = candidates
                .filter { it.text().length > 200 }
                .maxByOrNull { it.text().length }
                ?.text()?.trim() ?: ""
            Log.d(TAG, "Fallback selector got ${content.length} chars")
        }

        content = content.lines()
            .filter { line ->
                !line.contains("freewebnovel", ignoreCase = true) &&
                !line.contains("libread", ignoreCase = true) &&
                line.trim().isNotBlank()
            }
            .joinToString("\n\n")

        if (content.isBlank()) content = "Could not extract content — try again."
        Log.d(TAG, "fetchChapterByUrl done, content length: ${content.length}")
        return Pair(title, content)
    }

    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val result = mutableListOf<NovelEntity>()
        val cards = run {
            val primary = doc.select(".li-row .li, .book-item, div.col-content").toList()
            if (primary.isNotEmpty()) primary
            else doc.select("a[href*='freewebnovel.com/']").toList()
        }
        Log.d(TAG, "parseNovelCards: found ${cards.size} raw cards")

        cards.forEach { card ->
            val href  = card.select("a[href]").firstOrNull()?.attr("abs:href") ?: return@forEach
            val slug  = slugFromUrl(href) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach
            val title = card.select("h3, h4, .tit").firstOrNull()?.text()?.trim()
                ?: card.select("img").firstOrNull()?.attr("alt")?.trim()
                ?: return@forEach
            val cover  = card.select("img").firstOrNull()?.attr("abs:src") ?: ""
            val rating = card.select("[class*=score], em.s1").firstOrNull()?.text()?.trim() ?: ""
            if (slug.isNotBlank() && title.isNotBlank()) {
                result.add(NovelEntity(
                    slug = slug, title = title, coverUrl = cover,
                    synopsis = "", status = "", rating = rating,
                    genres = "", chapterCount = 0, latestChapter = ""
                ))
            }
        }
        Log.d(TAG, "parseNovelCards: returning ${result.size} novels")
        return result.distinctBy { it.slug }.take(60)
    }

    private fun extractChapterNum(url: String, text: String): Int? {
        Regex("/chapter-(\\d+)\\.html").find(url)
            ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("chapter[\\s-]*(\\d+)", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    fun slugFromUrl(url: String): String? =
        Regex("freewebnovel\\.com/([^/?#]+)").find(url)
            ?.groupValues?.get(1)
            ?.takeIf {
                it.isNotBlank() &&
                !it.startsWith("search") &&
                !it.startsWith("genre") &&
                !it.startsWith("latest")
            }
}
