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

    // Separate "most popular" sort — a genuinely different source (/sort/most-popular)
    // from the "latest release" one above, not just a slice of the same list.
    private val _popularState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val popularState: StateFlow<BrowseUiState> = _popularState.asStateFlow()

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

    // ── CHANGE: recentlyReading — powers the new "Recently Read" cards row ──
    // Everything the user has read EXCEPT the single most-recent one (that's
    // already the hero banner above it), newest-first, capped at 12 so the
    // row stays a horizontal scroll rather than a wall.
    //
    // Advantage : reuses the same allReadingProgressFlow() the hero already
    //   observes — no new DB query, no new DAO method needed.
    // Disadvantage: repo.getNovel() runs once per item inside the map — for
    //   12 items that's 12 sequential Room lookups on each emission. Fine at
    //   this scale (single-digit ms each); would need batching past ~50 items.
    val recentlyReading: StateFlow<List<ContinueReadingInfo>> =
        repo.allReadingProgressFlow()
            .map { all ->
                all.drop(1)
                    .take(12)
                    // FIX: was repo.getNovel(), which can hit the NETWORK inside this flow
                    // (and crash the collector when offline). DB-only lookup instead.
                    .mapNotNull { p -> repo.getCachedNovel(p.novelSlug)?.let { ContinueReadingInfo(it, p) } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var homepageJobs: List<Job> = emptyList()

    init {
        Log.d(TAG, "BrowseViewModel created — calling loadHomepage()")
        loadHomepage()
        observeContinueReading()
        observeSourceChanges()
    }

    // FIX: Home only loaded once per app session, so changing the top-priority
    // source in Settings left the old source's feed on screen until a restart.
    private fun observeSourceChanges() {
        viewModelScope.launch {
            repo.sourcePreferences().orderFlow()
                .map { it.firstOrNull() }        // Home is fed by the top source only
                .distinctUntilChanged()
                .drop(1)                         // the initial value is loaded by init
                .collect { loadHomepage() }
        }
    }

    private fun observeContinueReading() {
        viewModelScope.launch {
            repo.allReadingProgressFlow()
                .map { it.firstOrNull() }   // already ordered by lastReadAt DESC
                .distinctUntilChanged()
                .collectLatest { progress ->
                    _continueReading.value = progress?.let { p ->
                        repo.getCachedNovel(p.novelSlug)?.let { novel -> ContinueReadingInfo(novel, p) }
                    }
                }
        }
    }

    fun loadHomepage() {
        // A newer load supersedes any still-running one, so a slow response from
        // the previous source can't overwrite the new source's feed.
        homepageJobs.forEach { it.cancel() }
        val latestJob = viewModelScope.launch {
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
            } catch (e: CancellationException) {
                throw e   // superseded by a newer load — must not surface as an error
            } catch (e: Exception) {
                Log.e(TAG, "fetchHomepage() FAILED: ${e::class.simpleName}: ${e.message}", e)
                _browseState.value = BrowseUiState.Error(
                    "${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                )
            }
        }
        val popularJob = viewModelScope.launch {
            Log.d(TAG, "loadPopular() started")
            _popularState.value = BrowseUiState.Loading
            try {
                val novels = repo.fetchPopular()
                Log.d(TAG, "fetchPopular() returned ${novels.size} novels")
                _popularState.value = if (novels.isEmpty()) BrowseUiState.Empty
                                       else BrowseUiState.Success(novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchPopular() FAILED: ${e.message}", e)
                _popularState.value = BrowseUiState.Error(e.message ?: "Failed to load")
            }
        }
        homepageJobs = listOf(latestJob, popularJob)
    }

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _searchState.value = BrowseUiState.Empty; return }
        // FIX: the state stayed Empty through the 350ms debounce, so the first
        // keystroke flashed "No results". Show loading straight away instead
        // (leave existing results on screen while refining a query).
        if (_searchState.value !is BrowseUiState.Success) _searchState.value = BrowseUiState.Loading
        searchJob = viewModelScope.launch {
            delay(350)
            _searchState.value = BrowseUiState.Loading
            try {
                Log.d(TAG, "search('$q') started")
                val results = repo.search(q)
                Log.d(TAG, "search('$q') returned ${results.size} results")
                _searchState.value = if (results.isEmpty()) BrowseUiState.Empty
                                     else BrowseUiState.Success(results)
            } catch (e: CancellationException) {
                throw e   // a newer keystroke replaced this search
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

    fun removeRecentSearch(term: String) {
        recentStore.removeRecent(term)
        _recentSearches.value = recentStore.getRecent()
    }
}
