package com.noven.ncrawler.viewmodel

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Chapter model (lightweight for detail list) ──────────────────────────────

data class ChapterItem(
    val title: String,
    val url: String,
)

// ─── UI state ─────────────────────────────────────────────────────────────────

data class NovelDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val authors: String = "",
    val coverUrl: String? = null,
    val status: String = "",       // "Ongoing" | "Complete"
    val genre: String = "",        // e.g. "Wuxia"
    val latestChapter: String = "", // e.g. "Ch. 1902"
    val rating: Float = 0f,
    val summary: String = "",
    val chapters: List<ChapterItem> = emptyList(),
    val error: String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class NovelDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NovelDetailUiState())
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    // Palette colours extracted from the cover art bitmap
    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    private val _vibrantColor = MutableStateFlow<Color?>(null)
    val vibrantColor: StateFlow<Color?> = _vibrantColor.asStateFlow()

    /**
     * Load novel detail from the repository/scraper.
     *
     * TODO: wire up to NovelRepository once detail scraping is implemented
     * (Sprint 2). For now this emits placeholder data so the UI can be
     * previewed immediately.
     */
    fun loadDetail(novelId: String) {
        viewModelScope.launch {
            _uiState.value = NovelDetailUiState(isLoading = true)

            // ── Replace this block with a real repository call in Sprint 2 ──
            withContext(Dispatchers.IO) {
                // Simulate network delay for design preview
                kotlinx.coroutines.delay(800)
            }

            // Placeholder state — swap with repo result
            _uiState.value = NovelDetailUiState(
                isLoading = false,
                title = novelId.replace("-", " ").replaceFirstChar { it.uppercaseChar() },
                authors = "Author Name",
                coverUrl = null, // repo will supply the real URL
                status = "Ongoing",
                genre = "Wuxia",
                latestChapter = "Ch. 1902",
                rating = 8.7f / 2f, // convert 10-scale to 5-star
                summary = "A young cultivator rises from the bottom of the martial world, " +
                    "armed with nothing but an iron will and an ancient jade pendant. " +
                    "Through countless trials, he forges his path toward the peak of " +
                    "immortality — reshaping the heavens themselves.",
                chapters = (1..50).map { n ->
                    ChapterItem(
                        title = "Chapter $n — ${if (n == 1) "The Journey Begins" else "Path of the Immortal"}",
                        url = "$novelId/chapter-$n",
                    )
                },
            )
        }
    }

    /**
     * Extract dominant and vibrant colours from the cover art drawable.
     * Called by the AsyncImage success listener in NovelDetailScreen.
     *
     * Advantage: colour fully adapts to each novel's cover — no hardcoding.
     * Drawback: Palette runs on IO thread; colours arrive ~100-300 ms after image load.
     */
    fun extractPalette(drawable: Drawable) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap: Bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> return@launch // unsupported drawable type
            }

            val palette = Palette.from(bitmap).generate()

            val dominant = palette.getDominantColor(0xFF050A1A.toInt())
            val vibrant = palette.getVibrantColor(
                palette.getLightVibrantColor(
                    palette.getMutedColor(0xFF4FC3F7.toInt())
                )
            )

            withContext(Dispatchers.Main) {
                _dominantColor.value = Color(dominant)
                _vibrantColor.value = Color(vibrant)
            }
        }
    }
}
