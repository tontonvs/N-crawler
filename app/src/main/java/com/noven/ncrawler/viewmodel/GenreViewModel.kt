package com.noven.ncrawler.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.NovelEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class GenreUiState(
    val genre: String = "",
    val novels: List<NovelEntity> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,   // pull-to-refresh — list stays on screen
    val hasMore: Boolean = true,
    val error: String? = null
)

// Keeps this simple, as asked: one genre, one flat infinite-scrolling list.
// No sort toggle, no local cache beyond what NovelRepository already does —
// same pattern the "See more" links and the Discover tab's genre tiles both
// land on. Mirrors DetailViewModel's plain load(param) pattern rather than
// SavedStateHandle, since nothing else in this app uses that.
class GenreViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository
    private val TAG  = "NCrawler_Genre"

    private val _state = MutableStateFlow(GenreUiState())
    val state: StateFlow<GenreUiState> = _state.asStateFlow()

    private var currentGenre = ""

    private val _refreshFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFailed: SharedFlow<Unit> = _refreshFailed.asSharedFlow()

    init {
        // Auto-refresh after switching source: a genre list belongs to one
        // source, so reload it when the source order changes.
        viewModelScope.launch {
            repo.sourcePreferences().orderFlow()
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val genre = currentGenre
                    if (genre.isNotBlank()) {
                        currentGenre = ""     // defeat load()'s "already loaded" early-return
                        load(genre)
                    }
                }
        }
    }

    // Pull-to-refresh: re-fetch page 1 but keep the current list visible.
    fun refresh() {
        val genre = currentGenre
        if (genre.isBlank() || _state.value.isLoading || _state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            try {
                val novels = repo.fetchGenre(genre, page = 1)
                _state.value = _state.value.copy(
                    novels       = novels,
                    page         = 1,
                    isRefreshing = false,
                    hasMore      = novels.isNotEmpty(),
                    error        = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refresh('$genre') failed: ${e.message}", e)
                _state.value = _state.value.copy(isRefreshing = false)
                _refreshFailed.tryEmit(Unit)
            }
        }
    }

    fun load(genre: String) {
        if (genre == currentGenre && _state.value.novels.isNotEmpty()) return
        currentGenre = genre
        viewModelScope.launch {
            _state.value = GenreUiState(genre = genre, isLoading = true)
            try {
                val novels = repo.fetchGenre(genre, page = 1)
                Log.d(TAG, "genre='$genre' page=1 returned ${novels.size} novels")
                _state.value = _state.value.copy(
                    novels    = novels,
                    page      = 1,
                    isLoading = false,
                    hasMore   = novels.isNotEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "load('$genre') failed: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error     = friendlyError(e, "Couldn't load")
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore || current.isLoading) return

        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true)
            val nextPage = current.page + 1
            try {
                val more = repo.fetchGenre(current.genre, page = nextPage)
                Log.d(TAG, "genre='${current.genre}' page=$nextPage returned ${more.size} novels")
                _state.value = _state.value.copy(
                    novels        = current.novels + more,
                    page          = nextPage,
                    isLoadingMore = false,
                    hasMore       = more.isNotEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadNextPage('${current.genre}', page=$nextPage) failed: ${e.message}", e)
                // Don't surface a full-screen error for a failed "load more" —
                // just stop paging so the person keeps what already loaded.
                _state.value = _state.value.copy(isLoadingMore = false, hasMore = false)
            }
        }
    }
}
