package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// CHANGE (Downloads overhaul): Downloads used to piggyback on
// LibraryViewModel/LibraryItem. Split out into its own ViewModel so future
// edits to Library don't risk breaking Downloads and vice versa — the two
// screens still read the same underlying tables (via NovelRepository), they
// just no longer share a ViewModel class.
data class DownloadItem(
    val novel: NovelEntity,
    val progress: DownloadProgress
)

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    val downloadItems: StateFlow<List<DownloadItem>> = combine(
        repo.allDownloadProgressFlow(),
        repo.libraryFlow()
    ) { progressList, novels ->
        progressList.mapNotNull { progress ->
            val novel = novels.find { it.slug == progress.novelSlug } ?: return@mapNotNull null
            DownloadItem(novel = novel, progress = progress)
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Pauses an active or queued download. */
    fun pause(slug: String) {
        viewModelScope.launch { repo.cancelDownload(slug) }
    }

    /**
     * Resumes a paused download, or retries one with failed chapters.
     * Re-queuing is safe either way — the worker skips any chapter already
     * saved and only re-attempts what's actually missing.
     */
    fun resume(slug: String) {
        viewModelScope.launch { repo.queueDownloadAll(slug) }
    }

    /** Deletes downloaded chapters for a novel. Leaves it in the Library. */
    fun delete(slug: String) {
        viewModelScope.launch { repo.deleteDownload(slug) }
    }

    suspend fun sizeBytesFor(slug: String): Long = repo.downloadedSizeBytes(slug)
}
