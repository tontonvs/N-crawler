package com.noven.ncrawler.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadStatus
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.db.ReadingProgress
import com.noven.ncrawler.data.local.RecentSearchStore
import com.noven.ncrawler.data.repository.SearchSection
import com.noven.ncrawler.data.repository.SectionState
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

    // CHANGE (grouped search): one section per enabled source, in priority order.
    // Empty list = nothing to show yet (blank query, or the 350ms debounce).
    private val _searchSections = MutableStateFlow<List<SearchSection>>(emptyList())
    val searchSections: StateFlow<List<SearchSection>> = _searchSections.asStateFlow()

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

    // CHANGE (top-bar download icon): true while ANY novel is actively DOWNLOADING.
    // Drives the animated download / arrow icons in the Browse top bar. Only the
    // DOWNLOADING status counts: a QUEUED download that is waiting for Wi-Fi isn't
    // moving, so its icon stays still. Reuses the flow the Downloads screen already
    // observes — no new query. A row stuck on DOWNLOADING after the app was killed is
    // reset to PAUSED at startup by repo.reconcileDownloads().
    // Disadvantage: re-evaluated on every progress write (once per chapter), but
    // distinctUntilChanged() means the UI only hears about true <-> false flips.
    val isDownloading: StateFlow<Boolean> =
        repo.allDownloadProgressFlow()
            .map { rows -> rows.any { it.status == DownloadStatus.DOWNLOADING } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // CHANGE (top-bar progress ring): overall progress 0f..1f of everything that is
    // DOWNLOADING right now = chapters saved / chapters requested, summed across the
    // active downloads (so two concurrent novels read as one combined ring). 0f when
    // nothing is active. Same flow as isDownloading, still one cheap pass per write.
    // Disadvantage: when several novels download, a short one finishing makes the
    // combined fraction dip a little; the ring animates that smoothly.
    val downloadFraction: StateFlow<Float> =
        repo.allDownloadProgressFlow()
            .map { rows ->
                val active = rows.filter { it.status == DownloadStatus.DOWNLOADING }
                val total  = active.sumOf { it.totalChapters }
                if (total <= 0) 0f
                else (active.sumOf { it.downloadedChapters }.toFloat() / total).coerceIn(0f, 1f)
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Pull-to-refresh: true while a silent reload (content stays on screen) runs.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One-shot "refresh failed" signal for a toast (state stays Success).
    private val _refreshFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    private var searchJob: Job? = null
    private var homepageJobs: List<Job> = emptyList()

    init {
        Log.d(TAG, "BrowseViewModel created — calling loadHomepage()")
        loadHomepage()
        observeContinueReading()
        observeSourceChanges()
    }

    // Auto-refresh after switching source. Home is fed by the top source only,
    // so it reloads (with the skeleton — it's a different feed) when that
    // changes; search spans every enabled source, so ANY change re-runs an
    // active search instead of leaving the old source's results on screen.
    private fun observeSourceChanges() {
        viewModelScope.launch {
            var lastTop = repo.sourcePreferences().getPriorityOrder().firstOrNull()
            repo.sourcePreferences().orderFlow()
                .distinctUntilChanged()
                .drop(1)                         // the initial value is loaded by init
                .collect { order ->
                    if (order.firstOrNull() != lastTop) {
                        lastTop = order.firstOrNull()
                        loadHomepage()
                    }
                    val q = _query.value
                    if (q.isNotBlank()) onQueryChange(q)
                }
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

    fun loadHomepage() = load(silent = false)

    // Pull-to-refresh: reload both rows but keep what's on screen meanwhile.
    fun refresh() = load(silent = true)

    private fun load(silent: Boolean) {
        // A newer load supersedes any still-running one, so a slow response from
        // the previous source can't overwrite the new source's feed.
        homepageJobs.forEach { it.cancel() }
        if (!silent) _isRefreshing.value = false
        val latestJob = viewModelScope.launch {
            Log.d(TAG, "loadHomepage(silent=$silent) started")
            if (!silent) _browseState.value = BrowseUiState.Loading
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
                if (silent && _browseState.value is BrowseUiState.Success) _refreshFailed.tryEmit(Unit)
                else _browseState.value = BrowseUiState.Error(friendlyError(e, "Couldn't load"))
            }
        }
        val popularJob = viewModelScope.launch {
            Log.d(TAG, "loadPopular() started")
            if (!silent) _popularState.value = BrowseUiState.Loading
            try {
                val novels = repo.fetchPopular()
                Log.d(TAG, "fetchPopular() returned ${novels.size} novels")
                _popularState.value = if (novels.isEmpty()) BrowseUiState.Empty
                                       else BrowseUiState.Success(novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchPopular() FAILED: ${e.message}", e)
                if (!(silent && _popularState.value is BrowseUiState.Success)) {
                    _popularState.value = BrowseUiState.Error(friendlyError(e, "Couldn't load"))
                }
            }
        }
        val jobs = listOf(latestJob, popularJob)
        homepageJobs = jobs

        if (silent) {
            _isRefreshing.value = true
            viewModelScope.launch {
                jobs.joinAll()
                // Only the newest load may switch the indicator off.
                if (homepageJobs === jobs) _isRefreshing.value = false
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _searchSections.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            // Sections of the previous query stay on screen (dimmed by the UI via their
            // LOADING state) until each source's new answer arrives — refining a query
            // never blanks the list.
            val before = _searchSections.value
            try {
                Log.d(TAG, "search('$q') started")
                repo.searchBySource(q).collect { fresh ->
                    _searchSections.value = fresh.map { section ->
                        if (section.state != SectionState.LOADING) section
                        else before.firstOrNull { it.sourceId == section.sourceId }
                            ?.copy(state = SectionState.LOADING) ?: section
                    }
                }
            } catch (e: CancellationException) {
                throw e   // a newer keystroke replaced this search
            } catch (e: Exception) {
                Log.e(TAG, "search('$q') FAILED: ${e.message}", e)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _searchSections.value = emptyList()
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
