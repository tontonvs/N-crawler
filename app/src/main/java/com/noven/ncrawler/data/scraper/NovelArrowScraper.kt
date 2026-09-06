package com.noven.ncrawler.data.scraper

import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

data class ChapterLink(val num: Int, val title: String, val url: String)

class NovelArrowScraper {

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
                .header("Referer", "https://novelarrow.com/")
                .build()
            chain.proceed(req)
        }
        .build()

    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        val req  = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        val body = resp.use { it.body!!.string() }
        Jsoup.parse(body, url)
    }

    // ── Homepage ──────────────────────────────────────────────────────────────
    suspend fun fetchHomepage(): List<NovelEntity> {
        val doc = fetch("https://novelarrow.com/")
        return parseNovelCards(doc)
    }

    // ── Search ────────────────────────────────────────────────────────────────
    suspend fun search(query: String): List<NovelEntity> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // Try both common search URL patterns
        val urls = listOf(
            "https://novelarrow.com/novels?q=$encoded",
            "https://novelarrow.com/search?q=$encoded",
            "https://novelarrow.com/?s=$encoded"
        )
        for (url in urls) {
            try {
                val doc     = fetch(url)
                val results = parseNovelCards(doc)
                if (results.isNotEmpty()) return results
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    // ── Novel detail — returns novel + real chapter links ─────────────────────
    suspend fun fetchDetail(slug: String): Pair<NovelEntity, List<ChapterLink>>? {
        val url = "https://novelarrow.com/novel/$slug"
        val doc = try { fetch(url) } catch (e: Exception) { return null }

        val title    = doc.select("h1").firstOrNull()?.text()?.trim() ?: return null
        val cover    = doc.select("img[class*=cover], img[alt*=$slug], .novel-cover img, img.cover")
                          .firstOrNull()?.attr("abs:src") ?: ""
        val synopsis = doc.select(
            "[class*=synopsis], [class*=summary], [class*=description], [class*=desc], .content"
        ).firstOrNull()?.text()?.trim() ?: ""
        val status   = doc.select("[class*=status]").firstOrNull()?.text()?.trim() ?: ""
        val rating   = doc.select("[class*=rating], [class*=score], [class*=star]")
                          .firstOrNull()?.text()?.trim() ?: ""
        val genres   = doc.select("a[href*='/genre/']")
                          .joinToString(",") { it.text().trim() }

        // Extract ALL chapter links from the page
        // NovelArrow chapter links typically contain /novel/<slug>/ in path
        val chapterLinks = extractChapterLinks(doc, slug)

        // Serialise chapter URL map for storage
        val urlMap = chapterLinks.joinToString("\t") { "${it.num}|${it.url}" }

        val novel = NovelEntity(
            slug          = slug,
            title         = title,
            coverUrl      = cover,
            synopsis      = synopsis,
            status        = status,
            rating        = rating,
            genres        = genres,
            chapterCount  = chapterLinks.size,
            latestChapter = chapterLinks.firstOrNull()?.title ?: "",
            chapterUrls   = urlMap
        )
        return Pair(novel, chapterLinks)
    }

    // ── Extract chapter links from a novel detail page ────────────────────────
    private fun extractChapterLinks(doc: Document, slug: String): List<ChapterLink> {
        val links = mutableListOf<ChapterLink>()

        // Select all <a> tags whose href contains the novel slug and looks like a chapter
        val anchors = doc.select("a[href]").filter { el ->
            val href = el.attr("href")
            href.contains("/novel/$slug/") || href.contains("/$slug/c")
        }

        anchors.forEachIndexed { index, el ->
            val href  = el.attr("abs:href")
            val text  = el.text().trim()
            if (href.isBlank() || text.isBlank()) return@forEachIndexed
            if (links.any { it.url == href }) return@forEachIndexed // deduplicate

            // Try to extract chapter number from URL or text
            val num = extractChapterNum(href, text) ?: (index + 1)
            links += ChapterLink(num = num, title = text, url = href)
        }

        // Sort descending by chapter number (latest first, like the site)
        return links.sortedByDescending { it.num }
    }

    private fun extractChapterNum(url: String, text: String): Int? {
        // Try URL patterns: /c123, /chapter-123, /c-123
        val urlPatterns = listOf(
            Regex("/c(\\d+)[-_]"),
            Regex("/c(\\d+)$"),
            Regex("/chapter-(\\d+)"),
            Regex("/chapter/(\\d+)"),
            Regex("-(\\d+)-")
        )
        for (pattern in urlPatterns) {
            pattern.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        // Try text: "Chapter 5", "C5", "C5 - Title"
        val textPatterns = listOf(
            Regex("(?:chapter|c)[\\s-]*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("^[Cc](\\d+)")
        )
        for (pattern in textPatterns) {
            pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    // ── Fetch chapter content by direct URL ───────────────────────────────────
    suspend fun fetchChapterByUrl(chapterUrl: String): Pair<String, String> {
        val doc = try {
            fetch(chapterUrl)
        } catch (e: Exception) {
            return Pair("Error", "Could not load chapter: ${e.message}")
        }

        val title = doc.select("h1, h2, [class*=chapter-title], [class*=title]")
                       .firstOrNull()?.text()?.trim() ?: "Chapter"

        // Try multiple content selectors — NovelArrow may use different class names
        val contentSelectors = listOf(
            "[class*=chapter-content]",
            "[class*=content-area]",
            "[class*=reading-content]",
            "[class*=novel-content]",
            "[class*=text-content]",
            "article",
            ".content",
            "#content",
            "main"
        )
        var content = ""
        for (sel in contentSelectors) {
            val el = doc.select(sel).firstOrNull()
            if (el != null && el.text().length > 200) {
                content = el.text().trim()
                break
            }
        }
        if (content.isBlank()) content = doc.body().text()

        return Pair(title, content)
    }

    // ── Parse novel cards from any listing page ───────────────────────────────
    private fun parseNovelCards(doc: Document): List<NovelEntity> {
        val result = mutableListOf<NovelEntity>()
        doc.select("a[href*='/novel/']").forEach { a ->
            val href  = a.attr("abs:href")
            val slug  = slugFromUrl(href) ?: return@forEach
            if (result.any { it.slug == slug }) return@forEach
            val title = a.select("img").attr("alt").ifBlank { a.text().trim() }
            val cover = a.select("img").attr("abs:src")
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

    private fun slugFromUrl(url: String): String? =
        Regex("/novel/([^/?#]+)").find(url)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() && it != "novel" }
}
