package com.noven.ncrawler.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.ChapterEntity
import com.noven.ncrawler.data.db.ReaderBookmark
import com.noven.ncrawler.data.local.ReadChaptersStore
import com.noven.ncrawler.data.local.ReadingPositionStore
import com.noven.ncrawler.data.local.ReaderPrefsStore
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(val chapter: ChapterEntity) : ReaderUiState
}

// Left / center / right (literal, not Start/End which would be RTL-relative).
// JUSTIFY was removed — its slot in the settings sheet is now Auto scroll. A
// saved "justify" (ordinal 3) falls back to LEFT via getOrElse below.
enum class ReaderTextAlign { LEFT, CENTER, RIGHT }

// Auto-scroll speed is in dp per second. Slow enough to read a line at a time at
// the bottom, fast enough to skim at the top; the swipe gesture scales it
// smoothly between the two.
const val AUTO_SPEED_MIN = 6f
const val AUTO_SPEED_MAX = 260f
const val AUTO_SPEED_DEFAULT = 24f

// A chapter you only jumped to (TOC, far from where you are) becomes "your
// place" once you have really read into it: past this fraction AND scrolled
// at least COMMIT_MOVE since it opened (so a restored spot alone doesn't count).
private const val COMMIT_FRACTION = 0.25f
private const val COMMIT_MOVE     = 0.03f

data class ReaderSettings(
    val fontSize: Float = 17f,
    val lineHeight: Float = 1.8f,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val brightness: Float = 0f,   // 0..1 — alpha of the screen-dimming overlay
    val swatchIndex: Int = 4,     // default: plain Dark (index 4, last swatch)
    val autoSpeed: Float = AUTO_SPEED_DEFAULT,   // auto-scroll, dp per second
    val autoPilot: Boolean = false               // auto-open the next chapter at the end
)

// One reading background option. Indices 0-2 are derived from the current
// novel's cover (a tinted/deep/alternate-hue trio) so the reader "follows
// the dominant colour from the detail page" — indices 3-4 are the fixed
// plain Light/Dark options, always available regardless of novel.
data class ReaderSwatch(
    val label: String,
    val background: Color,
    val foreground: Color,
    val accent: Color   // chapter title color / active-pill color
)

