package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface BrowseUiState {
    data object Loading  : BrowseUiState
    data object Empty    : BrowseUiState
    data class  Error(val message: String) : BrowseUiState
    data class  Success(val novels: List<NovelEntity>) : BrowseUiState
}

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    // ── State ─────────────────────────────────────────────────────────────
    private val _browseState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val browseState: StateFlow<BrowseUiState> = _browseState.asStateFlow()

    private val _searchState = MutableStateFlow<BrowseUiState>(BrowseUiState.Empty)
    val searchState: StateFlow<BrowseUiState> = _searchState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Debounce search so we don't fire on every keystroke
    private var searchJob: Job? = null

    init { loadHomepage() }

    // ── Actions ───────────────────────────────────────────────────────────
    fun loadHomepage() {
        viewModelScope.launch {
            _browseState.value = BrowseUiState.Loading
            _browseState.value = try {
                val novels = repo.fetchHomepage()
                if (novels.isEmpty()) BrowseUiState.Empty else BrowseUiState.Success(novels)
            } catch (e: Exception) {
                BrowseUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchState.value = BrowseUiState.Empty
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)   // 350 ms debounce
            _searchState.value = BrowseUiState.Loading
            _searchState.value = try {
                val results = repo.search(q)
                if (results.isEmpty()) BrowseUiState.Empty else BrowseUiState.Success(results)
            } catch (e: Exception) {
                BrowseUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _searchState.value = BrowseUiState.Empty
    }
}
