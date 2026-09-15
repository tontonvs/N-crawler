package com.noven.ncrawler.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.db.ReadingProgress
import com.noven.ncrawler.data.local.RecentSearchStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface BrowseUiState {
    data object Loading  : BrowseUiState
    data object Empty    : BrowseUiState
    data class  Error(val message: String) : BrowseUiState
    data class  Success(val novels: List<NovelEntity>) : BrowseUiState
}

// The novel + progress row for whatever the user most recently read — powers
// both the home hero card and the bottom-nav play FAB, so both resume at the
// exact chapter instead of just opening a screen.
data class ContinueReadingInfo(
    val novel: NovelEntity,
    val progress: ReadingProgress
)

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val repo        = (app as NCrawlerApp).repository
    private val recentStore = RecentSearchStore(app)
    private val TAG  = "NCrawler_Browse"

    private val _browseState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val browseState: StateFlow<BrowseUiState> = _browseState.asStateFlow()

    private val _searchState = MutableStateFlow<BrowseUiState>(BrowseUiState.Empty)
    val searchState: StateFlow<BrowseUiState> = _searchState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Recent search terms — max 5, most-recent-first, persisted across sessions
    private val _recentSearches = MutableStateFlow(recentStore.getRecent())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Most-recently-read novel + exact chapter, or null if nothing read yet
    private val _continueReading = MutableStateFlow<ContinueReadingInfo?>(null)
    val continueReading: StateFlow<ContinueReadingInfo?> = _continueReading.asStateFlow()

    private var searchJob: Job? = null

    init {
        Log.d(TAG, "BrowseViewModel created — calling loadHomepage()")
        loadHomepage()
        observeContinueReading()
    }

    private fun observeContinueReading() {
        viewModelScope.launch {
            repo.allReadingProgressFlow()
                .map { it.firstOrNull() }   // already ordered by lastReadAt DESC
                .distinctUntilChanged()
                .collectLatest { progress ->
                    _continueReading.value = progress?.let { p ->
                        repo.getNovel(p.novelSlug)?.let { novel -> ContinueReadingInfo(novel, p) }
                    }
                }
        }
    }

    fun loadHomepage() {
        viewModelScope.launch {
            Log.d(TAG, "loadHomepage() started")
            _browseState.value = BrowseUiState.Loading
            try {
                Log.d(TAG, "Calling repo.fetchHomepage()...")
                val novels = repo.fetchHomepage()
                Log.d(TAG, "fetchHomepage() returned ${novels.size} novels")
                _browseState.value = if (novels.isEmpty()) {
                    Log.w(TAG, "Novel list is empty")
                    BrowseUiState.Empty
                } else {
                    BrowseUiState.Success(novels)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchHomepage() FAILED: ${e::class.simpleName}: ${e.message}", e)
                _browseState.value = BrowseUiState.Error(
                    "${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _searchState.value = BrowseUiState.Empty; return }
        searchJob = viewModelScope.launch {
            delay(350)
            _searchState.value = BrowseUiState.Loading
            try {
                Log.d(TAG, "search('$q') started")
                val results = repo.search(q)
                Log.d(TAG, "search('$q') returned ${results.size} results")
                _searchState.value = if (results.isEmpty()) BrowseUiState.Empty
                                     else BrowseUiState.Success(results)
            } catch (e: Exception) {
                Log.e(TAG, "search('$q') FAILED: ${e.message}", e)
                _searchState.value = BrowseUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _searchState.value = BrowseUiState.Empty
    }

    // Records a term into recent searches (max 5, deduped, most-recent-first).
    // Call on IME submit or when a search result is actually tapped.
    fun commitSearch(term: String) {
        if (term.isBlank()) return
        recentStore.addRecent(term)
        _recentSearches.value = recentStore.getRecent()
    }
}
