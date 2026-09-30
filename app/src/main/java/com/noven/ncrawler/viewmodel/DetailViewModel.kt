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

    // Drives the bookmark button (Detail had no way to add/remove a library entry).
    private val _inLibrary = MutableStateFlow(false)
    val inLibrary: StateFlow<Boolean> = _inLibrary.asStateFlow()

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
                _state.value = DetailUiState.Error(friendlyError(e, "Couldn't load"))
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

        viewModelScope.launch {
            repo.isInLibraryFlow(slug).collect { _inLibrary.value = it }
        }
    }

    fun toggleLibrary() {
        viewModelScope.launch {
            val add = !_inLibrary.value
            repo.setLibrary(currentSlug, add)
            _updateMessage.value = if (add) "Added to library" else "Removed from library"
        }
    }

    // Tap on the download button: start a fresh "download all", or — when a
    // download already exists (paused / error) — resume the range that was
    // originally requested. Used to always call downloadAll(), so retrying a
    // "Last 50" download pulled the whole novel.
    fun startOrResumeDownload() {
        if ((_state.value as? DetailUiState.Success)?.chaptersLoading == true) {
            _updateMessage.value = "Still loading chapters"
            return
        }
        viewModelScope.launch {
            try {
                if (_downloadProgress.value != null) repo.resumeDownload(currentSlug)
                else repo.queueDownloadAll(currentSlug)
            } catch (e: Exception) {
                _updateMessage.value = "Couldn't start download"
            }
        }
    }

    fun downloadAll() {
        viewModelScope.launch {
            try { repo.queueDownloadAll(currentSlug) }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    // CHANGE (partial downloads): backs the Detail screen's download-options
    // sheet — "Last N chapters" and volume/custom-range picks.
    fun downloadLast(count: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadLast(currentSlug, count) }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    fun downloadRange(startChapter: Int, endChapter: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadRange(currentSlug, startChapter, endChapter) }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
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
                if (newCount > 0) {
                    // The on-screen chapter list was stale after a check.
                    val chapters = repo.getChapterList(currentSlug)
                    (_state.value as? DetailUiState.Success)?.let {
                        _state.value = it.copy(chapters = chapters)
                    }
                }
                _updateMessage.value = when {
                    newCount <= 0                  -> "Already up to date"
                    _downloadProgress.value != null -> "$newCount new · downloading"
                    else                           -> "$newCount new chapters"
                }
            } catch (e: Exception) {
                _updateMessage.value = "Couldn't check updates"
            }
        }
    }

    fun clearUpdateMessage() { _updateMessage.value = null }
}