private val ReaderBgDark    = Color(0xFF0D0C0A)
private val ReaderBgLight   = Color(0xFFF5F0E8)
private val ReaderTextDark  = Color(0xFFE8E4DC)
private val ReaderTextLight = Color(0xFF1A1714)
private val ReaderAccentDefault = Color(0xFF7FB2F0)   // was amber — yellow is reserved for the star rating
// Used only when a cover has no swatch at all — a neutral, so it never invents a tint
private val NeutralDominant = Color(0xFF3A3A3A)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = (app as NCrawlerApp).repository
    private val prefs = ReaderPrefsStore(app)
    private val readStore = ReadChaptersStore(app)
    private val positionStore = ReadingPositionStore(app)
    private val TAG   = "NCrawler_Reader"

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(
        ReaderSettings(
            fontSize    = prefs.getFontSize(17f),
            lineHeight  = prefs.getLineHeight(1.8f),
            textAlign   = ReaderTextAlign.entries.getOrElse(prefs.getTextAlignOrdinal(0)) { ReaderTextAlign.LEFT },
            brightness  = prefs.getBrightness(0f),
            swatchIndex = prefs.getSwatchIndex(4),
            autoSpeed   = prefs.getAutoSpeed(AUTO_SPEED_DEFAULT).coerceIn(AUTO_SPEED_MIN, AUTO_SPEED_MAX),
            autoPilot   = prefs.getAutoPilot(false)
        )
    )
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _swatches = MutableStateFlow(defaultSwatches())
    val swatches: StateFlow<List<ReaderSwatch>> = _swatches.asStateFlow()

    private val _chapterList = MutableStateFlow<List<ChapterLink>>(emptyList())
    val chapterList: StateFlow<List<ChapterLink>> = _chapterList.asStateFlow()

    // Chapters actually opened in this novel — drives the TOC's "read" checks
    // (individual chapters, not "everything before the current one").
    private val _readChapters = MutableStateFlow<Set<Int>>(emptySet())
    val readChapters: StateFlow<Set<Int>> = _readChapters.asStateFlow()

    // The novel's own title. The reader uses it to tell a real chapter title
    // apart from a scraped heading that is just the novel's name.
    private val _novelTitle = MutableStateFlow("")
    val novelTitle: StateFlow<String> = _novelTitle.asStateFlow()

    private var currentSlug    = ""
    private var currentChapter = 0

    // ── "Your place" vs "the chapter that's open" ────────────────────────────
    // FIX: every chapter that loaded used to overwrite the saved progress, so a
    // quick look at the newest chapter (78) while really on 56 made the novel
    // reopen at 78 forever. Now the saved place only moves when you actually
    // continue from it:
    //   • the chapter is next to your place (previous / same / next), or there is
    //     no place yet, or you opened a bookmark → it becomes your place at once;
    //   • any other jump is a PEEK: the place stays put until you really read
    //     into the peeked chapter (COMMIT_FRACTION), and its spot is saved in a
    //     separate slot so the place's own spot isn't lost either.
    private val _placeChapter = MutableStateFlow(0)
    val placeChapter: StateFlow<Int> = _placeChapter.asStateFlow()
    private var placeLoadedFor = ""
    private var provisional    = false
    private var baseline: Float? = null

    // ── Page bookmarks (one per chapter) ─────────────────────────────────────
    // Follows whichever novel is open; the list is empty until load() sets a slug.
    private val slugFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<ReaderBookmark>> = slugFlow
        .flatMapLatest { slug -> if (slug.isEmpty()) flowOf(emptyList()) else repo.bookmarksFlow(slug) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // One-shot text for the little "Bookmark added" toast.
    private val _bookmarkEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val bookmarkEvent: SharedFlow<String> = _bookmarkEvent.asSharedFlow()

    // Set by jumpToBookmark(), read ONCE by the screen when that chapter has loaded,
    // so it scrolls to the bookmarked spot instead of the last saved reading spot.
    private var pendingJump: Pair<Int, Float>? = null

    // Slug the current swatches were successfully derived from (not the
    // amber defaults) — lets a failed/too-early attempt be retried.
    private var swatchesDerivedFor = ""
    private var swatchJob: Job? = null
    private var listJob: Job? = null

    fun load(slug: String, chapterNum: Int, forceCommit: Boolean = false) {
        val novelChanged = slug != currentSlug
        currentSlug    = slug
        currentChapter = chapterNum
        slugFlow.value = slug
        // A jump only applies to the chapter it was made for; never let a stale one linger.
        if (pendingJump?.first != chapterNum) pendingJump = null

        // FIX (colours from another novel): the moment a different novel is
        // loaded, everything that belongs to the previous one is thrown away —
        // its swatches, chapter list, title and read-set — and its in-flight
        // loads are cancelled. Before, the old novel's swatches stayed on
        // screen until the new ones finished, and a slow earlier load could
        // even finish LAST and overwrite the new novel's colours.
        if (novelChanged) {
            swatchJob?.cancel()
            listJob?.cancel()
            swatchesDerivedFor  = ""
            _swatches.value     = defaultSwatches()
            _chapterList.value  = emptyList()
            _novelTitle.value   = ""
            _readChapters.value = readStore.getRead(slug)
            _placeChapter.value = 0
            placeLoadedFor      = ""
            loadSwatches(slug)
            loadChapterList(slug)
        }

        viewModelScope.launch {
            _state.value = ReaderUiState.Loading
            _state.value = try {
                val chapter = repo.downloadChapter(slug, chapterNum)
                // Decide whether this chapter is "your place" or only a peek
                // (see the note on _placeChapter).
                if (placeLoadedFor != slug) {
                    _placeChapter.value = repo.getReadingProgress(slug)?.lastChapterNum ?: 0
                    placeLoadedFor = slug
                }
                val place      = _placeChapter.value
                val sequential = forceCommit || place == 0 || chapterNum in (place - 1)..(place + 1)
                if (sequential) {
                    commitPlace(slug, chapterNum, chapter.title)
                } else {
                    provisional = true
                    baseline    = null
                }
                // Opening a chapter marks it read
                _readChapters.value = readStore.markRead(slug, chapterNum)

                // Retry anything the first attempt missed (e.g. the novel row
                // wasn't in the DB yet, so there was no cover to derive from).
                if (slug == currentSlug) {
                    if (swatchesDerivedFor != slug && swatchJob?.isActive != true) loadSwatches(slug)
                    if (_chapterList.value.isEmpty() && listJob?.isActive != true) loadChapterList(slug)
                }
                ReaderUiState.Success(chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderUiState.Error(friendlyError(e, "Couldn't load chapter"))
            }
        }
    }

    private suspend fun commitPlace(slug: String, chapterNum: Int, title: String) {
        provisional = false
        baseline    = null
        repo.saveReadingProgress(slug, chapterNum, title)
        _placeChapter.value = chapterNum
    }

    private fun loadSwatches(slug: String) {
        swatchJob?.cancel()
        swatchJob = viewModelScope.launch {
            val novel = try {
                repo.getNovel(slug)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (slug != currentSlug) return@launch          // user moved on
            _novelTitle.value = novel?.title.orEmpty()

            val cover = novel?.coverUrl.orEmpty()
            if (cover.isBlank()) return@launch              // keep defaults; retried after the chapter loads

            val derived = computeSwatches(cover)
            // Only apply if this is still the novel being read
            if (derived != null && slug == currentSlug) {
                _swatches.value    = derived
                swatchesDerivedFor = slug
            }
        }
    }

    private fun loadChapterList(slug: String) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val list = try {
                repo.getChapterList(slug)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (slug == currentSlug) _chapterList.value = list
        }
    }

    // null = couldn't derive (network/decoding failure) — the caller keeps the
    // defaults and can retry, instead of treating defaults as a "result".
    private suspend fun computeSwatches(coverUrl: String): List<ReaderSwatch>? =
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    // Palette needs a software bitmap — same fix as the
                    // detail-screen cover crash (hardware bitmaps throw).
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    Log.w(TAG, "computeSwatches: no bitmap for $coverUrl")
                    null
                } else {
                    val dominant = extractDominant(bitmap)
                    Log.d(TAG, "swatches: dominant=#${Integer.toHexString(dominant.toArgb())} cover=$coverUrl")
                    deriveSwatches(dominant)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "computeSwatches failed: ${e.message}", e)
                null
            }
        }

    // FIX (reader stuck on red): the previous version scored swatches by
    // population × saturation, which favoured small, very saturated accents
    // (blood-red titles, fire, glows) over the cover's real dominant colour, so
    // a lot of covers ended up with a red tint. Back to "dominant" meaning
    // dominant: Palette's own dominant swatch first (its default filter already
    // drops near-black, near-white and the skin-tone hue line), then the next
    // most populous ones — but only among the top four, so a tiny accent can
    // never win. A candidate must carry a real hue (saturation ≥ 0.15) and not
    // be near-black/near-white; if none does, the cover is effectively neutral
    // and deriveSwatches keeps the family near-neutral instead of inventing a tint.
    private fun extractDominant(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).generate()
        val hsl = FloatArray(3)

        fun usable(rgb: Int): Boolean {
            ColorUtils.colorToHSL(rgb, hsl)
            return hsl[1] >= 0.15f && hsl[2] in 0.10f..0.92f
        }

        val candidates = (listOfNotNull(palette.dominantSwatch) +
                palette.swatches.sortedByDescending { it.population })
            .distinct()
            .take(4)
        val pick = candidates.firstOrNull { usable(it.rgb) }

        Log.d(
            TAG,
            "dominant candidates: " + candidates.joinToString {
                "#%06X x%d".format(it.rgb and 0xFFFFFF, it.population)
            } + " -> picked " + (pick?.let { "#%06X".format(it.rgb and 0xFFFFFF) } ?: "none (neutral)")
        )

        return Color(pick?.rgb ?: palette.getDominantColor(NeutralDominant.toArgb()))
    }

    // Where in [chapterNum] the reader stopped (0..1), or null if there's nothing
    // saved for that chapter. Read BEFORE the screen scrolls to the top on load —
    // a fresh chapter's position 0 must never overwrite it.
    fun savedFraction(chapterNum: Int): Float? = positionStore.get(currentSlug, chapterNum)

    // Remember the reading spot (fraction of the chapter) for the chapter that is
    // currently open.
    fun saveReadingFraction(fraction: Float) {
        if (currentSlug.isEmpty() || currentChapter <= 0) return
        if (!provisional) {
            positionStore.save(currentSlug, currentChapter, fraction)
            return
        }
        // A peeked chapter: its spot goes in the peek slot (the place's own spot
        // stays safe) until the reader has really read into it.
        val base = baseline ?: fraction.also { baseline = it }
        if (fraction >= COMMIT_FRACTION && fraction - base >= COMMIT_MOVE) {
            val slug  = currentSlug
            val num   = currentChapter
            val title = (state.value as? ReaderUiState.Success)?.chapter?.title ?: ""
            provisional = false          // synchronously, so a second call can't commit twice
            baseline    = null
            positionStore.save(slug, num, fraction)
            viewModelScope.launch { commitPlace(slug, num, title) }
        } else {
            positionStore.savePeek(currentSlug, currentChapter, fraction)
        }
    }

    fun saveScrollPosition(scrollPos: Int) {
        // A peek must not touch the saved place (this call rewrites the progress row).
        if (provisional) return
        viewModelScope.launch {
            repo.saveReadingProgress(
                slug         = currentSlug,
                chapterNum   = currentChapter,
                chapterTitle = (state.value as? ReaderUiState.Success)?.chapter?.title ?: "",
                scrollPos    = scrollPos
            )
        }
    }

    /** Adds a bookmark at [fraction] of the open chapter, or removes the chapter's bookmark. */
    fun toggleBookmark(fraction: Float, chapterTitle: String) {
        val slug = currentSlug
        val num  = currentChapter
        if (slug.isEmpty() || num <= 0) return
        viewModelScope.launch {
            val added = repo.toggleBookmark(slug, num, chapterTitle, fraction)
            _bookmarkEvent.tryEmit(if (added) "Bookmark added" else "Bookmark removed")
        }
    }

    fun removeBookmark(chapterNum: Int) {
        val slug = currentSlug
        viewModelScope.launch { repo.removeBookmark(slug, chapterNum) }
    }

    /** Opens a bookmark: loads its chapter and scrolls to the saved spot. */
    fun jumpToBookmark(bookmark: ReaderBookmark) {
        pendingJump = bookmark.chapterNum to bookmark.fraction
        // A bookmark is a deliberate "this is my spot" — it becomes the place at once.
        load(currentSlug, bookmark.chapterNum, forceCommit = true)
    }

    /** The bookmarked spot to scroll to for [chapterNum], consumed on read; null if none. */
    fun takePendingJump(chapterNum: Int): Float? {
        val jump = pendingJump ?: return null
        if (jump.first != chapterNum) return null
        pendingJump = null
        return jump.second
    }

    /**
     * Fetches a chapter WITHOUT opening it (no state change, no place / read
     * bookkeeping) — used by auto-pilot to have the next chapter ready to flow in
     * under the current one. null if it couldn't be loaded.
     */
    suspend fun fetchChapter(chapterNum: Int): ChapterEntity? {
        val slug = currentSlug
        if (slug.isEmpty() || chapterNum <= 0) return null
        return try {
            repo.downloadChapter(slug, chapterNum)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchChapter($chapterNum) failed: ${e.message}")
            null
        }
    }

    /**
     * Makes an already-fetched chapter the open one with NO Loading step — the
     * screen has already swapped it in under the reader's eyes (continuous
     * auto-pilot). Does the same bookkeeping load() does: the chapter just
     * finished counts as fully read, the new one is marked read, and it becomes
     * "your place" (it is always the next chapter, so it is sequential).
     */
    fun advanceTo(chapter: ChapterEntity, chapterNum: Int) {
        val slug = currentSlug
        if (slug.isEmpty() || chapterNum != currentChapter + 1) return
        positionStore.save(slug, currentChapter, 1f)       // the one we just finished
        currentChapter = chapterNum
        pendingJump    = null
        provisional    = false
        baseline       = null
        _readChapters.value = readStore.markRead(slug, chapterNum)
        _state.value   = ReaderUiState.Success(chapter)
        viewModelScope.launch { commitPlace(slug, chapterNum, chapter.title) }
    }

    fun loadNext() = load(currentSlug, currentChapter + 1)
    fun loadPrev() { if (currentChapter > 1) load(currentSlug, currentChapter - 1) }
    fun jumpTo(chapterNum: Int) = load(currentSlug, chapterNum)

    fun increaseFontSize() = updateSettings { it.copy(fontSize = (it.fontSize + 1f).coerceAtMost(28f)) }
    fun decreaseFontSize() = updateSettings { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(12f)) }
    fun setTextAlign(align: ReaderTextAlign) = updateSettings { it.copy(textAlign = align) }
    fun setBrightness(value: Float) = updateSettings { it.copy(brightness = value.coerceIn(0f, 1f)) }
    fun selectSwatch(index: Int) = updateSettings { it.copy(swatchIndex = index) }
    fun setAutoPilot(on: Boolean) = updateSettings { it.copy(autoPilot = on) }

    // Called when a swipe ends / auto-scroll stops — not on every drag event.
    fun saveAutoSpeed(speed: Float) =
        updateSettings { it.copy(autoSpeed = speed.coerceIn(AUTO_SPEED_MIN, AUTO_SPEED_MAX)) }

    private inline fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.save(
            fontSize         = updated.fontSize,
            lineHeight       = updated.lineHeight,
            textAlignOrdinal = updated.textAlign.ordinal,
            brightness       = updated.brightness,
            swatchIndex      = updated.swatchIndex,
            autoSpeed        = updated.autoSpeed,
            autoPilot        = updated.autoPilot
        )
    }

    val currentChapterNum get() = currentChapter
}

