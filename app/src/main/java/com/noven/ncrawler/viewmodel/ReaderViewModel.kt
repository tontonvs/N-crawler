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
import com.noven.ncrawler.data.local.ReaderPrefsStore
import com.noven.ncrawler.data.scraper.ChapterLink
import kotlinx.coroutines.Dispatchers
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

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = (app as NCrawlerApp).repository
    private val prefs = ReaderPrefsStore(app)
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

    private var currentSlug    = ""
    private var currentChapter = 0
    private var swatchesLoadedForSlug = ""

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

        // Swatches + TOC only depend on the novel, not the chapter — skip
        // recomputing them when just moving between chapters of the same book.
        if (slug != swatchesLoadedForSlug) {
            swatchesLoadedForSlug = slug
            loadSwatches(slug)
            loadChapterList(slug)
        }
    }

    private fun loadSwatches(slug: String) {
        viewModelScope.launch {
            val novel = try { repo.getNovel(slug) } catch (e: Exception) { null }
            val cover = novel?.coverUrl.orEmpty()
            _swatches.value = if (cover.isBlank()) defaultSwatches() else computeSwatches(cover)
        }
    }

    private fun loadChapterList(slug: String) {
        viewModelScope.launch {
            _chapterList.value = try { repo.getChapterList(slug) } catch (e: Exception) { emptyList() }
        }
    }

    private suspend fun computeSwatches(coverUrl: String): List<ReaderSwatch> =
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
                if (bitmap != null) deriveSwatches(extractDominant(bitmap)) else defaultSwatches()
            } catch (e: Exception) {
                Log.e(TAG, "computeSwatches failed: ${e.message}", e)
                defaultSwatches()
            }
        }

    private fun extractDominant(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).generate()
        val argb = palette.getDominantColor(ReaderAmber.toArgb())
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

    fun hsl(h: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l)))

    val tinted = ReaderSwatch(
        label      = "Tinted",
        background = hsl(hue, 0.30f, 0.14f),
        foreground = fg,
        accent     = hsl(hue, 0.45f, 0.30f)
    )
    val deep = ReaderSwatch(
        label      = "Deep",
        background = hsl(hue, 0.40f, 0.08f),
        foreground = fg,
        accent     = hsl(hue, 0.50f, 0.20f)
    )
    val alternate = ReaderSwatch(
        label      = "Alternate",
        background = hsl(hue + 40f, 0.32f, 0.11f),
        foreground = fg,
        accent     = hsl(hue + 40f, 0.45f, 0.24f)
    )
    val plainLight = ReaderSwatch("Light", ReaderBgLight, ReaderTextLight, Color(0xFF3C1D18))
    val plainDark  = ReaderSwatch("Dark",  ReaderBgDark,  ReaderTextDark,  Color(0xFF2D2420))

    return listOf(tinted, deep, alternate, plainLight, plainDark)
}
