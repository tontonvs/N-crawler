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
        val lastReadChapter: Int? = null
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

    fun load(slug: String) {
        currentSlug = slug
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val novel    = repo.getNovel(slug)
                    ?: run { _state.value = DetailUiState.Error("Novel not found"); return@launch }
                val chapters = repo.getChapterList(slug)
                val lastRead = repo.getReadingProgress(slug)?.lastChapterNum

                _state.value = DetailUiState.Success(novel, chapters, lastRead)

                // Observe download progress live
                repo.downloadProgressFlow(slug).collect { progress ->
                    _downloadProgress.value = progress
                }
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "Failed to load")
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
