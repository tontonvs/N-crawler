package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.db.ReadingProgress
import com.noven.ncrawler.data.db.newChapterCount
import com.noven.ncrawler.data.db.activityAt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryItem(
    val novel: NovelEntity,
    val readingProgress: ReadingProgress?,
    val downloadProgress: DownloadProgress?
)

// A novel with unread new chapters (favourite or downloaded).
data class UpdateItem(val novel: NovelEntity, val newCount: Int)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    val libraryItems: StateFlow<List<LibraryItem>> = combine(
        repo.libraryFlow(),
        repo.allReadingProgressFlow(),
        repo.allDownloadProgressFlow()
    ) { novels, readingList, downloadList ->
        novels.map { novel ->
            LibraryItem(
                novel           = novel,
                readingProgress = readingList.find { it.novelSlug == novel.slug },
                downloadProgress = downloadList.find { it.novelSlug == novel.slug }
            )
        }
            // Recent-first: newest favourite / download / update / read on top, then A-Z.
            .sortedWith(
                compareByDescending<LibraryItem> {
                    it.novel.activityAt(it.downloadProgress, it.readingProgress)
                }.thenBy { it.novel.title.lowercase() }
            )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5000),
        initialValue  = emptyList()
    )

    val updates: StateFlow<List<UpdateItem>> = repo.updatesFlow()
        .map { list -> list.map { UpdateItem(it, it.newChapterCount()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pull-to-refresh: checks every favourite / downloaded novel for new chapters.
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    fun checkForUpdates() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                // Skip novels checked in the last 10 minutes (the background job just ran, say).
                repo.checkAllForUpdates(minIntervalMs = 10 * 60 * 1000L)
            } catch (e: Exception) {
                _refreshFailed.tryEmit(Unit)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun removeFromLibrary(slug: String) {
        viewModelScope.launch {
            repo.setLibrary(slug, false)
        }
    }
}
