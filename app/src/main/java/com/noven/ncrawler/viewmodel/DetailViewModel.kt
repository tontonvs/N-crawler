package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Success(
        val novel: NovelEntity,
        val chapters: List<ChapterLink>,
        val lastReadChapter: Int? = null,
        // CHANGE (perf fix): true while the chapter list is still loading in
        // the background after the fast metadata paint below — lets the UI
        // show "Loading chapters…" instead of a misleading "0 total".
        val chaptersLoading: Boolean = false
    ) : DetailUiState
}

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private var currentSlug = ""

    // CHANGE (perf fix): was one sequential await — getNovel() (which itself
    // pulled the FULL chapter list before returning) then getChapterList().
    // For a long novel, that meant the whole Detail screen was blocked on
    // however many paginated requests it took to load thousands of
    // chapters, just to show a title and cover. Now: fetch metadata only
    // (fast — a single API call), paint immediately, then stream the
    // chapter list in as its own step. Download progress collection moved
    // to its own coroutine so it doesn't serialize behind either fetch.
    fun load(slug: String) {
        currentSlug = slug

        viewModelScope.launch {
            _state.value = DetailUiState.Loading

            val novel = try {
                repo.getNovelInfo(slug)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "Failed to load")
                return@launch
            }
            if (novel == null) {
                _state.value = DetailUiState.Error("Novel not found")
                return@launch
            }

            val lastRead = repo.getReadingProgress(slug)?.lastChapterNum
            _state.value = DetailUiState.Success(
                novel           = novel,
                chapters        = emptyList(),
                lastReadChapter = lastRead,
                chaptersLoading = true
            )

            // Guarded against a stale write: if the user has already
            // navigated to a different novel by the time this resolves,
            // currentSlug will have moved on and this result is discarded
            // instead of clobbering whatever screen is showing now.
            try {
                val chapters = repo.getChapterList(slug)
                if (currentSlug == slug) {
                    (_state.value as? DetailUiState.Success)?.let {
                        _state.value = it.copy(chapters = chapters, chaptersLoading = false)
                    }
                }
            } catch (e: Exception) {
                if (currentSlug == slug) {
                    (_state.value as? DetailUiState.Success)?.let {
                        _state.value = it.copy(chaptersLoading = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            repo.downloadProgressFlow(slug).collect { progress ->
                _downloadProgress.value = progress
            }
        }
    }

    fun downloadAll() {
        viewModelScope.launch {
            try { repo.queueDownloadAll(currentSlug) }
            catch (e: Exception) { /* silent */ }
        }
    }

    // CHANGE (partial downloads): backs the Detail screen's download-options
    // sheet — "Last N chapters" and volume/custom-range picks.
    fun downloadLast(count: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadLast(currentSlug, count) }
            catch (e: Exception) { /* silent */ }
        }
    }

    fun downloadRange(startChapter: Int, endChapter: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadRange(currentSlug, startChapter, endChapter) }
            catch (e: Exception) { /* silent */ }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            repo.cancelDownload(currentSlug)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val newCount = repo.checkForUpdates(currentSlug)
                _updateMessage.value = if (newCount > 0)
                    "$newCount new chapter(s) — downloading in background"
                else
                    "Already up to date"
            } catch (e: Exception) {
                _updateMessage.value = "Update check failed"
            }
        }
    }

    fun clearUpdateMessage() { _updateMessage.value = null }
}
