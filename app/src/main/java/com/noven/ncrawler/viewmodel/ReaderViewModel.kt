package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.ChapterEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(val chapter: ChapterEntity) : ReaderUiState
}

data class ReaderSettings(
    val fontSize: Float  = 17f,
    val darkMode: Boolean = true,
    val lineHeight: Float = 1.8f
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private var currentSlug    = ""
    private var currentChapter = 0

    fun load(slug: String, chapterNum: Int) {
        currentSlug    = slug
        currentChapter = chapterNum
        viewModelScope.launch {
            _state.value = ReaderUiState.Loading
            _state.value = try {
                val chapter = repo.downloadChapter(slug, chapterNum)
                // Auto-save reading progress whenever a chapter loads
                repo.saveReadingProgress(slug, chapterNum, chapter.title)
                ReaderUiState.Success(chapter)
            } catch (e: Exception) {
                ReaderUiState.Error(e.message ?: "Failed to load chapter")
            }
        }
    }

    fun saveScrollPosition(scrollPos: Int) {
        viewModelScope.launch {
            repo.saveReadingProgress(
                slug         = currentSlug,
                chapterNum   = currentChapter,
                chapterTitle = (state.value as? ReaderUiState.Success)?.chapter?.title ?: "",
                scrollPos    = scrollPos
            )
        }
    }

    fun loadNext() = load(currentSlug, currentChapter + 1)
    fun loadPrev() { if (currentChapter > 1) load(currentSlug, currentChapter - 1) }

    fun increaseFontSize() {
        _settings.value = _settings.value.copy(
            fontSize = (_settings.value.fontSize + 1f).coerceAtMost(28f)
        )
    }
    fun decreaseFontSize() {
        _settings.value = _settings.value.copy(
            fontSize = (_settings.value.fontSize - 1f).coerceAtLeast(12f)
        )
    }
    fun toggleDarkMode() {
        _settings.value = _settings.value.copy(darkMode = !_settings.value.darkMode)
    }

    val currentChapterNum get() = currentChapter
}
