package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Success(
        val novel: NovelEntity,
        val chapters: List<ChapterLink>
    ) : DetailUiState
}

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _downloading = MutableStateFlow<Set<Int>>(emptySet())
    val downloading: StateFlow<Set<Int>> = _downloading.asStateFlow()

    fun load(slug: String) {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val novel    = repo.getNovel(slug)
                    ?: run { _state.value = DetailUiState.Error("Novel not found"); return@launch }
                val chapters = repo.getChapterList(slug)
                _state.value = DetailUiState.Success(novel, chapters)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun downloadChapter(slug: String, chapterNum: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            _downloading.value = _downloading.value + chapterNum
            try {
                repo.downloadChapter(slug, chapterNum)
                onDone()
            } finally {
                _downloading.value = _downloading.value - chapterNum
            }
        }
    }
}
