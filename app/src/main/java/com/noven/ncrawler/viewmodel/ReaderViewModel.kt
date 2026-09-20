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
import com.noven.ncrawler.data.local.ReadChaptersStore
import com.noven.ncrawler.data.local.ReaderPrefsStore
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(val chapter: ChapterEntity) : ReaderUiState
}

// Mirrors the mockup's 4 left/center/right/justify options exactly (not
// Start/End, which would be RTL-relative — the mockup is literal L/C/R/J).
enum class ReaderTextAlign { LEFT, CENTER, RIGHT, JUSTIFY }

data class ReaderSettings(
    val fontSize: Float = 17f,
    val lineHeight: Float = 1.8f,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val brightness: Float = 0f,   // 0..1 — alpha of the screen-dimming overlay
    val swatchIndex: Int = 4      // default: plain Dark (index 4, last swatch)
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
private val ReaderAmber     = Color(0xFFFFCA28)
// Used only when a cover has no swatch at all — a neutral, so it never invents a tint
private val NeutralDominant = Color(0xFF3A3A3A)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = (app as NCrawlerApp).repository
    private val prefs = ReaderPrefsStore(app)
    private val readStore = ReadChaptersStore(app)
    private val TAG   = "NCrawler_Reader"

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(
        ReaderSettings(
            fontSize    = prefs.getFontSize(17f),
            lineHeight  = prefs.getLineHeight(1.8f),
            textAlign   = ReaderTextAlign.entries.getOrElse(prefs.getTextAlignOrdinal(0)) { ReaderTextAlign.LEFT },
            brightness  = prefs.getBrightness(0f),
            swatchIndex = prefs.getSwatchIndex(4)
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

    // Slug the current swatches were successfully derived from (not the
    // amber defaults) — lets a failed/too-early attempt be retried.
    private var swatchesDerivedFor = ""
    private var swatchJob: Job? = null
    private var listJob: Job? = null

    fun load(slug: String, chapterNum: Int) {
        val novelChanged = slug != currentSlug
        currentSlug    = slug
        currentChapter = chapterNum

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
            loadSwatches(slug)
            loadChapterList(slug)
        }

        viewModelScope.launch {
            _state.value = ReaderUiState.Loading
            _state.value = try {
                val chapter = repo.downloadChapter(slug, chapterNum)
                // Auto-save reading progress whenever a chapter loads
                repo.saveReadingProgress(slug, chapterNum, chapter.title)
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
                ReaderUiState.Error(e.message ?: "Failed to load chapter")
            }
        }
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

    // FIX: pick the most COLOURFUL meaningful swatch, not just the biggest one.
    // Greys / near-black / near-white swatches carry no hue, and the hue that
    // falls out of them is arbitrary — it tinted every dark or white-bordered
    // cover the same way. Population still counts; saturation weights it.
    // Falls back to the plain dominant colour (→ near-neutral swatches).
    private fun extractDominant(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).maximumColorCount(24).generate()
        val hsl = FloatArray(3)
        var best: Palette.Swatch? = null
        var bestScore = 0f
        for (swatch in palette.swatches) {
            ColorUtils.colorToHSL(swatch.rgb, hsl)
            if (hsl[1] < 0.18f || hsl[2] < 0.12f || hsl[2] > 0.90f) continue
            val score = swatch.population * (0.4f + hsl[1])
            if (score > bestScore) {
                bestScore = score
                best = swatch
            }
        }
        val argb = best?.rgb ?: palette.getDominantColor(NeutralDominant.toArgb())
        return Color(argb)
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
    fun jumpTo(chapterNum: Int) = load(currentSlug, chapterNum)

    fun increaseFontSize() = updateSettings { it.copy(fontSize = (it.fontSize + 1f).coerceAtMost(28f)) }
    fun decreaseFontSize() = updateSettings { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(12f)) }
    fun setTextAlign(align: ReaderTextAlign) = updateSettings { it.copy(textAlign = align) }
    fun setBrightness(value: Float) = updateSettings { it.copy(brightness = value.coerceIn(0f, 1f)) }
    fun selectSwatch(index: Int) = updateSettings { it.copy(swatchIndex = index) }

    private inline fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.save(
            fontSize         = updated.fontSize,
            lineHeight       = updated.lineHeight,
            textAlignOrdinal = updated.textAlign.ordinal,
            brightness       = updated.brightness,
            swatchIndex      = updated.swatchIndex
        )
    }

    val currentChapterNum get() = currentChapter
}

// Fallback set when there's no cover to derive from yet — two plain options
// plus one amber-tinted "Tinted" placeholder so the swatch row isn't empty.
private fun defaultSwatches(): List<ReaderSwatch> = listOf(
    ReaderSwatch("Tinted", Color(0xFF241E14), Color(0xFFE8E4DC), ReaderAmber),
    ReaderSwatch("Deep",   Color(0xFF17130D), Color(0xFFE8E4DC), Color(0xFFB8860B)),
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