// Fallback set when there's no cover to derive from yet — two plain options
// plus one amber-tinted "Tinted" placeholder so the swatch row isn't empty.
private fun defaultSwatches(): List<ReaderSwatch> = listOf(
    ReaderSwatch("Tinted", Color(0xFF141C26), Color(0xFFE4E8EC), ReaderAccentDefault),
    ReaderSwatch("Deep",   Color(0xFF0E141C), Color(0xFFE4E8EC), Color(0xFF5B8FD6)),
    ReaderSwatch("Alternate", Color(0xFF14181F), Color(0xFFE8E4DC), Color(0xFF4F6D8C)),
    ReaderSwatch("Light",  ReaderBgLight, ReaderTextLight, Color(0xFF3C1D18)),
    ReaderSwatch("Dark",   ReaderBgDark,  ReaderTextDark,  Color(0xFF2D2420))
)

// Derives a 5-swatch family from one dominant cover color: 3 reading-safe
// dark tints sharing (or near) the novel's hue, plus the 2 fixed plain
// options. All tinted backgrounds are kept dark/desaturated on purpose —
// a raw saturated brand color makes a poor reading background, so this
// trades saturation/lightness for legibility while keeping the hue.
private fun deriveSwatches(dominant: Color): List<ReaderSwatch> {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(dominant.toArgb(), hsl)
    val hue = hsl[0]
    val fg  = Color(0xFFE8E4DC)
    // FIX: a grey/black/white source has no real hue, so inventing a strongly
    // saturated tint from it made unrelated novels look identical. Keep the
    // family near-neutral in that case.
    val k = if (hsl[1] < 0.12f) 0.2f else 1f

    fun hsl(h: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l)))

    val tinted = ReaderSwatch(
        label      = "Tinted",
        background = hsl(hue, 0.30f * k, 0.14f),
        foreground = fg,
        accent     = hsl(hue, 0.45f * k, 0.30f)
    )
    val deep = ReaderSwatch(
        label      = "Deep",
        background = hsl(hue, 0.40f * k, 0.08f),
        foreground = fg,
        accent     = hsl(hue, 0.50f * k, 0.20f)
    )
    val alternate = ReaderSwatch(
        label      = "Alternate",
        background = hsl(hue + 40f, 0.32f * k, 0.11f),
        foreground = fg,
        accent     = hsl(hue + 40f, 0.45f * k, 0.24f)
    )
    val plainLight = ReaderSwatch("Light", ReaderBgLight, ReaderTextLight, Color(0xFF3C1D18))
    val plainDark  = ReaderSwatch("Dark",  ReaderBgDark,  ReaderTextDark,  Color(0xFF2D2420))

    return listOf(tinted, deep, alternate, plainLight, plainDark)
}
