package com.noven.ncrawler.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.delay
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

// Skeleton hold times. Opening a novel: the skeleton shows until the metadata is in,
// and at least this long so a cached open doesn't flash it for one frame.
// Refresh: the skeleton always shows for at least this long (or until the update
// check finishes, whichever is later).
private const val OPEN_SKELETON_MIN_MS    = 700L
private const val REFRESH_SKELETON_MIN_MS = 1600L
// Gap between the heart animation starting and the "Added to favourites" notice.
private const val HEART_NOTICE_DELAY_MS   = 700L

// CHANGE (updates): the unread new-chapter range found by an update check (inclusive).
data class NewRange(val from: Int, val to: Int) {
    val size: Int get() = to - from + 1
}

// "Downloading <novel>" banner on the Detail screen. `id` makes every tap a new
// event, so tapping again re-shows the banner even if the title is the same.
data class DownloadNotice(val novelTitle: String, val id: Long)

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    // Drives the heart (favourite) button. Stored in the same isInLibrary column.
    private val _inLibrary = MutableStateFlow(false)
    val inLibrary: StateFlow<Boolean> = _inLibrary.asStateFlow()

    // Chapter numbers saved on this device — drives the downloaded ticks in the
    // chapter list and the "Missing only" option in the download sheet.
    private val _downloadedNums = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedNums: StateFlow<Set<Int>> = _downloadedNums.asStateFlow()

    // True while the refresh button's update check runs — drives the animated
    // refresh icon and the skeleton over the Detail layout.
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // CHANGE (favourites): fires when a novel is hearted — the screen plays the
    // big heart pop, and the "Added to favourites" notice follows it.
    private val _favouriteBurst = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val favouriteBurst: SharedFlow<Unit> = _favouriteBurst.asSharedFlow()

    private val _downloadNotice = MutableStateFlow<DownloadNotice?>(null)
    val downloadNotice: StateFlow<DownloadNotice?> = _downloadNotice.asStateFlow()

    // Pending new chapters for this novel (null = none). Fed by the novel row, so a
    // background check, a refresh, reading and dismissing all update it live.
    private val _newRange = MutableStateFlow<NewRange?>(null)
    val newRange: StateFlow<NewRange?> = _newRange.asStateFlow()

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
            val openedAt = SystemClock.elapsedRealtime()

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

            val held = SystemClock.elapsedRealtime() - openedAt
            if (held < OPEN_SKELETON_MIN_MS) delay(OPEN_SKELETON_MIN_MS - held)

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
                val chapters = loadChapters(slug)
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

        viewModelScope.launch {
            repo.novelFlow(slug).collect { n ->
                _newRange.value =
                    if (n != null && n.newToChapter > 0 && n.newToChapter >= n.newFromChapter)
                        NewRange(n.newFromChapter, n.newToChapter)
                    else null
            }
        }

        // The table changes on every saved chapter (several a second while
        // downloading). conflate() + a short pause keeps the UI to ~1 refresh
        // a second instead of re-drawing the chapter list for each one.
        viewModelScope.launch {
            repo.downloadedNumsFlow(slug).conflate().collect {
                _downloadedNums.value = it.toHashSet()
                delay(750)
            }
        }
    }

    // The cached chapter list only knows "Chapter N" for most rows. Where the
    // chapter is downloaded we know its real title — use it instead.
    private fun ChapterLink.isPlaceholder() =
        title.isBlank() || title.trim().equals("Chapter $num", ignoreCase = true)

    private suspend fun loadChapters(slug: String): List<ChapterLink> {
        val raw = repo.getChapterList(slug)
        val titles = try { repo.downloadedChapterTitles(slug) } catch (e: Exception) { emptyMap() }
        if (titles.isEmpty()) return raw
        return raw.map { c ->
            if (!c.isPlaceholder()) c
            else titles[c.num]?.takeIf { it.isNotBlank() }?.let { c.copy(title = it) } ?: c
        }
    }

    private fun announceDownload() {
        val title = (_state.value as? DetailUiState.Success)?.novel?.title ?: return
        _downloadNotice.value = DownloadNotice(title, SystemClock.elapsedRealtime())
    }

    fun clearDownloadNotice() { _downloadNotice.value = null }

    fun showMessage(message: String) { _updateMessage.value = message }

    fun toggleLibrary() {
        viewModelScope.launch {
            val add = !_inLibrary.value
            repo.setLibrary(currentSlug, add)
            if (add) {
                // Heart animation first (~0.7s), then the notice — like a social-app like.
                _favouriteBurst.tryEmit(Unit)
                delay(HEART_NOTICE_DELAY_MS)
                _updateMessage.value = "Added to favourites"
            } else {
                _updateMessage.value = "Removed from favourites"
            }
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
                announceDownload()
            } catch (e: Exception) {
                _updateMessage.value = "Couldn't start download"
            }
        }
    }

    fun downloadAll() {
        viewModelScope.launch {
            try { repo.queueDownloadAll(currentSlug); announceDownload() }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    // CHANGE (partial downloads): backs the Detail screen's download-options
    // sheet — "Last N chapters" and volume/custom-range picks.
    fun downloadLast(count: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadLast(currentSlug, count); announceDownload() }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    fun downloadRange(startChapter: Int, endChapter: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadRange(currentSlug, startChapter, endChapter); announceDownload() }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    fun downloadFirst(count: Int) {
        viewModelScope.launch {
            try { repo.queueDownloadFirst(currentSlug, count); announceDownload() }
            catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    fun downloadMissing() {
        viewModelScope.launch {
            try {
                if (repo.queueDownloadMissing(currentSlug) == 0) {
                    _updateMessage.value = "Every chapter is already downloaded"
                } else announceDownload()
            } catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    // Multi-select in the chapter list: any set of chapters, adjacent or not.
    fun downloadChapters(nums: Set<Int>) {
        if (nums.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.queueDownloadChapters(currentSlug, nums)
                announceDownload()
                _updateMessage.value = "Queued ${nums.size} chapter${if (nums.size == 1) "" else "s"}"
            } catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    // The speech bubble: download the new chapters that aren't on disk yet.
    fun downloadNewChapters() {
        viewModelScope.launch {
            try {
                val queued = repo.downloadNewChapters(currentSlug)
                if (queued == 0) {
                    _updateMessage.value = "New chapters are already downloaded"
                } else {
                    announceDownload()
                    _updateMessage.value = "Queued $queued new chapter${if (queued == 1) "" else "s"}"
                }
            } catch (e: Exception) { _updateMessage.value = "Couldn't start download" }
        }
    }

    // "Got it": removes the NEW badges and takes the novel out of Library > Updates.
    fun dismissUpdate() {
        viewModelScope.launch { repo.clearUpdate(currentSlug) }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            repo.cancelDownload(currentSlug)
        }
    }

    // Refresh: the chapters already loaded stay in memory (and in the DB cache);
    // the check only adds what's new. While it runs the screen shows the skeleton
    // for at least REFRESH_SKELETON_MIN_MS.
    fun checkForUpdates() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val startedAt = SystemClock.elapsedRealtime()
            var message: String
            try {
                val newCount = repo.checkForUpdates(currentSlug)

                // Pick up the re-saved novel (author, status, cover) and the
                // fresh chapter list.
                val chapters = loadChapters(currentSlug)
                val novel    = repo.getCachedNovel(currentSlug)
                (_state.value as? DetailUiState.Success)?.let {
                    _state.value = it.copy(novel = novel ?: it.novel, chapters = chapters)
                }
                // Nothing is downloaded automatically any more — the bubble offers it.
                message = if (newCount <= 0) "Already up to date"
                          else "$newCount new chapter${if (newCount == 1) "" else "s"}"
            } catch (e: Exception) {
                message = "Couldn't check updates"
            }

            val held = SystemClock.elapsedRealtime() - startedAt
            if (held < REFRESH_SKELETON_MIN_MS) delay(REFRESH_SKELETON_MIN_MS - held)
            _refreshing.value = false
            _updateMessage.value = message
        }
    }

    fun clearUpdateMessage() { _updateMessage.value = null }
}
