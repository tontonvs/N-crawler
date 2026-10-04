package com.noven.ncrawler.data.worker

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.local.DownloadPreferences
import com.noven.ncrawler.data.local.ForegroundBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports a novel's downloaded chapters as ONE .epub file into the folder the user
 * picked (Storage Access Framework):
 *
 *     <picked folder>/<Novel title>/<Novel title> (Ch 1-1200).epub
 *
 * Replaces the old one-.txt-per-chapter export (which littered thousands of files).
 *
 * UPDATES: the chapters already packed into an EPUB are remembered per novel
 * (DownloadPreferences.saveExported). Exporting again packs only the chapters that
 * are new since, as a SEPARATE file named with its own range, e.g.
 * "<Novel title> (Ch 1201-1215).epub". Nothing is re-packed or overwritten.
 *
 * DESIGN
 *  - Room stays the source of truth; this only COPIES text out, so a failed or
 *    cancelled export can't damage the library. "Exported" is recorded only after
 *    the file has been fully written to the chosen folder.
 *  - MEMORY: the EPUB is streamed into a temp zip in the cache dir, chapter bodies
 *    loaded BATCH_SIZE at a time by primary key (never the whole novel at once),
 *    then copied to the SAF folder and the temp file deleted.
 *  - VALID EPUB 3 (with an EPUB 2 NCX for older readers): `mimetype` stored first and
 *    uncompressed, container.xml, content.opf, nav.xhtml, toc.ncx, one XHTML per
 *    chapter, optional cover. All text is XML-escaped and stripped of characters
 *    XML forbids (scraped text sometimes has stray control characters, which would
 *    make strict readers reject the whole book).
 *  - Cover is best-effort (8s timeout, JPEG/PNG only, max 3 MB); any failure just
 *    means no cover.
 *  - Runs as a foreground service like the downloader; notification ids 4202/4203.
 */
class EpubExportWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val TAG = "NCrawler_EpubExport"

    companion object {
        const val SLUG = "slug"

        private const val NOTIFICATION_ID = 4202
        private const val DONE_NOTIFICATION_ID = 4203

        private const val BATCH_SIZE = 20
        private const val BATCH_PAUSE_MS = 100L
        private const val MAX_COVER_BYTES = 3 * 1024 * 1024

        fun buildRequest(slug: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EpubExportWorker>()
                .setInputData(workDataOf(SLUG to slug))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .addTag("export")
                .addTag("export_$slug")
                .build()
    }

    // A refused foreground start must not fail the export (same as the downloader).
    private var foregroundOk = true
    private val budget by lazy { ForegroundBudget(applicationContext) }
    private var sessionId = -1L

    private suspend fun promote(info: ForegroundInfo) {
        if (!foregroundOk) return
        try {
            setForeground(info)
            if (sessionId < 0) sessionId = budget.beginSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            foregroundOk = false
            Log.w(TAG, "Couldn't start foreground service (${e.javaClass.simpleName}: ${e.message}) — continuing in background")
        }
    }

    override suspend fun doWork(): Result {
        if (!budget.canStartForeground()) {
            foregroundOk = false
            Log.w(TAG, "Foreground time budget used up — exporting as a background job")
        }
        return try {
            doExport()
        } finally {
            if (sessionId >= 0) budget.touch(sessionId)
        }
    }

    private data class Child(val docId: String, val isDir: Boolean, val size: Long)
    private data class Toc(val num: Int, val title: String)

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NCrawlerApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Building EPUB $done / $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done, false)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun postResult(title: String, text: String) {
        val n = NotificationCompat.Builder(applicationContext, NCrawlerApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(DONE_NOTIFICATION_ID, n)
    }

    private suspend fun doExport(): Result = withContext(Dispatchers.IO) {
        val slug = inputData.getString(SLUG) ?: return@withContext Result.failure()
        val prefs = DownloadPreferences(applicationContext)
        val treeString = prefs.getExportTreeUri()
        if (treeString == null) {
            postResult("Export failed", "No export folder chosen.")
            return@withContext Result.failure()
        }
        val tree = Uri.parse(treeString)

        val app = applicationContext as NCrawlerApp
        val chapterDao = app.db.chapterDao()
        val novel = app.db.novelDao().getBySlug(slug)
        val novelTitle = novel?.title?.ifBlank { null } ?: slug.substringAfter("::")

        val downloaded = chapterDao.downloadedChapterNums(slug).sorted()
        if (downloaded.isEmpty()) {
            postResult(novelTitle, "Nothing downloaded yet — nothing to export.")
            return@withContext Result.success()
        }
        val alreadyExported = prefs.getExported(slug)
        val nums = downloaded.filter { it !in alreadyExported }
        if (nums.isEmpty()) {
            postResult(novelTitle, "Nothing new to export — every downloaded chapter is already in an EPUB.")
            return@withContext Result.success()
        }
        Log.d(TAG, "Export start: $slug — ${nums.size} chapters (${alreadyExported.size} already exported)")
        promote(foregroundInfo(novelTitle, 0, nums.size))

        val tmp = File(applicationContext.cacheDir, "epub_${System.nanoTime()}.epub")
        try {
            // ── 1. Build the EPUB in the cache dir ───────────────────────────
            val cover = novel?.coverUrl?.takeIf { it.startsWith("http") }?.let { fetchCover(it) }
            val written = ArrayList<Toc>(nums.size)
            val range = rangeLabel(nums.first(), nums.last())
            val bookTitle = "$novelTitle ($range)"

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zip ->
                // `mimetype` must be the FIRST entry and STORED (not compressed).
                val mt = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                zip.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mt.size.toLong()
                    compressedSize = mt.size.toLong()
                    crc = CRC32().also { it.update(mt) }.value
                })
                zip.write(mt)
                zip.closeEntry()

                zip.text("META-INF/container.xml", CONTAINER_XML)
                if (cover != null) zip.bytes("OEBPS/${cover.fileName}", cover.data)

                for (chunk in nums.chunked(BATCH_SIZE)) {
                    if (isStopped) throw CancellationException("export stopped")
                    val rows = chapterDao.chaptersByIds(chunk.map { "$slug::$it" }).sortedBy { it.chapterNum }
                    for (ch in rows) {
                        val title = ch.title.ifBlank { "Chapter ${ch.chapterNum}" }
                        zip.text("OEBPS/text/${chapterFile(ch.chapterNum)}", chapterXhtml(title, ch.content))
                        written.add(Toc(ch.chapterNum, title))
                    }
                    promote(foregroundInfo(novelTitle, written.size, nums.size))
                    delay(BATCH_PAUSE_MS)
                }
                if (written.isEmpty()) throw IOException("No chapter text could be read")

                // Real range of what was actually packed (a chapter could vanish mid-export).
                val realRange = rangeLabel(written.first().num, written.last().num)
                val realTitle = "$novelTitle ($realRange)"
                val uid = "urn:uuid:" + UUID.nameUUIDFromBytes("$slug:$realRange".toByteArray()).toString()

                zip.text("OEBPS/nav.xhtml", navXhtml(realTitle, written))
                zip.text("OEBPS/toc.ncx", ncx(uid, realTitle, written))
                zip.text(
                    "OEBPS/content.opf",
                    opf(uid, realTitle, novel?.author.orEmpty(), novel?.synopsis.orEmpty(), cover, written)
                )
            }

            // ── 2. Copy it into the folder the user picked ───────────────────
            val resolver = applicationContext.contentResolver
            val rootDocId = DocumentsContract.getTreeDocumentId(tree)
            val folderName = safeName(novelTitle, "novel")
            val folderDocId = listChildren(resolver, tree, rootDocId)[folderName]
                ?.takeIf { it.isDir }?.docId
                ?: run {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocId)
                    val created = DocumentsContract.createDocument(
                        resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, folderName
                    ) ?: throw IOException("Couldn't create folder \"$folderName\"")
                    DocumentsContract.getDocumentId(created)
                }
            val folderUri = DocumentsContract.buildDocumentUriUsingTree(tree, folderDocId)

            val finalRange = rangeLabel(written.first().num, written.last().num)
            val fileName = safeName("$novelTitle ($finalRange)", "novel").take(120) + ".epub"
            val existing = listChildren(resolver, tree, folderDocId)[fileName]
            val docUri = existing?.takeIf { !it.isDir }
                ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.docId) }
                ?: DocumentsContract.createDocument(resolver, folderUri, "application/epub+zip", fileName)
                ?: throw IOException("Couldn't create $fileName")

            // "wt" = write + truncate, so overwriting never leaves old bytes behind.
            resolver.openOutputStream(docUri, "wt")?.use { out ->
                FileInputStream(tmp).use { it.copyTo(out, 64 * 1024) }
            } ?: throw IOException("Couldn't open $fileName for writing")

            // ── 3. Only now remember these chapters as exported ──────────────
            prefs.saveExported(slug, alreadyExported + written.map { it.num })
            Log.d(TAG, "Export done: $fileName (${written.size} chapters)")
            postResult(
                "$novelTitle exported",
                "${written.size} chapter${if (written.size == 1) "" else "s"} · $finalRange\n$folderName/$fileName"
            )
            Result.success(workDataOf("written" to written.size))
        } catch (e: CancellationException) {
            Log.d(TAG, "Export cancelled")
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "Export folder not accessible: ${e.message}")
            postResult("Export failed", "Can't access the export folder — choose it again.")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Export aborted: ${e.message}", e)
            postResult("Export stopped", e.message ?: "unknown error")
            Result.failure()
        } finally {
            tmp.delete()
        }
    }

    // ── EPUB parts ────────────────────────────────────────────────────────────

    private class Cover(val fileName: String, val mediaType: String, val data: ByteArray)

    // Best effort: JPEG or PNG only (the core EPUB image types), small, quick.
    private fun fetchCover(url: String): Cover? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NCrawler")
        }
        try {
            if (conn.responseCode !in 200..299) null
            else {
                val data = conn.inputStream.use { it.readBytes() }
                when {
                    data.size < 8 || data.size > MAX_COVER_BYTES -> null
                    data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() ->
                        Cover("cover.jpg", "image/jpeg", data)
                    data[0] == 0x89.toByte() && data[1] == 0x50.toByte() ->
                        Cover("cover.png", "image/png", data)
                    else -> null
                }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cover skipped: ${e.message}")
        null
    }

    private fun chapterFile(num: Int) = String.format(Locale.ROOT, "c%05d.xhtml", num)
    private fun chapterId(num: Int) = String.format(Locale.ROOT, "c%05d", num)

    private fun rangeLabel(first: Int, last: Int) = if (first == last) "Ch $first" else "Ch $first-$last"

    private val NS = "xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\""

    private fun chapterXhtml(title: String, content: String): String {
        val sb = StringBuilder(content.length + 400)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<html $NS lang=\"en\" xml:lang=\"en\"><head><meta charset=\"utf-8\"/><title>")
            .append(xml(title)).append("</title></head><body>\n<h2>").append(xml(title)).append("</h2>\n")
        for (line in content.split("\n")) {
            val t = line.trim()
            if (t.isNotEmpty()) sb.append("<p>").append(xml(t)).append("</p>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun navXhtml(bookTitle: String, toc: List<Toc>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<html $NS lang=\"en\" xml:lang=\"en\"><head><meta charset=\"utf-8\"/><title>")
            .append(xml(bookTitle)).append("</title></head><body>\n")
        sb.append("<nav epub:type=\"toc\" id=\"toc\"><h1>Contents</h1><ol>\n")
        for (t in toc) {
            sb.append("<li><a href=\"text/").append(chapterFile(t.num)).append("\">")
                .append(xml(t.title)).append("</a></li>\n")
        }
        sb.append("</ol></nav>\n</body></html>")
        return sb.toString()
    }

    private fun ncx(uid: String, bookTitle: String, toc: List<Toc>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><head>")
        sb.append("<meta name=\"dtb:uid\" content=\"").append(xml(uid)).append("\"/>")
        sb.append("<meta name=\"dtb:depth\" content=\"1\"/><meta name=\"dtb:totalPageCount\" content=\"0\"/>")
        sb.append("<meta name=\"dtb:maxPageNumber\" content=\"0\"/></head>")
        sb.append("<docTitle><text>").append(xml(bookTitle)).append("</text></docTitle><navMap>\n")
        toc.forEachIndexed { i, t ->
            sb.append("<navPoint id=\"n").append(i + 1).append("\" playOrder=\"").append(i + 1).append("\">")
                .append("<navLabel><text>").append(xml(t.title)).append("</text></navLabel>")
                .append("<content src=\"text/").append(chapterFile(t.num)).append("\"/></navPoint>\n")
        }
        sb.append("</navMap></ncx>")
        return sb.toString()
    }

    private fun opf(
        uid: String, bookTitle: String, author: String, synopsis: String,
        cover: Cover?, toc: List<Toc>
    ): String {
        val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" xml:lang=\"en\">\n")
        sb.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
        sb.append("<dc:identifier id=\"bookid\">").append(xml(uid)).append("</dc:identifier>\n")
        sb.append("<dc:title>").append(xml(bookTitle)).append("</dc:title>\n")
        sb.append("<dc:language>en</dc:language>\n")
        if (author.isNotBlank()) sb.append("<dc:creator>").append(xml(author)).append("</dc:creator>\n")
        if (synopsis.isNotBlank()) sb.append("<dc:description>").append(xml(synopsis.take(1500))).append("</dc:description>\n")
        sb.append("<meta property=\"dcterms:modified\">").append(modified).append("</meta>\n")
        if (cover != null) sb.append("<meta name=\"cover\" content=\"cover-image\"/>\n")
        sb.append("</metadata>\n<manifest>\n")
        sb.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
        sb.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        if (cover != null) {
            sb.append("<item id=\"cover-image\" href=\"").append(cover.fileName).append("\" media-type=\"")
                .append(cover.mediaType).append("\" properties=\"cover-image\"/>\n")
        }
        for (t in toc) {
            sb.append("<item id=\"").append(chapterId(t.num)).append("\" href=\"text/").append(chapterFile(t.num))
                .append("\" media-type=\"application/xhtml+xml\"/>\n")
        }
        sb.append("</manifest>\n<spine toc=\"ncx\">\n")
        for (t in toc) sb.append("<itemref idref=\"").append(chapterId(t.num)).append("\"/>\n")
        sb.append("</spine>\n</package>")
        return sb.toString()
    }

    private val CONTAINER_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
        "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>" +
        "</rootfiles></container>"

    private fun ZipOutputStream.text(name: String, content: String) = bytes(name, content.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.bytes(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }

    // XML-escapes AND drops characters XML 1.0 forbids (control chars, lone surrogates,
    // U+FFFE/FFFF). Scraped text occasionally contains them; one would invalidate the book.
    private fun xml(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val cp = Character.codePointAt(s, i)
            i += Character.charCount(cp)
            val ok = cp == 0x9 || cp == 0xA || cp == 0xD ||
                cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
            if (!ok) continue
            when (cp) {
                '&'.code -> sb.append("&amp;")
                '<'.code -> sb.append("&lt;")
                '>'.code -> sb.append("&gt;")
                '"'.code -> sb.append("&quot;")
                '\''.code -> sb.append("&apos;")
                else -> sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }

    // ── SAF helpers ───────────────────────────────────────────────────────────

    // One provider query for every child of a folder.
    private fun listChildren(resolver: ContentResolver, tree: Uri, parentDocId: String): Map<String, Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val result = HashMap<String, Child>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                result[name] = Child(id, mime == DocumentsContract.Document.MIME_TYPE_DIR, size)
            }
        }
        return result
    }

    // Strips characters that are illegal in file names on common providers.
    private fun safeName(raw: String, fallback: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.ifBlank { fallback }
    }
}
