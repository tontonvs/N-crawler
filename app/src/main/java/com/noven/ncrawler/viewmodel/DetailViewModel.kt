package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Success(val novel: NovelEntity) : DetailUiState
}

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    // Track which chapters are being downloaded
    private val _downloading = MutableStateFlow<Set<Int>>(emptySet())
    val downloading: StateFlow<Set<Int>> = _downloading.asStateFlow()

    fun load(slug: String) {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = try {
                val novel = repo.getNovel(slug)
                if (novel != null) DetailUiState.Success(novel)
                else DetailUiState.Error("Novel not found")
            } catch (e: Exception) {
                DetailUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun downloadChapter(slug: String, chapterNum: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            _downloading.value = _downloading.value + chapterNum
            try {
                repo.downloadChapter(slug, chapterNum)
                onDone()
            } catch (e: Exception) {
                // chapter failed — silently drop from downloading set
            } finally {
                _downloading.value = _downloading.value - chapterNum
            }
        }
    }
}
