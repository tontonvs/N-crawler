package com.noven.ncrawler.data.scraper

import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Scrapes novelarrow.com for:
 *  - Homepage (trending + latest)
 *  - Search results
 *  - Novel detail page
 *  - Chapter content
 *
 * All network work dispatched on Dispatchers.IO.
 */
class NovelArrowScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    // ── Internal fetch ────────────────────────────────────────────────────
    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        val req  = Request.Builder().url(url).build()
        val body = client.newCall(req).execute().use { it.body!!.string() }
        Jsoup.parse(body, url)
    }

    // ── Homepage scrape: trending + latest rows ───────────────────────────
    suspend fun fetchHomepage(): List<NovelEntity> {
        val doc    = fetch("https://novelarrow.com/")
        val result = mutableListOf<NovelEntity>()

        // Novel cards — each appears as <a> with a cover image and title
        doc.select("a[href*='/novel/']").forEach { a ->
            val href  = a.attr("abs:href")
            val slug  = slugFromUrl(href) ?: return@forEach
            // skip duplicates (same novel can appear in multiple sections)
            if (result.any { it.slug == slug }) return@forEach

            val title  = a.select("img").attr("alt").ifBlank {
                a.text().trim()
            }
            val cover  = a.select("img").attr("abs:src")
            val rating = a.parents().firstOrNull()
                ?.select(".rating, [class*=star], [class*=rate]")
                ?.text()?.trim() ?: ""
            val status = a.parents().firstOrNull()
                ?.select("[class*=status], [class*=ongoing], [class*=complete]")
                ?.text()?.trim() ?: ""

            if (slug.isNotBlank() && title.isNotBlank() && cover.isNotBlank()) {
                result += NovelEntity(
                    slug         = slug,
                    title        = title,
                    coverUrl     = cover,
                    synopsis     = "",           // filled on detail fetch
                    status       = status,
                    rating       = rating,
                    genres       = "",
                    chapterCount = 0,
                    latestChapter = ""
                )
            }
        }
        return result.distinctBy { it.slug }.take(60)
    }

    // ── Search scrape ─────────────────────────────────────────────────────
    suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc     = fetch("https://novelarrow.com/novels?q=$encoded")
        return parseNovelCards(doc)
    }

    // ── Novel detail page ─────────────────────────────────────────────────
    suspend fun fetchDetail(slug: String): NovelEntity? {
        val doc = fetch("https://novelarrow.com/novel/$slug")

        val title    = doc.select("h1").firstOrNull()?.text()?.trim() ?: return null
        val cover    = doc.select("img[class*=cover], img[alt*=$slug]")
                          .firstOrNull()?.attr("abs:src") ?: ""
        val synopsis = doc.select("[class*=synopsis], [class*=summary], [class*=desc]")
                          .firstOrNull()?.text()?.trim() ?: ""
        val status   = doc.select("[class*=status]").firstOrNull()?.text()?.trim() ?: ""
        val rating   = doc.select("[class*=rating], [class*=score]")
                          .firstOrNull()?.text()?.trim() ?: ""
        val genres   = doc.select("a[href*='/genre/'], a[href*='/genres/']")
                          .joinToString(",") { it.text().trim() }
        val chapters = doc.select("a[href*='/chapter/'], a[href*='chapter-']")
        val latest   = chapters.firstOrNull()?.text()?.trim() ?: ""

        return NovelEntity(
            slug          = slug,
            title         = title,
            coverUrl      = cover,
            synopsis      = synopsis,
            status        = status,
            rating        = rating,
            genres        = genres,
            chapterCount  = chapters.size,
            latestChapter = latest
        )
    }

    // ── Chapter content ───────────────────────────────────────────────────
    suspend fun fetchChapter(slug: String, chapterNum: Int): Pair<String, String> {
        // Try common chapter URL patterns
        val urls = listOf(
            "https://novelarrow.com/novel/$slug/chapter-$chapterNum",
            "https://novelarrow.com/novel/$slug/chapter/$chapterNum"
        )
        var doc: Document? = null
        for (url in urls) {
            try { doc = fetch(url); break } catch (_: Exception) {}
        }
        val d = doc ?: return Pair("Chapter $chapterNum", "Could not load chapter.")

        val chTitle = d.select("h1, h2, [class*=chapter-title]")
                       .firstOrNull()?.text()?.trim() ?: "Chapter $chapterNum"
        val content = d.select(
            "[class*=chapter-content], [class*=content-area], article, #content"
        ).firstOrNull()?.text()?.trim() ?: d.body().text()

        return Pair(chTitle, content)
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val result = mutableListOf<NovelEntity>()
        doc.select("a[href*='/novel/']").forEach { a ->
            val href  = a.attr("abs:href")
            val slug  = slugFromUrl(href) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach
            val title  = a.select("img").attr("alt").ifBlank { a.text().trim() }
            val cover  = a.select("img").attr("abs:src")
            if (slug.isNotBlank() && title.isNotBlank() && cover.isNotBlank()) {
                result += NovelEntity(
                    slug = slug, title = title, coverUrl = cover,
                    synopsis = "", status = "", rating = "",
                    genres = "", chapterCount = 0, latestChapter = ""
                )
            }
        }
        return result.distinctBy { it.slug }
    }

    private fun slugFromUrl(url: String): String? {
        val match = Regex("/novel/([^/?#]+)").find(url)
        return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
