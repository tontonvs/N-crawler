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
import java.io.IOException
import java.util.Locale

/**
 * Exports a novel's downloaded chapters as plain .txt files into a folder the
 * user picked (Storage Access Framework), one file per chapter:
 *
 *     <picked folder>/<Novel title>/00001 - <Chapter title>.txt
 *
 * DESIGN (see the notes in the hand-off):
 *  - Room stays the source of truth for reading. This only COPIES text out,
 *    so a failed/cancelled export can never damage the library or the reader.
 *  - BATCHES: chapter ids are sorted once (a cheap list of ints), then bodies
 *    are loaded BATCH_SIZE at a time by primary key, written, and released.
 *    Memory stays at roughly one batch (a few hundred KB), not the whole
 *    novel — the old chaptersForNovel() Flow would materialise every body.
 *  - RESUMABLE / IDEMPOTENT: files that already exist with content are
 *    skipped, zero-byte leftovers (crash mid-write) are rewritten. Running the
 *    export again after downloading more chapters only writes the new ones.
 *  - FAST ON SAF: the folder is listed ONCE up front with a single provider
 *    query (name + size), instead of one lookup per chapter — per-file SAF
 *    lookups are what make naive exporters crawl on low-end phones.
 *  - Only framework APIs (DocumentsContract) — no new dependency.
 *
 * Runs as a foreground service (same type/channel as the downloader) so a big
 * export survives the app being backgrounded. Uses its own notification ids so
 * it never collides with a running download (4201).
 */
class TxtExportWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val TAG = "NCrawler_TxtExport"

    companion object {
        const val SLUG = "slug"

        private const val NOTIFICATION_ID = 4202
        private const val DONE_NOTIFICATION_ID = 4203

        private const val BATCH_SIZE = 20
        private const val BATCH_PAUSE_MS = 150L        // lets the UI thread breathe on a weak CPU
        private const val MAX_CONSECUTIVE_FAILURES = 5 // e.g. folder permission revoked → stop early

        fun buildRequest(slug: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<TxtExportWorker>()
                .setInputData(workDataOf(SLUG to slug))
                // Pure local I/O — no network needed. Just don't start when
                // the phone is nearly out of space.
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .addTag("export")
                .addTag("export_$slug")
                .build()
    }

    // Same reasoning as ChapterDownloadWorker.promote(): a refused foreground
    // start must not fail the export — it just runs as a normal background job.
    private var foregroundOk = true

    // CHANGE (Android 15): counts against the same 6-hour dataSync budget as
    // downloads (see ForegroundBudget). An export is short, so it never yields —
    // it just doesn't start a foreground service when the budget is used up.
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

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NCrawlerApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Exporting TXT $done / $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done, false)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun postResult(title: String, text: String) {
        val n = NotificationCompat.Builder(applicationContext, NCrawlerApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(DONE_NOTIFICATION_ID, n)
    }

    private suspend fun doExport(): Result = withContext(Dispatchers.IO) {
        val slug = inputData.getString(SLUG) ?: return@withContext Result.failure()
        val treeString = DownloadPreferences(applicationContext).getExportTreeUri()
        if (treeString == null) {
            postResult("Export failed", "No export folder chosen.")
            return@withContext Result.failure()
        }
        val tree = Uri.parse(treeString)

        val app = applicationContext as NCrawlerApp
        val chapterDao = app.db.chapterDao()
        val novelTitle = app.db.novelDao().getBySlug(slug)?.title?.ifBlank { null }
            ?: slug.substringAfter("::")

        val nums = chapterDao.downloadedChapterNums(slug).sorted()
        if (nums.isEmpty()) {
            postResult(novelTitle, "Nothing downloaded yet — nothing to export.")
            return@withContext Result.success()
        }
        val total = nums.size
        Log.d(TAG, "Export start: $slug — $total chapters, folder=$tree")

        promote(foregroundInfo(novelTitle, 0, total))

        val resolver = applicationContext.contentResolver
        var written = 0
        var skipped = 0
        var failed = 0
        var consecutiveFailures = 0
        var lastError = ""

        try {
            // ── Resolve (or create) "<picked folder>/<Novel title>" ───────────
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

            // One listing for the whole export (name → id/size).
            val files = listChildren(resolver, tree, folderDocId)

            // ── Batches ───────────────────────────────────────────────────────
            for (chunk in nums.chunked(BATCH_SIZE)) {
                if (isStopped) break

                val rows = chapterDao.chaptersByIds(chunk.map { "$slug::$it" })
                    .sortedBy { it.chapterNum }

                for (ch in rows) {
                    if (isStopped) break
                    val name = fileName(ch.chapterNum, ch.title)
                    val existing = files[name]

                    if (existing != null && !existing.isDir && existing.size > 0) {
                        skipped++
                        consecutiveFailures = 0
                        continue
                    }

                    try {
                        val docUri = existing?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.docId) }
                            ?: DocumentsContract.createDocument(resolver, folderUri, "text/plain", name)
                            ?: throw IOException("Couldn't create $name")

                        val body = ch.title + "\n\n" + ch.content + "\n"
                        // "wt" = write + truncate; plain "w" may leave old bytes
                        // behind on some providers when overwriting.
                        resolver.openOutputStream(docUri, "wt")?.use { out ->
                            out.write(body.toByteArray(Charsets.UTF_8))
                        } ?: throw IOException("Couldn't open $name for writing")

                        written++
                        consecutiveFailures = 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        consecutiveFailures++
                        lastError = e.message ?: e.javaClass.simpleName
                        Log.e(TAG, "Export failed for chapter ${ch.chapterNum}: $lastError")
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            throw IOException("Stopped after $MAX_CONSECUTIVE_FAILURES failures in a row: $lastError")
                        }
                    }
                }

                promote(foregroundInfo(novelTitle, written + skipped + failed, total))
                delay(BATCH_PAUSE_MS)
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Export cancelled: $written written, $skipped skipped")
            throw e
        } catch (e: SecurityException) {
            // Folder permission was revoked (or never persisted).
            Log.e(TAG, "Export folder not accessible: ${e.message}")
            postResult("Export failed", "Can't access the export folder — choose it again.")
            return@withContext Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Export aborted: ${e.message}", e)
            postResult("Export stopped", "$written written · ${e.message ?: "unknown error"}")
            return@withContext Result.failure()
        }

        Log.d(TAG, "Export done: $written written, $skipped skipped, $failed failed")
        val summary = buildString {
            append("$written new")
            if (skipped > 0) append(" · $skipped already exported")
            if (failed > 0) append(" · $failed failed")
        }
        postResult("$novelTitle exported", summary)
        Result.success(workDataOf("written" to written, "skipped" to skipped, "failed" to failed))
    }

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

    // 5-digit zero-padded number so files sort correctly up to 99,999 chapters.
    // Locale.ROOT keeps the digits ASCII regardless of the phone's language.
    private fun fileName(chapterNum: Int, title: String): String =
        String.format(Locale.ROOT, "%05d", chapterNum) + " - " + safeName(title, "Chapter $chapterNum").take(80) + ".txt"

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
